package kr.jclab.restic.repository

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.luben.zstd.Zstd
import com.github.luben.zstd.ZstdCompressCtx
import com.github.luben.zstd.ZstdException
import com.github.luben.zstd.ZstdInputStream
import kr.jclab.restic.backend.Backend
import kr.jclab.restic.backend.FileType
import kr.jclab.restic.backend.Handle
import kr.jclab.restic.backend.RewindInputStream
import kr.jclab.restic.crypto.Crypto
import kr.jclab.restic.crypto.Key
import kr.jclab.restic.jackson.JacksonHolder
import kr.jclab.restic.model.ResticId
import kr.jclab.restic.util.FutureLoop
import org.apache.commons.io.IOUtils
import java.io.ByteArrayInputStream
import java.util.*
import java.util.concurrent.CompletableFuture

class Repository(
    val backend: Backend,
    val objectMapper: ObjectMapper = JacksonHolder.OBJECT_MAPPER,
) {
    private var key: Key? = null
    private var config: Config? = null

    companion object {
        private val COMPRESSION_HEADER_SIZE = 1
    }

    fun getKeys(): CompletableFuture<List<Key>> {
        return backend.list(FileType.KeyFile)
            .thenCompose { keyFiles ->
                val keys = ArrayList<Key>()
                keys.ensureCapacity(keyFiles.size)
                FutureLoop.execute(keyFiles) { keyFile ->
                    Key.loadKey(this, ResticId.parse(keyFile.name))
                        .thenApply { key ->
                            keys.add(key)
                            null
                        }
                }.thenApply { keys }
            }
    }

    fun isOpen(): Boolean {
        return key != null
    }

    fun init(key: Key, version: Int = Config.StableRepoVersion): CompletableFuture<Void?> {
        val promise = CompletableFuture<Void?>()

        if (isOpen()) {
            promise.completeExceptionally(IllegalStateException("already open"))
            return promise
        }

        if (!key.isOpen()) {
            promise.completeExceptionally(IllegalStateException("key is not opened"))
            return promise
        }

        return CompletableFuture.allOf(
            backend.stat(Handle(FileType.ConfigFile))
                .whenComplete { _, ex ->
                    if (ex?.let { backend.isNotExist(it) } != true) {
                        promise.completeExceptionally(ex ?: RepositoryAlreadyExistsError("config file already exists"))
                    }
                }.exceptionally { null },
            backend.list(FileType.KeyFile)
                .whenComplete { res, ex ->
                    if (ex != null) {
                        promise.completeExceptionally(ex)
                    } else if (res.isNotEmpty()) {
                        promise.completeExceptionally(RepositoryAlreadyExistsError("repository already contains keys"))
                    }
                }.exceptionally { null },
            backend.list(FileType.SnapshotFile)
                .whenComplete { res, ex ->
                    if (ex != null) {
                        promise.completeExceptionally(ex)
                    } else if (res.isNotEmpty()) {
                        promise.completeExceptionally(RepositoryAlreadyExistsError("repository already contains snapshots"))
                    }
                }.exceptionally { null },
        ).thenCompose {
            if (promise.isDone) {
                promise
            } else {
                backend.create()
                    .thenCompose { _ ->
                        val config = Config.create(version)
                        this.config = config
                        this.key = key
                        CompletableFuture.allOf(
                            saveRaw(FileType.KeyFile, key.id, key.raw),
                            saveUnpacked(FileType.ConfigFile, key.master, objectMapper.writeValueAsBytes(config))
                        )
                    }
            }
        }
    }

    fun open(key: Key, password: String? = null): CompletableFuture<Void?> {
        if (!key.isOpen()) {
            try {
                key.openKey(objectMapper, password ?: throw IllegalStateException("key is not opened"))
            } catch (e: Throwable) {
                return CompletableFuture<Void?>().apply {
                    completeExceptionally(e)
                }
            }
        }

        return loadUnpacked(FileType.ConfigFile, ResticId(), key.master)
            .thenApply {
                val config = objectMapper.readValue(it, Config::class.java)
                if (config.version < Config.MinRepoVersion || config.version > Config.MaxRepoVersion) {
                    throw RuntimeException("unsupported repository version ${config.version}")
                }
                if (!config.chunkerPolynomial.irreducible()) {
                    throw RuntimeException("invalid chunker polynomial")
                }
                this.config = config
                this.key = key
                null
            }
    }

    fun addKey(
        password: String,
        username: String?,
        hostname: String?,
        attributes: Map<String, Any?>? = null,
    ): CompletableFuture<Key> {
        return ensureOpen()
            .thenCompose {
                val newKey = Key.create(
                    objectMapper = objectMapper,
                    password = password,
                    username = username,
                    hostname = hostname,
                    template = this.key!!.master,
                    attributes = attributes,
                )
                saveRaw(FileType.KeyFile, newKey.id, newKey.raw)
                    .thenApply { _ -> newKey }
            }
    }

    fun loadRaw(fileType: FileType, id: ResticId): CompletableFuture<ByteArray> {
        return backend.loadRaw(Handle(fileType, false, id.toString()))
            .thenApply {
                val computed = it.getHash()
                if (!computed.isEqualsTo(id) && fileType != FileType.ConfigFile) {
                    throw InvalidDataError(id, computed)
                }
                it.data
            }
    }

    fun saveRaw(fileType: FileType, id: ResticId, buf: ByteArray): CompletableFuture<Void?> {
        return backend.save(Handle(fileType, false, id.toString()), RewindInputStream.from(buf), buf.size)
    }

    private fun ensureOpen(): CompletableFuture<Void?> {
        val promise = CompletableFuture<Void?>()
        if (isOpen()) {
            promise.complete(null)
        } else {
            promise.completeExceptionally(IllegalStateException("repository is not opened"))
        }
        return promise
    }

    private fun loadUnpacked(fileType: FileType, id: ResticId, key: Crypto.Key): CompletableFuture<ByteArray> {
        return loadRaw(fileType, id)
            .thenApply { raw ->
                val nonce = raw.copyOfRange(0, key.nonceSize())
                val ciphertext = raw.copyOfRange(key.nonceSize(), raw.size)
                key.open(nonce, ciphertext, null)
            }.thenApply { p ->
                if (fileType == FileType.ConfigFile) {
                    p
                } else {
                    decompressUnpacked(p)
                }
            }
    }

    private fun saveUnpacked(
        fileType: FileType,
        key: Crypto.Key,
        buf: ByteArray,
    ): CompletableFuture<ResticId> {
        return try {
            val p = (if (fileType == FileType.ConfigFile) buf else compressUnpacked(buf))
            val nonce = Crypto.newRandomNonce()
            val ciphertext = key.seal(nonce, p, null)
            val output = nonce + ciphertext
            val id = if (fileType == FileType.ConfigFile) { ResticId() } else { ResticId.hash(output) }
            val handle = Handle(fileType, false, id.toString())
            backend.save(handle, RewindInputStream.from(output), output.size)
                .thenApply { _ -> id }
        } catch (ex: Throwable) {
            CompletableFuture<ResticId>().apply {
                completeExceptionally(ex)
            }
        }
    }

    private fun decompressUnpacked(p: ByteArray): ByteArray {
        if (config!!.version < 2) {
            return p
        }
        if (p.isEmpty()) {
            return p
        }
        if ((p[0] == '['.code.toByte()) || (p[0] == '{'.code.toByte())) {
            return p
        }
        if (p[0] != 2.toByte()) {
            throw RuntimeException("not supported encoding format")
        }
        return ZstdInputStream(ByteArrayInputStream(p, 1, p.size-1)).use {
            IOUtils.toByteArray(it)
        }
    }

    private fun compressUnpacked(src: ByteArray): ByteArray {
        if (config!!.version < 2) {
            return src
        }

        return ZstdCompressCtx().use { ctx ->
            ctx.setLevel(Zstd.defaultCompressionLevel())

            val maxDstSize = Zstd.compressBound(src.size.toLong())
            if (maxDstSize > Int.MAX_VALUE) {
                throw ZstdException(Zstd.errGeneric(), "Max output size is greater than MAX_INT")
            }
            val dst = ByteArray(COMPRESSION_HEADER_SIZE + maxDstSize.toInt())
            dst[0] = 2
            val size: Int = ctx.compressByteArray(dst, COMPRESSION_HEADER_SIZE, dst.size - COMPRESSION_HEADER_SIZE, src, 0, src.size)
            Arrays.copyOfRange(dst, 0, COMPRESSION_HEADER_SIZE + size)
        }
    }
}

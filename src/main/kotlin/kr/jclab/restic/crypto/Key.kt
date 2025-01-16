package kr.jclab.restic.crypto

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import kr.jclab.restic.backend.Backend
import kr.jclab.restic.backend.FileType
import kr.jclab.restic.backend.Handle
import kr.jclab.restic.jackson.ISO8601
import kr.jclab.restic.jackson.JacksonHolder
import kr.jclab.restic.model.ResticId
import kr.jclab.restic.repository.Repository
import java.time.Instant
import java.util.concurrent.CompletableFuture

data class Key(
    @field:JsonProperty("created")
    @field:JsonSerialize(using = ISO8601.ISO8601Serializer::class)
    @field:JsonDeserialize(using = ISO8601.ISO8601Deserializer::class)
    val created: Instant,

    @field:JsonProperty("username")
    val username: String,

    @field:JsonProperty("hostname")
    val hostname: String,

    @field:JsonProperty("kdf")
    val kdf: String,

    @field:JsonProperty("N")
    val n: Int,

    @field:JsonProperty("r")
    val r: Int,

    @field:JsonProperty("p")
    val p: Int,

    @field:JsonProperty("salt")
    val salt: ByteArray,

    @field:JsonProperty("data")
    val data: ByteArray,
) {
    companion object {
        fun create(
            password: String,
            username: String?,
            hostname: String?,
            template: Crypto.Key?
        ): Key {
            val params = KeyUtils.getParams()

            val salt = CryptoUtils.newSalt()
            val user = CryptoUtils.kdf(params, salt, password)
            val master = template ?: Crypto.newRandomKey()

            val masterJson = JacksonHolder.OBJECT_MAPPER.writeValueAsBytes(master)
            val nonce = Crypto.newRandomNonce()
            val ciphertext = user.seal(nonce, masterJson, null)

            val key = Key(
                created = Instant.now(),
                username = username?.takeIf { it.isNotBlank() } ?: System.getProperty("user.name") ?: "",
                hostname = hostname?.takeIf { it.isNotBlank() } ?: System.getenv("HOSTNAME") ?: "",
                kdf = "scrypt",
                n = params.n,
                r = params.r,
                p = params.p,
                salt = salt,
                data = nonce + ciphertext,
            )
            key.master = master
            key.user = user
            val raw = JacksonHolder.OBJECT_MAPPER.writeValueAsBytes(key)
            key.id = ResticId.hash(raw)
            key.raw = raw

            return key
        }

        fun loadKey(r: Repository, id: ResticId): CompletableFuture<Key> {
            return r.loadRaw(FileType.KeyFile, id)
                .thenApply { raw ->
                    val key = JacksonHolder.OBJECT_MAPPER.readValue(raw, Key::class.java)
                    key.raw = raw
                    key.id = id
                    key
                }
        }
    }

    @field:JsonIgnore
    var user: Crypto.Key? = null
    @field:JsonIgnore
    var master: Crypto.Key? = null
    @field:JsonIgnore
    var id: ResticId? = null
    @field:JsonIgnore
    var raw: ByteArray? = null

    fun valid(): Boolean =
        user?.valid() == true && master?.valid() == true

    override fun toString(): String =
        "<Key of $username@$hostname, created on $created>"

    @JsonIgnore
    fun isOpen(): Boolean {
        return this.id != null && this.user != null && this.master != null
    }

    fun openKey(
        password: String,
    ) {
        if (kdf != "scrypt") {
            throw IllegalArgumentException("only supported KDF is scrypt()")
        }

        val params = CryptoParams(n, r, p)
        val user = CryptoUtils.kdf(params, salt, password)
        val nonce = data.copyOfRange(0, user.nonceSize())
        val ciphertext = data.copyOfRange(user.nonceSize(), data.size)
        val buf = user.open(nonce, ciphertext)

        this.user = user
        this.master = JacksonHolder.OBJECT_MAPPER.readValue(buf, Crypto.Key::class.java)

        if (!valid()) {
            throw RuntimeException("invalid key for repository")
        }
    }
}
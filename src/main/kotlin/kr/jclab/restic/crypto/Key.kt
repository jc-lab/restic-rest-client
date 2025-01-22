package kr.jclab.restic.crypto

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import kr.jclab.restic.backend.FileType
import kr.jclab.restic.jackson.ISO8601
import kr.jclab.restic.model.ResticId
import kr.jclab.restic.repository.Repository
import java.time.Instant
import java.util.concurrent.CompletableFuture

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Key(
    @field:JsonProperty("created")
    @field:JsonSerialize(using = ISO8601.ISO8601Serializer::class)
    @field:JsonDeserialize(using = ISO8601.ISO8601Deserializer::class)
    @JsonDeserialize(using = ISO8601.ISO8601Deserializer::class)
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

    @field:JsonProperty("attr")
    val attributes: Map<String, Any?>? = null,
) {
    companion object {
        fun create(
            objectMapper: ObjectMapper,
            password: String,
            username: String? = null,
            hostname: String? = null,
            template: Crypto.Key? = null,
            attributes: Map<String, Any?>? = null,
        ): Key {
            val params = KeyUtils.getParams()

            val salt = CryptoUtils.newSalt()
            val user = CryptoUtils.kdf(params, salt, password)
            val master = template ?: Crypto.newRandomKey()

            val masterJson = objectMapper.writeValueAsBytes(master)
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
                attributes = attributes,
            )
            key.privateMaster = master
            key.privateUser = user
            val raw = objectMapper.writeValueAsBytes(key)
            key.privateId = ResticId.hash(raw)
            key.privateRaw = raw

            return key
        }

        fun loadKey(r: Repository, id: ResticId): CompletableFuture<Key> {
            return r.loadRaw(FileType.KeyFile, id)
                .thenApply { raw ->
                    val key = r.objectMapper.readValue(raw, Key::class.java)
                    key.privateRaw = raw
                    key.privateId = id
                    key
                }
        }
    }

    @field:JsonIgnore
    private var privateUser: Crypto.Key? = null
    @field:JsonIgnore
    private var privateMaster: Crypto.Key? = null
    @field:JsonIgnore
    private var privateId: ResticId? = null
    @field:JsonIgnore
    private var privateRaw: ByteArray? = null

    @get:JsonIgnore
    val id: ResticId
        get() = privateId!!

    @get:JsonIgnore
    val master: Crypto.Key
        get() = privateMaster!!

    @get:JsonIgnore
    val raw: ByteArray
        get() = privateRaw!!

    fun valid(): Boolean =
        privateUser?.valid() == true && privateMaster?.valid() == true

    override fun toString(): String =
        "<Key of $username@$hostname, created on $created>"

    @JsonIgnore
    fun isOpen(): Boolean {
        return this.privateId != null && this.privateUser != null && this.privateMaster != null
    }

    fun openKey(
        objectMapper: ObjectMapper,
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

        this.privateUser = user
        this.privateMaster = objectMapper.readValue(buf, Crypto.Key::class.java)

        if (!valid()) {
            throw RuntimeException("invalid key for repository")
        }
    }
}
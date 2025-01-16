package kr.jclab.restic.repository

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import kr.jclab.restic.chunker.Pol
import kr.jclab.restic.chunker.PolJackson
import org.apache.hc.client5.http.utils.Hex
import java.security.SecureRandom

data class Config(
    @field:JsonProperty("version")
    val version: Int,

    @field:JsonProperty("id")
    val id: String,

    @field:JsonProperty("chunker_polynomial")
    @field:JsonSerialize(using = PolJackson.PolSerializer::class)
    @field:JsonDeserialize(using = PolJackson.PolDeserializer::class)
    val chunkerPolynomial: Pol,
) {
    companion object {
        private val secureRandom = SecureRandom()

        const val MinRepoVersion: Int = 1
        const val MaxRepoVersion: Int = 2
        const val StableRepoVersion: Int = 2

        fun create(version: Int): Config {
            val polynomial = Pol.randomPolynomial()
            val id = Hex.encodeHexString(ByteArray(32).also {
                secureRandom.nextBytes(it)
            })
            return Config(
                version = version,
                id = id,
                chunkerPolynomial = polynomial
            )
        }
    }

    fun validate() {
        if (version < MinRepoVersion || version > MaxRepoVersion) {
            throw IllegalArgumentException("unsupported repository version $version")
        }

        if (!chunkerPolynomial.irreducible()) {
            throw IllegalArgumentException("invalid chunker polynomial")
        }
    }
}

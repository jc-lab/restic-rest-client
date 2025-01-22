package kr.jclab.restic.repository

import kr.jclab.restic.crypto.Crypto
import kr.jclab.restic.crypto.UnauthenticatedError
import kr.jclab.restic.jackson.JacksonHolder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.experimental.xor

class CryptoTest {
    private val objectMapper = JacksonHolder.OBJECT_MAPPER

    @Test
    fun testEncryptionDecryption() {
        val key = Crypto.newRandomKey()
        val nonce = Crypto.newRandomNonce()
        val plaintext = "Hello, World!".toByteArray()
        val additionalData: ByteArray? = null

        val ciphertext = key.seal(nonce, plaintext, additionalData)
        val decryptedText = key.open(nonce, ciphertext, additionalData)

        assertArrayEquals(plaintext, decryptedText)
    }

    @Test
    fun testInvalidKey() {
        val invalidKey = Crypto.Key(
            macKey = Crypto.MACKey(ByteArray(Crypto.MAC_KEY_SIZE_K), ByteArray(Crypto.MAC_KEY_SIZE_R)),
            encryptionKey = Crypto.EncryptionKey(ByteArray(Crypto.AES_KEY_SIZE))
        )
        val nonce = Crypto.newRandomNonce()
        val plaintext = "Hello, World!".toByteArray()
        val additionalData: ByteArray? = null

        assertThrows(IllegalStateException::class.java) {
            invalidKey.seal(nonce, plaintext, additionalData)
        }
    }

    @Test
    fun testInvalidNonce() {
        val key = Crypto.newRandomKey()
        val invalidNonce = ByteArray(Crypto.IV_SIZE - 1) // Invalid nonce size
        val plaintext = "Hello, World!".toByteArray()
        val additionalData: ByteArray? = null

        assertThrows(IllegalArgumentException::class.java) {
            key.seal(invalidNonce, plaintext, additionalData)
        }
    }

    @Test
    fun testBadData() {
        val key = Crypto.newRandomKey()
        val nonce = Crypto.newRandomNonce()
        val plaintext = "Hello, World!".toByteArray()
        val additionalData: ByteArray? = null

        val ciphertext = key.seal(nonce, plaintext, additionalData)
        ciphertext[0] = ciphertext[0].xor(0x01)
        assertThrows(UnauthenticatedError::class.java) {
            val decryptedText = key.open(nonce, ciphertext, additionalData)
            assertArrayEquals(plaintext, decryptedText)
        }
    }

    @Test
    fun keyJsonTest() {
        val sample = """{"mac":{"k":"+ial+xSsr+xTIe4SAeyp1w==","r":"Qtzx8IXaQkMM+j4EjCadvQ=="},"encrypt":"2irwACuBKaBsgtrALmM2upX37FjaJ6fi4wdVXJxyAQ0="}"""
        val parsed = objectMapper.readValue(sample.toByteArray(), Crypto.Key::class.java)
        assertThat(parsed.encryptionKey.bytes).isEqualTo(Base64.getDecoder().decode("2irwACuBKaBsgtrALmM2upX37FjaJ6fi4wdVXJxyAQ0="))
    }
}
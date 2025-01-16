package kr.jclab.restic.crypto

import com.fasterxml.jackson.annotation.JsonProperty
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.macs.Poly1305
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.util.encoders.Hex
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object Crypto {
    private val random = SecureRandom()

    const val AES_KEY_SIZE = 32    // for AES-256
    const val MAC_KEY_SIZE_K = 16  // for AES-128
    const val MAC_KEY_SIZE_R = 16  // for Poly1305
    const val MAC_KEY_SIZE = MAC_KEY_SIZE_K + MAC_KEY_SIZE_R
    const val IV_SIZE = 16         // AES block size
    const val MAC_SIZE = 16        // Poly1305 tag size
    const val EXTENSION = IV_SIZE + MAC_SIZE

    @JvmInline
    value class EncryptionKey(val bytes: ByteArray) {
        fun valid(): Boolean = bytes.any { it != 0.toByte() }
    }

    data class MACKey(
        @field:JsonProperty("k")
        val k: ByteArray,  // for AES-128
        @field:JsonProperty("r")
        val r: ByteArray   // for Poly1305
    ) {
        fun valid(): Boolean {
            val nonzeroK = k.any { it != 0.toByte() }
            return nonzeroK && r.any { it != 0.toByte() }
        }
    }

    data class Key(
        @field:JsonProperty("mac")
        val macKey: MACKey,

        @field:JsonProperty("encrypt")
        val encryptionKey: EncryptionKey
    ) {
        fun nonceSize(): Int {
            return IV_SIZE
        }

        fun valid(): Boolean = encryptionKey.valid() && macKey.valid()

        fun seal(nonce: ByteArray, plaintext: ByteArray, additionalData: ByteArray? = null): ByteArray {
            if (!valid()) {
                throw IllegalStateException("key is invalid")
            }
            if (additionalData != null && additionalData.isNotEmpty()) {
                throw IllegalArgumentException("additional data is not supported")
            }
            if (nonce.size != IV_SIZE) {
                throw IllegalArgumentException("incorrect nonce length")
            }
            if (!validNonce(nonce)) {
                throw IllegalArgumentException("nonce is invalid")
            }

            val cipher = createCipher(Cipher.ENCRYPT_MODE, encryptionKey.bytes, nonce)
            val ciphertext = cipher.doFinal(plaintext)

            println("ciphertext(A): ${Hex.toHexString(ciphertext)}")
            val mac = poly1305MAC(ciphertext, nonce, macKey)

            return ciphertext + mac
        }

        fun open(nonce: ByteArray, ciphertext: ByteArray, additionalData: ByteArray? = null): ByteArray {
            if (!valid()) {
                throw IllegalStateException("invalid key")
            }
            if (nonce.size != IV_SIZE) {
                throw IllegalArgumentException("incorrect nonce length")
            }
            if (!validNonce(nonce)) {
                throw IllegalArgumentException("nonce is invalid")
            }
            if (ciphertext.size < MAC_SIZE) {
                throw IllegalArgumentException("ciphertext too short")
            }

            val l = ciphertext.size - MAC_SIZE
            val ct = ciphertext.copyOfRange(0, l)
            val mac = ciphertext.copyOfRange(l, ciphertext.size)

            println("ciphertext(B): ${Hex.toHexString(ciphertext)}")
            if (!poly1305Verify(ct, nonce, macKey, mac)) {
                throw UnauthenticatedError("ciphertext verification failed")
            }

            val cipher = createCipher(Cipher.DECRYPT_MODE, encryptionKey.bytes, nonce)
            return cipher.doFinal(ct)
        }
    }

    fun newRandomNonce(): ByteArray {
        val nonce = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(nonce)
        return nonce
    }

    fun newRandomKey(): Key {
        val encKey = ByteArray(AES_KEY_SIZE)
        val macKeyK = ByteArray(MAC_KEY_SIZE_K)
        val macKeyR = ByteArray(MAC_KEY_SIZE_R)

        random.nextBytes(encKey)
        random.nextBytes(macKeyK)
        random.nextBytes(macKeyR)

        return Key(
            macKey = MACKey(macKeyK, macKeyR),
            encryptionKey = EncryptionKey(encKey)
        )
    }

    private fun validNonce(nonce: ByteArray): Boolean =
        nonce.any { it != 0.toByte() }

    private fun createCipher(mode: Int, key: ByteArray, iv: ByteArray): Cipher {
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        val secretKey = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(iv)
        cipher.init(mode, secretKey, ivSpec)
        return cipher
    }


    private fun poly1305MAC(msg: ByteArray, nonce: ByteArray, key: MACKey): ByteArray {
        val poly1305Key = poly1305PrepareKey(nonce, key)

        val mac = Poly1305()
        mac.init(KeyParameter(poly1305Key))
        mac.update(msg, 0, msg.size)

        val result = ByteArray(MAC_SIZE)
        mac.doFinal(result, 0)
        return result
    }

    private fun poly1305PrepareKey(nonce: ByteArray, key: MACKey): ByteArray {
        val k = ByteArray(32)

        // Use AES engine directly for key generation
        val aes = AESEngine.newInstance()
        aes.init(true, KeyParameter(key.k))

        // Generate second part of the key using AES
        val tmp = ByteArray(16)
        aes.processBlock(nonce, 0, tmp, 0)

        // Combine r and AES(k, nonce)
        System.arraycopy(key.r, 0, k, 0, 16)
        System.arraycopy(tmp, 0, k, 16, 16)

        return k
    }

    private fun poly1305Verify(msg: ByteArray, nonce: ByteArray, key: MACKey, mac: ByteArray): Boolean {
        val computedMac = poly1305MAC(msg, nonce, key)
        return computedMac.contentEquals(mac)
    }
}

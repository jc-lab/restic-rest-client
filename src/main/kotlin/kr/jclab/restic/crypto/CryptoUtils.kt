package kr.jclab.restic.crypto

import org.bouncycastle.crypto.generators.SCrypt
import java.security.SecureRandom
import java.time.Duration
import kotlin.system.measureNanoTime

object CryptoUtils {
    private val random = SecureRandom()
    private const val SALT_LENGTH = 64
    private const val KEY_LENGTH = Crypto.MAC_KEY_SIZE + Crypto.AES_KEY_SIZE

    fun calibrate(timeout: Duration, memoryMb: Int): CryptoParams {
        var params = CryptoParams.DEFAULT
        var duration = measureDuration(params)

        // Adjust N (keeping r and p constant) until we find suitable parameters
        while (duration < timeout && (params.n * params.r * 128) < (memoryMb * 1024 * 1024)) {
            params = params.copy(n = params.n * 2)
            duration = measureDuration(params)
        }

        // If we overshot, go back one step
        if (duration > timeout) {
            params = params.copy(n = params.n / 2)
        }

        return params
    }

    private fun measureDuration(params: CryptoParams): Duration {
        val salt = ByteArray(SALT_LENGTH)
        val password = "benchmark"

        val nanos = measureNanoTime {
            SCrypt.generate(
                password.toByteArray(),
                salt,
                params.n,
                params.r,
                params.p,
                KEY_LENGTH
            )
        }

        return Duration.ofNanos(nanos)
    }

    fun kdf(params: CryptoParams, salt: ByteArray, password: String): Crypto.Key {
        require(salt.size == SALT_LENGTH) { "Invalid salt length: ${salt.size}" }
        params.validate()

        val derived = SCrypt.generate(
            password.toByteArray(),
            salt,
            params.n,
            params.r,
            params.p,
            KEY_LENGTH
        )

        // Split the derived key into encryption and MAC keys
        val encKey = derived.copyOfRange(0, Crypto.AES_KEY_SIZE)
        val macKey = derived.copyOfRange(Crypto.AES_KEY_SIZE, derived.size)

        return Crypto.Key(
            macKey = Crypto.MACKey(
                k = macKey.copyOfRange(0, Crypto.MAC_KEY_SIZE_K),
                r = macKey.copyOfRange(Crypto.MAC_KEY_SIZE_K, Crypto.MAC_KEY_SIZE)
            ),
            encryptionKey = Crypto.EncryptionKey(encKey)
        )
    }

    fun newSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        random.nextBytes(salt)
        return salt
    }
}

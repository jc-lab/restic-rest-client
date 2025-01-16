package kr.jclab.restic.crypto

import java.time.Duration

object KeyUtils {
    const val KDF_TIMEOUT_MS = 500L
    const val KDF_MEMORY = 60

    private val lock = Object()
    private var params: CryptoParams? = null

    fun getParams(): CryptoParams = synchronized(lock) {
        params ?: CryptoUtils.calibrate(Duration.ofMillis(KDF_TIMEOUT_MS), KDF_MEMORY).let {
            params = it
            it
        }
    }
}
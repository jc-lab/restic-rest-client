package kr.jclab.restic.crypto

data class CryptoParams(
    val n: Int,
    val r: Int,
    val p: Int
) {
    companion object {
        // Default parameters from simple-scrypt
        val DEFAULT = CryptoParams(
            n = 16384,  // 2^14
            r = 8,
            p = 1
        )
    }

    fun validate() {
        require(n > 1 && (n and (n - 1)) == 0) { "N must be > 1 and a power of 2" }
        require(r > 0) { "r must be > 0" }
        require(p > 0) { "p must be > 0" }
    }
}
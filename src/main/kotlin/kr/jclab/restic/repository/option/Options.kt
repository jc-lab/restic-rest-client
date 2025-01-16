package kr.jclab.restic.repository.option

data class Options(
    val compression: CompressionMode = CompressionMode.Auto,
    val packSize: UInt = DefaultPackSize,
    val noExtraVerify: Boolean = false
) {
    companion object {
        val MinPackSize: UInt = 4u * 1024u * 1024u
        val DefaultPackSize: UInt = 16u * 1024u * 1024u
        val MaxPackSize: UInt = 128u * 1024u * 1024u
    }

    fun validate() {
        if (compression == CompressionMode.Invalid) {
            throw IllegalArgumentException("invalid compression mode")
        }

        if (packSize > MaxPackSize) {
            throw IllegalArgumentException("pack size larger than limit of ${MaxPackSize / 1024u / 1024u} MiB")
        }
        if (packSize < MinPackSize) {
            throw IllegalArgumentException("pack size smaller than minimum of ${MinPackSize / 1024u / 1024u} MiB")
        }
    }
}

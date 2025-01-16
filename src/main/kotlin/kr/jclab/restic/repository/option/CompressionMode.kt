package kr.jclab.restic.repository.option

enum class CompressionMode {
    Auto,
    Off,
    Max,
    Invalid;

    override fun toString(): String = when(this) {
        Auto -> "auto"
        Off -> "off"
        Max -> "max"
        Invalid -> "invalid"
    }

    companion object {
        fun fromString(s: String): CompressionMode = when(s.lowercase()) {
            "auto" -> Auto
            "off" -> Off
            "max" -> Max
            else -> Invalid
        }
    }
}
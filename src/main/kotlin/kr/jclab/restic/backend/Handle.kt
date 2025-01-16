package kr.jclab.restic.backend

data class Handle(
    val type: FileType,
    val isMetadata: Boolean = false,
    val name: String = ""
) {
    override fun toString(): String {
        val displayName = if (name.length > 10) name.substring(0, 10) else name
        return "<${type}/${displayName}>"
    }

    fun validate() {
        if (type == FileType.ConfigFile) {
            return
        }
        if (name.isEmpty()) {
            throw IllegalArgumentException("invalid Name")
        }
    }
}

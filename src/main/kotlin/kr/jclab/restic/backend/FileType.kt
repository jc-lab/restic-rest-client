package kr.jclab.restic.backend

enum class FileType(val value: Int) {
    PackFile(1),
    KeyFile(2),
    LockFile(3),
    SnapshotFile(4),
    IndexFile(5),
    ConfigFile(6);

    override fun toString(): String = when(this) {
        PackFile -> "data"
        KeyFile -> "keys"
        LockFile -> "locks"
        SnapshotFile -> "snapshots"
        IndexFile -> "index"
        ConfigFile -> "config"
    }
}

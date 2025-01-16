package kr.jclab.restic.backend

import java.io.InputStream
import java.util.concurrent.CompletableFuture

interface Backend {
    fun create(): CompletableFuture<Void?>
    fun save(handle: Handle, rd: RewindInputStream, length: Int): CompletableFuture<Void?>
    fun load(handle: Handle, length: Int = 0, offset: Long = 0): CompletableFuture<InputStream>
    fun loadRaw(handle: Handle): CompletableFuture<HashedBytes>
    fun stat(handle: Handle): CompletableFuture<FileInfo>
    fun remove(handle: Handle): CompletableFuture<Void?>
    fun list(type: FileType): CompletableFuture<List<FileInfo>>

    fun isNotExist(ex: Throwable): Boolean
}
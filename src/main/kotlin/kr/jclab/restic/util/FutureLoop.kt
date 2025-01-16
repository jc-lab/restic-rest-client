package kr.jclab.restic.util

import java.util.concurrent.CompletableFuture

class FutureLoop<T>(
    val data: List<T>,
    val handler: (item: T) -> CompletableFuture<Void?>,
) {
    companion object {
        fun <T> execute(data: List<T>, handle: (item: T) -> CompletableFuture<Void?>): CompletableFuture<Void?> {
            return FutureLoop(data, handle).execute()
        }
    }

    fun execute(): CompletableFuture<Void?> {
        val promise = CompletableFuture<Void?>()
        executeImpl(promise, 0)
        return promise
    }

    fun executeImpl(promise: CompletableFuture<Void?>, index: Int) {
        if (index >= data.size) {
            promise.complete(null)
        } else {
            try {
                handler(data[index])
                    .whenComplete { _, ex ->
                        if (ex != null) {
                            promise.completeExceptionally(ex)
                        } else {
                            executeImpl(promise, index + 1)
                        }
                    }
            } catch (e: Throwable) {
                promise.completeExceptionally(e)
            }
        }
    }
}
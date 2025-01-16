package kr.jclab.restic.backend.restclient

import org.apache.hc.core5.concurrent.FutureCallback
import java.util.concurrent.CompletableFuture

class SimpleFutureCallback<T>(
    private val promise: CompletableFuture<T>,
    private val filter: ((promise: CompletableFuture<T>, result: T) -> Boolean)? = null,
) : FutureCallback<T> {
    override fun completed(result: T) {
        if (filter?.invoke(promise, result) != false) {
            promise.complete(result)
        }
    }

    override fun failed(ex: java.lang.Exception) {
        promise.completeExceptionally(ex)
    }

    override fun cancelled() {
        promise.cancel(false)
    }
}

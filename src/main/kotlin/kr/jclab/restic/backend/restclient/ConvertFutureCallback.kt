package kr.jclab.restic.backend.restclient

import org.apache.hc.core5.concurrent.FutureCallback
import java.util.concurrent.CompletableFuture

class ConvertFutureCallback<T, R>(
    private val promise: CompletableFuture<R>,
    private val convert: (promise: CompletableFuture<R>, result: T) -> Unit
) : FutureCallback<T> {
    override fun completed(result: T) {
        convert(promise, result)
        if (!promise.isDone) {
            promise.completeExceptionally(RuntimeException("not processed"))
        }
    }

    override fun failed(ex: java.lang.Exception) {
        promise.completeExceptionally(ex)
    }

    override fun cancelled() {
        promise.cancel(false)
    }
}

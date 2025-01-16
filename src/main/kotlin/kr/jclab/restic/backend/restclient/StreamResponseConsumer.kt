package kr.jclab.restic.backend.restclient

import org.apache.hc.core5.concurrent.FutureCallback
import org.apache.hc.core5.http.*
import org.apache.hc.core5.http.nio.AsyncResponseConsumer
import org.apache.hc.core5.http.nio.entity.AbstractBinDataConsumer
import org.apache.hc.core5.http.protocol.HttpContext
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.ByteBuffer
import kotlin.concurrent.Volatile

typealias ResponseFilter = (
    response: HttpResponse,
    entityDetails: EntityDetails,
    context: HttpContext?,
    resultCallback: FutureCallback<StreamResponse>,
) -> Boolean

open class StreamResponseConsumer(
    private val filter: ResponseFilter? = null,
) : AsyncResponseConsumer<StreamResponse>, AbstractBinDataConsumer() {
    @Volatile
    private lateinit var resultCallback: FutureCallback<StreamResponse>
    private lateinit var response: HttpResponse
    private lateinit var entityDetails: EntityDetails
    private var context: HttpContext? = null

    private val pipedOutputStream = PipedOutputStream()
    private val pipedInputStream = PipedInputStream(pipedOutputStream)
    private var closed: Boolean = false

    override fun consumeResponse(
        response: HttpResponse,
        entityDetails: EntityDetails,
        context: HttpContext?,
        resultCallback: FutureCallback<StreamResponse>,
    ) {
        this.response = response
        this.entityDetails = entityDetails
        this.context = context
        this.resultCallback = resultCallback
        if (filter?.invoke(response, entityDetails, context, resultCallback) == false) {
            closed = true
            return
        }
        resultCallback.completed(
            StreamResponse(
                response,
                entityDetails,
                pipedInputStream,
            )
        )
    }

    override fun informationResponse(response: HttpResponse, context: HttpContext) {}

    override fun capacityIncrement(): Int = 131072

    @Throws(IOException::class)
    override fun data(srcBuffer: ByteBuffer?, endOfStream: Boolean) {
        if (closed) {
            throw IOException("closed")
        }

        try {
            srcBuffer?.let { src ->
                if (src.hasArray()) {
                    val size = src.remaining()
                    pipedOutputStream.write(src.array(), src.arrayOffset(), src.remaining())
                    src.position(src.position() + size)
                } else {
                    val buffer = ByteArray(src.remaining())
                    src.get(buffer)
                    pipedOutputStream.write(buffer)
                }
            }
            if (endOfStream) {
                pipedOutputStream.close()
            }
        } catch (e: Exception) {
            failed(e)
        }
    }

    override fun completed() {}

    override fun failed(cause: Exception) {
        resultCallback.failed(cause)
    }

    override fun releaseResources() {
        try {
            pipedOutputStream.close()
            pipedInputStream.close()
        } catch (e: IOException) {
            // nothing
        }
    }
}
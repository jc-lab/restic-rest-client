package kr.jclab.restic.backend.restclient

import kr.jclab.restic.backend.RewindInputStream
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.nio.AsyncEntityProducer
import org.apache.hc.core5.http.nio.DataStreamChannel
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class InputStreamAsyncEntityProducer(
    private val input: InputStream,
    private val contentLength: Long = -1,
    private val contentType: ContentType,
    private val fragmentSizeHint: Int = 131072
) : AsyncEntityProducer {

    @Volatile
    private var endOfStream = false
    private val started = AtomicBoolean()
    private val exception = AtomicReference<Exception?>(null)

    fun start() {
        if (!started.getAndSet(true)) {
            if (input is RewindInputStream) {
                input.rewind()
            }
        }
    }

    override fun available(): Int {
        start()
        return if (endOfStream) 0 else input.available()
    }

    override fun produce(channel: DataStreamChannel) {
        start()
        val buffer = ByteBuffer.allocate(fragmentSizeHint)
        try {
            val bytesRead = input.read(buffer.array())
            if (bytesRead == -1) {
                endOfStream = true
                channel.endStream()
            } else {
                buffer.limit(bytesRead)
                channel.write(buffer)
            }
        } catch (ex: IOException) {
            failed(ex)
        }
    }

    override fun isRepeatable(): Boolean {
        return false
    }

    override fun getContentType(): String {
        return contentType.toString()
    }

    override fun getContentEncoding(): String? = null

    override fun getContentLength(): Long {
        return contentLength
    }

    override fun isChunked(): Boolean {
        return contentLength < 0
    }

    override fun getTrailerNames(): Set<String> {
        return emptySet()
    }

    override fun failed(cause: Exception) {
        if (exception.compareAndSet(null, cause)) {
            releaseResources()
        }
    }

    override fun releaseResources() {
        try {
            input.close()
        } catch (ignore: IOException) {
        }
    }
}
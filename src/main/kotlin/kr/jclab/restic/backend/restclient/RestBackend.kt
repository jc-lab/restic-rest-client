package kr.jclab.restic.backend.restclient

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kr.jclab.restic.backend.*
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient
import org.apache.hc.core5.http.*
import org.apache.hc.core5.http.nio.AsyncRequestProducer
import org.apache.hc.core5.http.nio.support.BasicRequestProducer
import org.apache.hc.core5.net.URIBuilder
import java.io.InputStream
import java.lang.Long.parseLong
import java.net.URI
import java.util.concurrent.CompletableFuture

class RestBackend(
    private val httpClient: CloseableHttpAsyncClient,
    baseURI: URI,
    auth: HttpAuth? = null,
    private val objectMapper: ObjectMapper = createObjectMapper(),
) : Backend {
    companion object {
        const val CONTENT_TYPE_V1 = "application/vnd.x.restic.rest.v1"
        const val CONTENT_TYPE_V2 = "application/vnd.x.restic.rest.v2"

        fun createObjectMapper(): ObjectMapper {
            val objectMapper = jacksonObjectMapper()
            objectMapper.registerModules(JavaTimeModule())
            return objectMapper
        }
    }

    private val auth: HttpAuth? = auth ?: baseURI.userInfo?.let { userInfo ->
        val tokens = userInfo.split(":")
        BasicAuth(tokens[0], tokens[1])
    }
    private val baseURI: URI = URI(
        baseURI.scheme,
        null,
        baseURI.host,
        baseURI.port,
        baseURI.path.let {
            if (it.endsWith("/")) it else "${it}/"
        },
        baseURI.query,
        baseURI.fragment
    )

    fun applyAuthTo(builder: SimpleRequestBuilder) {
        auth?.getHeaders()?.forEach { (k, v) ->
            builder.addHeader(k, v)
        }
    }

    override fun create(): CompletableFuture<Void?> {
        val request = SimpleRequestBuilder.post(
            URIBuilder(baseURI)
                .addParameter("create", "true")
                .build()
        )
            .also { applyAuthTo(it) }
            .setBody(ByteArray(0), ContentType.APPLICATION_OCTET_STREAM)
            .build()

        return httpExecuteSimpleResponse(SimpleRequestProducer.create(request)) { promise, response ->
            if (response.code != 200) {
                promise.completeExceptionally(
                    RestError(Handle(FileType.ConfigFile), response.code, response.reasonPhrase)
                )
                false
            } else true
        }.thenApply { null }
    }

    override fun save(handle: Handle, rd: RewindInputStream, length: Int): CompletableFuture<Void?> {
        val url = baseURI.resolve(getFilename(handle))

        val request = SimpleRequestBuilder.post(url)
            .also { applyAuthTo(it) }
            .addHeader(HttpHeaders.ACCEPT, CONTENT_TYPE_V2)
            .build()

        val requestProducer = BasicRequestProducer(
            request,
            InputStreamAsyncEntityProducer(rd, rd.available().toLong(), ContentType.APPLICATION_OCTET_STREAM)
        )

        return httpExecuteSimpleResponse(requestProducer) { promise, response ->
            if (response.code != 200) {
                promise.completeExceptionally(
                    RestError(handle, response.code, response.reasonPhrase)
                )
                false
            } else true
        }.thenApply { null }
    }

    override fun load(handle: Handle, length: Int, offset: Long): CompletableFuture<InputStream> {
        val url = baseURI.resolve(getFilename(handle))
        val request = SimpleRequestBuilder.get(url)
            .addHeader(HttpHeaders.ACCEPT, CONTENT_TYPE_V2)
            .also { applyAuthTo(it) }
            .apply {
                if (length > 0 || offset > 0) {
                    val range = if (length > 0) {
                        "bytes=$offset-${offset + length - 1}"
                    } else {
                        "bytes=$offset-"
                    }
                    addHeader(HttpHeaders.RANGE, range)
                }
            }
            .build()

        return httpExecuteStreamResponse(SimpleRequestProducer.create(request)) {
                response, entityDetails, context, resultCallback ->
            when {
                response.code != 200 && response.code != 206 -> {
                    resultCallback.failed(
                        RestError(handle, response.code, response.reasonPhrase)
                    )
                    false
                }

                length > 0 && entityDetails.contentLength != length.toLong() -> {
                    resultCallback.failed(
                        RestError(handle, 416, "partial out of bounds read")
                    )
                    false
                }

                else -> true
            }
        }.thenApply { it.inputStream }
    }

    override fun loadRaw(handle: Handle): CompletableFuture<HashedBytes> {
        val url = baseURI.resolve(getFilename(handle))
        val request = SimpleRequestBuilder.get(url)
            .addHeader(HttpHeaders.ACCEPT, CONTENT_TYPE_V2)
            .also { applyAuthTo(it) }
            .build()
        val promise = CompletableFuture<HashedBytes>()
        httpClient.execute(request, ConvertFutureCallback(promise) { _, response: SimpleHttpResponse ->
            if (response.code != 200 && response.code != 206) {
                promise.completeExceptionally(RestError(handle, response.code, response.reasonPhrase))
            } else {
                promise.complete(HashedBytes(response.bodyBytes))
            }
        })
        return promise
    }

    override fun stat(handle: Handle): CompletableFuture<FileInfo> {
        val url = baseURI.resolve(getFilename(handle))
        val request = SimpleRequestBuilder.head(url)
            .also { applyAuthTo(it) }
            .addHeader(HttpHeaders.ACCEPT, CONTENT_TYPE_V2)
            .build()

        return httpExecuteSimpleResponse(SimpleRequestProducer.create(request)) { promise, response ->
            if (response.code != 200) {
                promise.completeExceptionally(
                    RestError(handle, response.code, response.reasonPhrase)
                )
                false
            } else {
                true
            }
        }.thenApply { response ->
            val contentLength = response.getHeader("content-length")
                ?.let { parseLong(it.value) }
                ?: throw IllegalStateException("no content-length header")
            if (contentLength < 0) {
                throw IllegalStateException("negative content length: ${contentLength}")
            }
            FileInfo(handle.name, contentLength)
        }
    }

    override fun remove(handle: Handle): CompletableFuture<Void?> {
        val url = baseURI.resolve(getFilename(handle))
        val request = SimpleRequestBuilder.delete(url)
            .also { applyAuthTo(it) }
            .addHeader(HttpHeaders.ACCEPT, CONTENT_TYPE_V2)
            .build()
        return httpExecuteSimpleResponse(SimpleRequestProducer.create(request)) { promise, response ->
            if (response.code != 200) {
                promise.completeExceptionally(
                    RestError(handle, response.code, response.reasonPhrase)
                )
                false
            } else true
        }.thenApply { null }
    }

    override fun list(type: FileType): CompletableFuture<List<FileInfo>> {
        val url = baseURI.resolve(getDirname(Handle(type)))
        val request = SimpleRequestBuilder.get(url)
            .also { applyAuthTo(it) }
            .addHeader(HttpHeaders.ACCEPT, CONTENT_TYPE_V2)
            .build()

        return httpExecuteSimpleResponse(SimpleRequestProducer.create(request)) { promise, response ->
            when {
                response.code == 404 &&
                        response.getHeader("Server")?.value?.startsWith("rclone/") != true -> {
                    true
                }

                response.code != 200 -> {
                    promise.completeExceptionally(
                        RestError(Handle(type), response.code, response.reasonPhrase)
                    )
                    false
                }

                else -> true
            }
        }.thenApply { response ->
            if (response.code == 404 && response.getHeader("Server")?.value?.startsWith("rclone/") != true) {
                emptyList()
            } else if (response.getHeader("Content-Type")?.value == CONTENT_TYPE_V2) {
                parseV2List(response.bodyBytes)
            } else {
                parseV1List(response.bodyBytes, type)
            }
        }
    }

    override fun isNotExist(ex: Throwable): Boolean {
        val restError = RestError.from(ex)
        return (restError?.statusCode == 404)
    }

    private fun getFilename(handle: Handle): String {
        return if (handle.type == FileType.ConfigFile) {
            "config"
        } else {
            "${handle.type}/${handle.name}"
        }
    }

    private fun getDirname(handle: Handle): String =
        "${handle.type}/"

    private fun parseV2List(content: ByteArray): List<FileInfo> {
        data class ListItem(val name: String, val size: Long)
        return objectMapper.readValue<List<ListItem>>(content).map {
            FileInfo(it.name, it.size)
        }
    }

    private fun parseV1List(content: ByteArray, type: FileType): List<FileInfo> {
        val names: List<String> = objectMapper.readValue(content)
        return names.mapNotNull { name ->
            try {
                val handle = Handle(type, name = name)
                val fileInfo = stat(handle).get()
                FileInfo(name, fileInfo.size)
            } catch (e: Exception) {
                null
            }
        }
    }


    private fun httpExecuteSimpleResponse(
        requestProducer: AsyncRequestProducer,
        filter: ((promise: CompletableFuture<SimpleHttpResponse>, result: SimpleHttpResponse) -> Boolean)? = null,
    ): CompletableFuture<SimpleHttpResponse> {
        val promise = CompletableFuture<SimpleHttpResponse>()
        httpClient.execute(
            requestProducer,
            SimpleResponseConsumer.create(),
            SimpleFutureCallback(promise, filter)
        )
        return promise
    }

    private fun httpExecuteStreamResponse(
        requestProducer: AsyncRequestProducer,
        filter: ResponseFilter? = null,
    ): CompletableFuture<StreamResponse> {
        val promise = CompletableFuture<StreamResponse>()
        httpClient.execute(
            requestProducer,
            StreamResponseConsumer(filter),
            SimpleFutureCallback(promise)
        )
        return promise
    }
}

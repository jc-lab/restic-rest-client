package kr.jclab.restic.backend.restclient

import org.apache.hc.core5.http.EntityDetails
import org.apache.hc.core5.http.HttpResponse
import java.io.InputStream

class StreamResponse(
    val response: HttpResponse,
    val entityDetails: EntityDetails,
    val inputStream: InputStream,
)
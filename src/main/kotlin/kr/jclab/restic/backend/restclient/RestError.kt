package kr.jclab.restic.backend.restclient

import kr.jclab.restic.backend.FileType
import kr.jclab.restic.backend.Handle

class RestError(
    val handle: Handle,
    val statusCode: Int,
    val status: String
) : Exception(buildErrorMessage(statusCode, status, handle)) {
    companion object {
        private fun buildErrorMessage(statusCode: Int, status: String, handle: Handle): String =
            if (statusCode == 404 && handle.type != FileType.ConfigFile) {
                "$handle does not exist"
            } else {
                "unexpected HTTP response ($statusCode): $status"
            }

        fun from(exception: Throwable?): RestError? {
            return when (exception) {
                null -> null
                is RestError -> exception
                else -> from (exception.cause)
            }
        }
    }
}
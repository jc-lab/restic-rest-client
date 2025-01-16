package kr.jclab.restic.backend.restclient

import java.util.Base64

class BasicAuth(
    val username: String,
    private val password: String,
) : HttpAuth {
    override fun getHeaders(): Map<String, String> {
        val auth = "$username:$password"
        val encodedAuth = Base64.getEncoder().encodeToString(auth.toByteArray())
        return mapOf("Authorization" to "Basic $encodedAuth")
    }
}

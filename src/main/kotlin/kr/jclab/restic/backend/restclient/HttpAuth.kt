package kr.jclab.restic.backend.restclient

interface HttpAuth {
    fun getHeaders(): Map<String, String>
}

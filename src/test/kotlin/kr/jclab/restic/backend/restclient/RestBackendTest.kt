package kr.jclab.restic.backend.restclient

import kr.jclab.restic.backend.restclient.RestBackend.Companion.createObjectMapper
import kr.jclab.restic.crypto.Key
import kr.jclab.restic.jackson.ISO8601
import org.junit.jupiter.api.Test
import java.time.Instant

class RestBackendTest {
    private val objectMapper = createObjectMapper()

    @Test
    fun objectMapperJavaTimeTest() {
        val key = Key(
            created = ISO8601.formatter.parse("2025-01-02T03:45:12.123456789Z", Instant::from),
            username = "user",
            hostname = "host",
            kdf = "kdf",
            n = 1,
            r = 2,
            p = 3,
            salt = byteArrayOf(1, 2, 3, 4),
            data = byteArrayOf(5, 6, 7, 8)
        )
        println(objectMapper.writeValueAsString(key))
        println(objectMapper.writeValueAsString(key))
        println(objectMapper.writeValueAsString(key))
    }
}
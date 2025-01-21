package kr.jclab.restic.backend.restclient

import kr.jclab.restic.crypto.Key
import kr.jclab.restic.jackson.ISO8601
import kr.jclab.restic.jackson.JacksonHolder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class RestBackendTest {
    private val objectMapper = JacksonHolder.OBJECT_MAPPER

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
        val expectedJson = "{\"created\":\"2025-01-02T03:45:12.123456789Z\",\"username\":\"user\",\"hostname\":\"host\",\"kdf\":\"kdf\",\"N\":1,\"r\":2,\"p\":3,\"salt\":\"AQIDBA==\",\"data\":\"BQYHCA==\"}"
        assertThat(objectMapper.writeValueAsString(key)).isEqualTo(expectedJson)
        val parsed = objectMapper.readValue(expectedJson, Key::class.java)
        assertThat(parsed.created).isEqualTo(key.created)
    }

    @Test
    fun objectMapperTimeFromResticTest() {
        val sampleJson = "{\"created\":\"2025-01-02T03:45:12.1234567+09:00\",\"username\":\"test\",\"hostname\":\"test\",\"kdf\":\"scrypt\",\"N\":32768,\"r\":8,\"p\":4,\"salt\":\"scEAB1OFlE+/Bot/F0hXcNh5xnqtMlXQ74uSkE4yQDWem3N1ub7zDeSfTmEDoHWuW24Yo6aDK7MGEZ6rY3/gZQ==\",\"data\":\"uCaTjn7/sM5Fz1uEggeNmGK8W5jUhD98ZP+R2490+CS37PlscMOKYF2VdJnqU86NkkrlL+mPKQ+VMA7BB+yi5w5IUzdq+HFVzVBNJndv012IZvyIzY4EtDTLLXq2wh0J4J1UaArHamwixjRXfTVUSaIN+/8ZwbC516YksXZEGYgCBoxapQ01FyCkZr/lFl1aZv86p0jnKsIgpATYnAHAzQ==\"}"
        val parsed = objectMapper.readValue(sampleJson, Key::class.java)
        assertThat(parsed.created.epochSecond).isEqualTo(1735757112)
    }
}
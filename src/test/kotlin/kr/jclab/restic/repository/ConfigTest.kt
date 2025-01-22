package kr.jclab.restic.repository

import kr.jclab.restic.jackson.JacksonHolder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConfigTest {
    private val objectMapper = JacksonHolder.OBJECT_MAPPER

    @Test
    fun configJsonTest() {
        val sample = "{\"version\":2,\"id\":\"a59810ee97711822cc8bde03887fa7cc84c5c4819f9df722d67f04ff9bed062c\",\"chunker_polynomial\":\"232ca9553c0607\"}"
        val parsed = objectMapper.readValue(sample, Config::class.java)
        assertThat(parsed.version).isEqualTo(2)
        assertThat(parsed.chunkerPolynomial.value.toLong()).isEqualTo(0x232ca9553c0607L)

        assertThat(parsed.chunkerPolynomial.irreducible()).isTrue()
        
        val serialized = objectMapper.writeValueAsString(parsed)
        assertThat(serialized).isEqualTo(sample)
    }

}

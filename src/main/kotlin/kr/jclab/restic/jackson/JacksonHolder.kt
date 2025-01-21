package kr.jclab.restic.jackson

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

object JacksonHolder {
    fun createObjectMapper(): ObjectMapper {
        val objectMapper = jacksonObjectMapper()
        objectMapper.registerModules(JavaTimeModule())
        return objectMapper
    }

    val OBJECT_MAPPER = createObjectMapper()
}
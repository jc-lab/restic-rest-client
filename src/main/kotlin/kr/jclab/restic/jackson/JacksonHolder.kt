package kr.jclab.restic.jackson

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

object JacksonHolder {
    val OBJECT_MAPPER = jacksonObjectMapper()
}
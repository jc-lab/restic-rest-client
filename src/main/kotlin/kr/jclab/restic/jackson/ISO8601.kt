package kr.jclab.restic.jackson

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ISO8601 {
    val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        .withZone(ZoneId.of("UTC"))

    class ISO8601Serializer : StdSerializer<Instant>(Instant::class.java) {
        override fun serialize(value: Instant, gen: JsonGenerator, provider: SerializerProvider) {
            gen.writeString(formatter.format(value))
        }
    }

    class ISO8601Deserializer : StdDeserializer<Instant>(Instant::class.java) {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Instant {
            val str = p.valueAsString
            return DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(str, Instant::from)
        }
    }
}

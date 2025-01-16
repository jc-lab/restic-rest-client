package kr.jclab.restic.chunker

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer

class PolJackson {
    class PolSerializer : StdSerializer<Pol>(Pol::class.java) {
        override fun serialize(value: Pol, gen: JsonGenerator, provider: SerializerProvider) {
            gen.writeString(value.toString())
        }
    }

    class PolDeserializer : StdDeserializer<Pol>(Pol::class.java) {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Pol {
            val str = p.valueAsString
            return Pol(str.toULong(16))
        }
    }
}

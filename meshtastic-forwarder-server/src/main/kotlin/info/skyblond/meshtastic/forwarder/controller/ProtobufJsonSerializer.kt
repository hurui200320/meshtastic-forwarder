package info.skyblond.meshtastic.forwarder.controller

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.google.protobuf.Message
import com.google.protobuf.util.JsonFormat

class ProtobufJsonSerializer : JsonSerializer<Message>() {
    override fun serialize(
        value: Message,
        gen: JsonGenerator,
        serializers: SerializerProvider
    ) {
        val str = JsonFormat.printer().omittingInsignificantWhitespace().print(value)
        gen.writeRawValue(str)
    }
}
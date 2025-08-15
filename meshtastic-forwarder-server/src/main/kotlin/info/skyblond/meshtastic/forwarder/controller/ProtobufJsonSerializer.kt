package info.skyblond.meshtastic.forwarder.controller

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.google.protobuf.Message
import info.skyblond.meshtastic.forwarder.toBase64

class ProtobufJsonSerializer : JsonSerializer<Message>() {
    override fun serialize(
        value: Message,
        gen: JsonGenerator,
        serializers: SerializerProvider
    ) {
        gen.writeString(value.toByteString().toBase64())
    }
}
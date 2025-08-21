package info.skyblond.meshtastic.forwarder.config

import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.databind.module.SimpleModule
import com.google.protobuf.Message
import info.skyblond.meshtastic.forwarder.controller.ProtobufJsonSerializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JacksonCustomConfig {
    @Bean
    fun protobufModule(): Module {
        val module = SimpleModule()
        module.addSerializer(Message::class.java, ProtobufJsonSerializer())
        return module
    }
}
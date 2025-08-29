package info.skyblond.meshtastic.forwarder.config

import com.google.genai.Client
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
class GeminiConfig {

    // pick up everything from env
    @Bean
    fun gemini(): Client = Client()
}
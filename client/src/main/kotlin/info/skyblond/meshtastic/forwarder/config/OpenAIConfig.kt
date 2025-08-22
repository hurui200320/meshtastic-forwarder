package info.skyblond.meshtastic.forwarder.config

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.springboot.OpenAIClientCustomizer
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

@Configuration
class OpenAIConfig {
    private val logger = LoggerFactory.getLogger(OpenAIConfig::class.java)

    @Suppress("HttpUrlsUsage")
    @Bean
    fun customizer() = OpenAIClientCustomizer { builder ->
        if (System.getenv("HTTP_PROXY") != null) {
            val uri = URI.create(System.getenv("HTTP_PROXY"))
            val host = uri.host ?: "127.0.0.1"
            val port = if (uri.port == -1) 80 else uri.port
            logger.info("Using proxy http://$host:$port for OpenAI library")
            builder.proxy(
                Proxy(
                    Proxy.Type.HTTP,
                    InetSocketAddress(host, port)
                )
            )
        }
    }
}
package info.skyblond.meshtastic.forwarder.config

import info.skyblond.meshtastic.forwarder.component.MeshtasticComponent
import info.skyblond.meshtastic.forwarder.controller.MeshPacketBroadcastWebSocketController
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean

@Configuration
@EnableWebSocket
class WebSocketConfiguration(
    private val meshtasticComponent: MeshtasticComponent
) : WebSocketConfigurer {
    @Bean
    fun meshPacketWebSocketController() = MeshPacketBroadcastWebSocketController(meshtasticComponent)

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry
            .addHandler(meshPacketWebSocketController(), "/ws/packet")
    }

    /**
     * Set the websocket settings
     *
     * + Max session idle = 15s, which has a recommend ping at every 5s
     * */
    @Bean
    fun createWebSocketContainer() = ServletServerContainerFactoryBean().apply {
        setMaxSessionIdleTimeout(15_000)
    }
}
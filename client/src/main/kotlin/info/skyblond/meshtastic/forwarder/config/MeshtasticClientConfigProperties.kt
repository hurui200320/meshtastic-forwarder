package info.skyblond.meshtastic.forwarder.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "meshtastic.client")
class MeshtasticClientConfigProperties {
    var baseUrl: String = "127.0.0.1:8080"
    var enableTls: Boolean = false
    var token: String = ""
}
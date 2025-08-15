package info.skyblond.meshtastic.forwarder.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "meshtastic.server")
class MeshtasticForwarderConfigProperties {
    /**
     * Map of token to a list of authorities.
     * */
    var tokens: Map<String, List<String>> = emptyMap()
}
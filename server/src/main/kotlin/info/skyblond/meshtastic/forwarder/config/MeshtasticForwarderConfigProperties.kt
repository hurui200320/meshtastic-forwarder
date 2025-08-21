package info.skyblond.meshtastic.forwarder.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "meshtastic.server")
class MeshtasticForwarderConfigProperties {

    /**
     * List of tokens to allow Read (read packets and pull info from the device).
     * A comma-separated list.
     * */
    var roTokens: String = ""

    /**
     * List of tokens to allow Read (read packets and pull info from the device)
     * and Write (sending packet).
     * A comma-separated list.
     * */
    var rwTokens: String = ""
}
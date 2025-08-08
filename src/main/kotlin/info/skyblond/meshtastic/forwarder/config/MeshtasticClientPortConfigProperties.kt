package info.skyblond.meshtastic.forwarder.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI

@ConfigurationProperties(prefix = "meshtastic.client.port")
class MeshtasticClientPortConfigProperties {
    var uri: URI = URI("serial:///dev/ttyACM0?baudrate=9600")
}
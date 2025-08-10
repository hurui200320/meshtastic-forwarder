package info.skyblond.meshtastic.forwarder.config

import jakarta.validation.constraints.Min
import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI

@ConfigurationProperties(prefix = "meshtastic.client")
class MeshtasticClientConfigProperties {
    var portUri: URI = URI("serial:///dev/ttyACM0?baudrate=9600")

    @Min(1)
    var configRefreshMinutes: Long = 5
}
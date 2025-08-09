package info.skyblond.meshtastic.forwarder.config

import info.skyblond.meshtastic.forwarder.api.client.MeshtasticClientPort
import info.skyblond.meshtastic.forwarder.serial.linux.LinuxSerialMeshtasticClientPort
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.URI

@Configuration
@EnableConfigurationProperties(MeshtasticClientPortConfigProperties::class)
class MeshtasticClientPortConfig {
    private val logger = LoggerFactory.getLogger(MeshtasticClientPortConfig::class.java)

    @Bean
    fun clientPort(
        config: MeshtasticClientPortConfigProperties,
    ): MeshtasticClientPort {
        return when (config.uri.scheme.lowercase()) {
            "serial" -> createSerialPort(config.uri)
            else -> throw IllegalArgumentException("Unsupported scheme: ${config.uri.scheme}")
        }
    }

    private fun createSerialPort(uri: URI): MeshtasticClientPort {
        val devicePath = uri.path
        val queryMap = (uri.rawQuery ?: "").split("&")
            .map { it.split("=", limit = 2) }
            .filter { it.size == 2 }
            .associate { (k, v) -> k to v }
        // get baud rate
        val baudRate = queryMap["baudrate"]?.toIntOrNull() ?: 9600
        logger.info("Using serial port $devicePath with baud rate $baudRate")
        // test os
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("linux") -> LinuxSerialMeshtasticClientPort(devicePath, baudRate)
            else -> throw IllegalArgumentException("Unsupported OS: $os")
        }
    }
}
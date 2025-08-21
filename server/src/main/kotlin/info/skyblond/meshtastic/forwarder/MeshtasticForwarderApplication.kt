package info.skyblond.meshtastic.forwarder

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MeshtasticForwarderApplication

fun main(args: Array<String>) {
    runApplication<MeshtasticForwarderApplication>(*args)
}

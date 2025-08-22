package info.skyblond.meshtastic.forwarder

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MeshtasticForwarderServer

fun main(args: Array<String>) {
    runApplication<MeshtasticForwarderServer>(*args)
}

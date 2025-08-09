package info.skyblond.meshtastic.forwarder.service

import build.buf.gen.meshtastic.ToRadio
import info.skyblond.meshtastic.forwarder.component.MeshtasticComponent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Service
class InfoRefreshService(
    private val meshtasticComponent: MeshtasticComponent
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(InfoRefreshService::class.java)

    private val executor = Executors.newSingleThreadScheduledExecutor()

    init {
        executor.scheduleAtFixedRate(
            { sendWantConfig() },
            2, 2, TimeUnit.MINUTES
        )
    }

    private fun sendWantConfig() {
        logger.info("Sending want_config to fresh info")
        meshtasticComponent.sendMessage(
            ToRadio.newBuilder()
                .setWantConfigId(meshtasticComponent.generatePacketId())
                .build()
        )
    }

    override fun close() {
        executor.shutdown()
    }
}
package info.skyblond.meshtastic.forwarder.service

import build.buf.gen.meshtastic.FromRadio
import info.skyblond.meshtastic.forwarder.component.AbstractMeshtasticComponentConsumer
import info.skyblond.meshtastic.forwarder.component.MeshtasticComponent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Consume [build.buf.gen.meshtastic.DeviceMetadata] messages and print log.
 * */
@Service
class MetadataLoggingService(
    meshtasticComponent: MeshtasticComponent
) : AbstractMeshtasticComponentConsumer(meshtasticComponent) {
    private val logger = LoggerFactory.getLogger(MetadataLoggingService::class.java)

    override suspend fun consume(message: FromRadio) {
        if (message.payloadVariantCase != FromRadio.PayloadVariantCase.METADATA) return
        val metadata = message.metadata
        logger.info("Device firmware version: ${metadata.firmwareVersion}")
        logger.info("Device role: ${metadata.role}")
        logger.info("Device hardware model: ${metadata.hwModel}")
        logger.info("Device support PKC: ${metadata.hasPKC}")
    }
}
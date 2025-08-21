package info.skyblond.meshtastic.forwarder.service

import build.buf.gen.meshtastic.Config
import build.buf.gen.meshtastic.FromRadio
import build.buf.gen.meshtastic.HardwareModel
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

    // These infos are not important, thus only use volatiles
    @Volatile
    private var firmwareVersion = ""
    @Volatile
    private var role = Config.DeviceConfig.Role.UNRECOGNIZED
    @Volatile
    private var hardwareModel = HardwareModel.UNRECOGNIZED


    override suspend fun consume(message: FromRadio) {
        if (message.payloadVariantCase != FromRadio.PayloadVariantCase.METADATA) return
        val metadata = message.metadata

        if (hardwareModel != metadata.hwModel) {
            hardwareModel = metadata.hwModel
            logger.info("Device hardware model: ${metadata.hwModel}")
        }
        if (firmwareVersion != metadata.firmwareVersion) {
            firmwareVersion = metadata.firmwareVersion
            logger.info("Device firmware version: ${metadata.firmwareVersion}")
        }
        if (role != metadata.role) {
            role = metadata.role
            logger.info("Device role: ${metadata.role}")
        }
    }
}
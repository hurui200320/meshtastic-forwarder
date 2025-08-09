package info.skyblond.meshtastic.forwarder.service

import build.buf.gen.meshtastic.ClientNotification
import build.buf.gen.meshtastic.FromRadio
import build.buf.gen.meshtastic.LogRecord
import com.google.protobuf.GeneratedMessage
import info.skyblond.meshtastic.forwarder.asTimestamp
import info.skyblond.meshtastic.forwarder.component.AbstractMeshtasticComponentConsumer
import info.skyblond.meshtastic.forwarder.component.MeshtasticComponent
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import org.springframework.stereotype.Service
import kotlin.math.log

/**
 * Consume [build.buf.gen.meshtastic.DeviceMetadata] messages and print log.
 * */
@Service
class ClientNotificationLoggingService(
    meshtasticComponent: MeshtasticComponent
) : AbstractMeshtasticComponentConsumer(meshtasticComponent) {
    private val logger = LoggerFactory.getLogger(ClientNotificationLoggingService::class.java)

    private fun GeneratedMessage.generateLogString() = this.allFields.entries.joinToString(", ") {
        "${it.key}=${it.value}"
    }

    override suspend fun consume(message: FromRadio) {
        if (message.payloadVariantCase != FromRadio.PayloadVariantCase.CLIENTNOTIFICATION) return
        val notify = message.clientNotification
        val content = when (notify.payloadVariantCase) {
            ClientNotification.PayloadVariantCase.KEY_VERIFICATION_NUMBER_INFORM -> notify.keyVerificationNumberInform
            ClientNotification.PayloadVariantCase.KEY_VERIFICATION_NUMBER_REQUEST -> notify.keyVerificationNumberRequest
            ClientNotification.PayloadVariantCase.KEY_VERIFICATION_FINAL -> notify.keyVerificationFinal
            ClientNotification.PayloadVariantCase.DUPLICATED_PUBLIC_KEY -> notify.duplicatedPublicKey
            ClientNotification.PayloadVariantCase.LOW_ENTROPY_KEY -> notify.lowEntropyKey
            ClientNotification.PayloadVariantCase.PAYLOADVARIANT_NOT_SET -> null
        }
        val logString = "Received device notification: " +
                "level=${notify.level}, " +
                "replyId=${notify.replyId}, " +
                "time=${notify.time.asTimestamp()}, " +
                "message='${notify.message}'" +
                (content?.generateLogString()?.let { ", $it" } ?: "")
        when (notify.level) {
            LogRecord.Level.CRITICAL -> logger.error(logString)
            LogRecord.Level.ERROR -> logger.error(logString)
            LogRecord.Level.WARNING -> logger.warn(logString)
            LogRecord.Level.DEBUG -> logger.debug(logString)
            LogRecord.Level.TRACE -> logger.trace(logString)
            else -> logger.info(logString)
        }
    }
}
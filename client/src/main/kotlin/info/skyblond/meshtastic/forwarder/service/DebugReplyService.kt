package info.skyblond.meshtastic.forwarder.service

import build.buf.gen.meshtastic.MeshPacket
import info.skyblond.meshtastic.forwarder.common.isBroadcast
import info.skyblond.meshtastic.forwarder.common.isNotEmojiReaction
import info.skyblond.meshtastic.forwarder.common.isTextMessage
import info.skyblond.meshtastic.forwarder.common.toNodeIdIsBroadcast
import info.skyblond.meshtastic.forwarder.component.AbstractComponent
import info.skyblond.meshtastic.forwarder.component.MeshPacketBus
import info.skyblond.meshtastic.forwarder.lib.http.MFHttpClient
import kotlinx.coroutines.flow.toList
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty("meshtastic.client.features.debug-reply.primary-channel.enabled")
class DebugReplyService(
    meshPacketBus: MeshPacketBus,
    mfHttpClient: MFHttpClient
) : AbstractComponent(meshPacketBus, mfHttpClient) {
    private val logger = LoggerFactory.getLogger(DebugReplyService::class.java)

    init {
        logger.info("Debug reply service enabled, will reply debug info on primary channel")
    }

    override suspend fun filter(packet: MeshPacket): Boolean =
        packet.isTextMessage()
                && packet.isBroadcast()
                && packet.channel == 0
                && packet.isNotEmojiReaction()

    override suspend fun consume(packet: MeshPacket) {
        val payload = packet.decoded
        logger.info(
            "Received message for debugging: from={}, payload={}",
            packet.from.toUInt(), payload.payload.toStringUtf8()
        )
        val channel = channels().getOrNull(packet.channel)
        val nodes = nodes().toList().associateBy { it.num }
        val viaMqtt =
            if (packet.viaMqtt) "Via MQTT"
            else "Via mesh"
        val packetId = "PacketId: ${packet.id.toUInt()}"
        val signalInfo =
            if (packet.hopStart != packet.hopLimit) "Hop: ${packet.hopStart - packet.hopLimit}, " +
                    "last hop possibly via one of these nodes: \n\t${
                        nodes.entries.filter { it.key and 0xff == packet.relayNode }
                            .joinToString("\n\t") { it.value.user?.longName ?: "<unknown node>" }
                            .ifEmpty { "unknown node num whose last byte is ${packet.relayNode}" }
                    }"
            else "SNR: ${packet.rxSnr}dB, RSSI: ${packet.rxRssi}dBm"
        val nodeInfo =
            "FromNode: ${
                nodes[packet.from]?.user?.longName ?: "<unknown name>"
            } (id=${packet.from.toUInt()})\n" +
                    "ToNode: ${if (packet.to.toNodeIdIsBroadcast()) "broadcast" else packet.to.toUInt()}\n" +
                    "Channel: ${
                        channel?.settings?.name?.ifEmpty { "Default" } ?: "<unknown channel>"
                    }"
        val replyInfo =
            if (payload.replyId != 0) "Reply to message #${payload.replyId.toUInt()}" else ""
        val replyMessage = listOf(
            viaMqtt, packetId, signalInfo, nodeInfo, replyInfo
        ).filter { it.isNotBlank() }.joinToString("\n")

        replyTextMessage(packet, replyMessage)
            .onFailure { logger.error("Failed to reply debug info", it) }
            .onSuccess { logger.info("Debug reply delivered") }
    }

    override fun onCancel() {
        // nop
    }
}
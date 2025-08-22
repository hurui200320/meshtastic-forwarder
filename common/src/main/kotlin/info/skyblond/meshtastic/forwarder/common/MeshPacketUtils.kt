package info.skyblond.meshtastic.forwarder.common

import build.buf.gen.meshtastic.MeshPacket
import build.buf.gen.meshtastic.PortNum


fun MeshPacket.isTextMessage() =
    this.payloadVariantCase == MeshPacket.PayloadVariantCase.DECODED
            && this.decoded.portnum == PortNum.TEXT_MESSAGE_APP

fun MeshPacket.isEmojiReaction() =
    this.payloadVariantCase == MeshPacket.PayloadVariantCase.DECODED
            && this.decoded.emoji != 0

fun MeshPacket.isNotEmojiReaction() = !this.isEmojiReaction()

fun MeshPacket.isBroadcast() = this.to.toNodeIdIsBroadcast()
fun MeshPacket.isNotBroadcast() = !this.isBroadcast()
package info.skyblond.meshtastic.forwarder.common

import build.buf.gen.meshtastic.Config
import com.google.protobuf.ByteString
import java.time.Instant
import java.time.ZonedDateTime
import java.util.*

val MESH_PACKET_TO_BROADCAST_NODE_ID_UINT = 0xFFFFFFFFu
val MESH_PACKET_TO_BROADCAST_NODE_ID = MESH_PACKET_TO_BROADCAST_NODE_ID_UINT.toInt()

fun Int.toNodeIdIsBroadcast() = this == MESH_PACKET_TO_BROADCAST_NODE_ID
fun Int.asTimestamp(): ZonedDateTime {
    val instant = Instant.ofEpochSecond(this.toUInt().toLong())
    return ZonedDateTime.ofInstant(instant, ZonedDateTime.now().zone)
}

fun ByteString.toBase64(): String = Base64.getEncoder().encodeToString(this.toByteArray())
fun ByteString.toHex(): String =
    joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

fun Map<Int, Config>.getLoraConfig() = this[Config.PayloadVariantCase.LORA.number]?.lora
fun Map<Int, Config>.getSecurityConfig() = this[Config.PayloadVariantCase.SECURITY.number]?.security


package info.skyblond.meshtastic.forwarder

import build.buf.gen.meshtastic.Config
import com.google.protobuf.ByteString
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

val MESH_PACKET_TO_BROADCAST_NODE_ID_UINT = 0xFFFFFFFFu
val MESH_PACKET_TO_BROADCAST_NODE_ID = MESH_PACKET_TO_BROADCAST_NODE_ID_UINT.toInt()

fun Int.toNodeIdIsBroadcast() = this == MESH_PACKET_TO_BROADCAST_NODE_ID
fun ByteString.toBase64(): String = Base64.getEncoder().encodeToString(this.toByteArray())
fun ByteString.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

fun ConcurrentHashMap<Int, Config>.getLoraConfig() = this[Config.PayloadVariantCase.LORA.number]
fun ConcurrentHashMap<Int, Config>.getSecurityConfig() = this[Config.PayloadVariantCase.SECURITY.number]

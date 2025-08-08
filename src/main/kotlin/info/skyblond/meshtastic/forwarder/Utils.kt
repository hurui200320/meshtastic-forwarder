package info.skyblond.meshtastic.forwarder

val MESH_PACKET_TO_BROADCAST_NODE_ID_UINT = 0xFFFFFFFFu
val MESH_PACKET_TO_BROADCAST_NODE_ID = MESH_PACKET_TO_BROADCAST_NODE_ID_UINT.toInt()

fun Int.toNodeIdIsBroadcast() = this == MESH_PACKET_TO_BROADCAST_NODE_ID
fun UInt.toNodeIdIsBroadcast() = this == MESH_PACKET_TO_BROADCAST_NODE_ID_UINT
package info.skyblond.meshtastic.forwarder.lib.ws

@Suppress("CanBeParameter")
class WebSocketClosedException(
    val statusCode: Int,
    val reason: String
) : Exception("WebSocket closed: statusCode=${statusCode}, reason='$reason'")
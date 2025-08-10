package info.skyblond.meshtastic.forwarder.client

import java.lang.Exception

@Suppress("CanBeParameter")
class WebSocketClosedException(
    val statusCode: Int,
    val reason: String
): Exception("WebSocket closed: statusCode=${statusCode}, reason='$reason'") {
}
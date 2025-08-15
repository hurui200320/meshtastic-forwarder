package info.skyblond.meshtastic.forwarder.client.ws

import build.buf.gen.meshtastic.MeshPacket
import info.skyblond.meshtastic.forwarder.client.ws.WebSocketClosedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.eclipse.jetty.websocket.api.Callback
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.StatusCode
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage
import org.eclipse.jetty.websocket.api.annotations.WebSocket
import java.nio.ByteBuffer

@Suppress("unused")
@WebSocket
class ClientEndpoint(
    private val meshPacketSharedFlow: MutableSharedFlow<Result<MeshPacket>>
) {
    @OnWebSocketClose
    fun onClose(statusCode: Int, reason: String) {
        runBlocking(Dispatchers.IO) {
            meshPacketSharedFlow.emit(
                Result.failure(
                    WebSocketClosedException(statusCode, reason)
                )
            )
        }
    }

    @OnWebSocketError
    fun onError(session: Session, cause: Throwable) {
        session.close(
            StatusCode.NORMAL,
            "Client error: message=${cause.message}",
            Callback.NOOP
        )
    }

    @OnWebSocketMessage
    fun onMessage(bytes: ByteBuffer, callback: Callback) {
        val result = runCatching {
            MeshPacket.parseFrom(bytes)
        }.onFailure { callback.fail(it) }
            .onSuccess { callback.succeed() }
        runBlocking(Dispatchers.IO) {
            meshPacketSharedFlow.emit(result)
        }
    }
}
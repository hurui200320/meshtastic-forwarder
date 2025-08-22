package info.skyblond.meshtastic.forwarder.lib.ws

import build.buf.gen.meshtastic.MeshPacket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.CompletableFuture

class MeshPacketWebSocketListener(
    private val meshPacketChannel: Channel<MeshPacket>,
    private val initialConnectFuture: CompletableFuture<Unit>
) : WebSocketListener() {

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        runBlocking(Dispatchers.IO) {
            meshPacketChannel.close(cause = WebSocketClosedException(code, reason))
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        if (initialConnectFuture.isDone) {
            runBlocking(Dispatchers.IO) {
                meshPacketChannel.close(cause = t)
            }
        } else {
            initialConnectFuture.completeExceptionally(t)
        }
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        initialConnectFuture.complete(Unit)
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        val result = runCatching {
            MeshPacket.parseFrom(bytes.toByteArray())
        }
        runBlocking(Dispatchers.IO) {
            if (result.isFailure) {
                meshPacketChannel.close(cause = result.exceptionOrNull()!!)
            } else {
                result.getOrNull()?.let { meshPacketChannel.send(it) }
            }
        }
    }
}
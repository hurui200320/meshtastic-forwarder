package info.skyblond.meshtastic.forwarder.lib.ws

import build.buf.gen.meshtastic.MeshPacket
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import java.util.concurrent.CompletableFuture

class MFWebSocketClient(
    okoHttpClient: OkHttpClient,
    serverBaseUrl: String,
    enableTls: Boolean
) : AutoCloseable {
    private val websocket: WebSocket

    private val _meshPacketChannel = Channel<MeshPacket>(
        capacity = Channel.BUFFERED, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val meshPacketListener: MeshPacketWebSocketListener

    /**
     * The flow of received [MeshPacket], will cache latest 64 messages.
     * When the buffer is overflowed, drop the oldest message.
     *
     * Will throw [WebSocketClosedException] if the websocket is failed.
     * */
    val meshPacketChannel: ReceiveChannel<MeshPacket> = _meshPacketChannel

    init {
        val connectFuture = CompletableFuture<Unit>()
        meshPacketListener = MeshPacketWebSocketListener(_meshPacketChannel, connectFuture)
        websocket = okoHttpClient.newWebSocket(
            Request.Builder()
                .url("${if (enableTls) "wss" else "ws"}://$serverBaseUrl/ws/packet")
                .build(),
            meshPacketListener
        )
        connectFuture.get()
    }

    override fun close() {
        websocket.close(1000, "Client closed")
    }
}
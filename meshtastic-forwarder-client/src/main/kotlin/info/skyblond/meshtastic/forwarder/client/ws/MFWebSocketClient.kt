package info.skyblond.meshtastic.forwarder.client.ws

import build.buf.gen.meshtastic.MeshPacket
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.map
import org.eclipse.jetty.client.HttpClient
import org.eclipse.jetty.util.component.LifeCycle
import org.eclipse.jetty.websocket.api.Callback
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest
import org.eclipse.jetty.websocket.client.WebSocketClient
import java.net.URI
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class MFWebSocketClient(
    httpClient: HttpClient,
    serverBaseUrl: String,
    enableTls: Boolean,
    token: String,
) : AutoCloseable {
    private val wsClient = WebSocketClient(httpClient)
    private val wsSessionRef = AtomicReference<Session?>(null)
    private val meshPacketSharedFlow = MutableSharedFlow<Result<MeshPacket>>()
    private val wsClientEndpoint = ClientEndpoint(meshPacketSharedFlow)

    /**
     * The flow of received [MeshPacket], will cache latest 64 messages.
     * When the buffer is overflowed, drop the oldest message.
     *
     * Will throw [WebSocketClosedException] if the websocket is failed.
     * */
    val meshPacketFlow: Flow<MeshPacket> = meshPacketSharedFlow
        .map { it.getOrThrow() }
        .buffer(64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        wsClient.idleTimeout = Duration.ofSeconds(15)
        wsClient.start()
        connectWebSocket(serverBaseUrl, enableTls, token)
        scope.launch {
            while (true) {
                delay(5000)
                wsSessionRef.get()?.let { session ->
                    if (session.isOpen) {
                        session.sendPing(ByteBuffer.allocate(0), Callback.NOOP)
                    }
                }
            }
        }
    }

    private fun connectWebSocket(serverBaseUrl: String, enableTls: Boolean, token: String) {
        val url = URI.create("${if (enableTls) "wss" else "ws"}://$serverBaseUrl/ws/packet")
        val request = ClientUpgradeRequest()
        request.setHeader("Authorization", "Bearer $token")
        wsClient.connect(wsClientEndpoint, url, request)
            .thenApply {
                // set a new one to ref and close the old one
                wsSessionRef.getAndSet(it)?.close()
            }
            .get()
    }

    override fun close() {
        scope.cancel()
        // From jetty document, we want to stop WebSocketClient from a thread
        // that is not owned by WebSocketClient itself
        thread { LifeCycle.stop(wsClient) }
    }
}
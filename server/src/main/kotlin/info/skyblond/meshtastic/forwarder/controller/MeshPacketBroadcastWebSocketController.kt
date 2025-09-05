package info.skyblond.meshtastic.forwarder.controller

import build.buf.gen.meshtastic.FromRadio
import info.skyblond.meshtastic.forwarder.component.MeshtasticComponent
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import org.slf4j.LoggerFactory
import org.springframework.web.socket.BinaryMessage
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.PingMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.BinaryWebSocketHandler
import java.util.concurrent.ConcurrentLinkedQueue
import javax.annotation.PostConstruct

class MeshPacketBroadcastWebSocketController(
    private val meshtasticComponent: MeshtasticComponent,
) : BinaryWebSocketHandler(), AutoCloseable {
    private val logger = LoggerFactory.getLogger(MeshPacketBroadcastWebSocketController::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun handleBinaryMessage(session: WebSocketSession, message: BinaryMessage) {
        // do not accept incoming messages
        session.close(CloseStatus.POLICY_VIOLATION)
    }

    private val sessions = ConcurrentLinkedQueue<WebSocketSession>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        logger.info("Connection established: id={}, remote={}", session.id, session.remoteAddress)
        sessions.add(session)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        logger.info("Connection terminated: id={}, status={}", session.id, status)
        sessions.remove(session)
    }

    @PostConstruct
    fun init() {
        meshtasticComponent.messageFlow
            .filter { // only forward packet
                it.payloadVariantCase == FromRadio.PayloadVariantCase.PACKET
            }
            .map { it.packet }
            .buffer(64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
            .onEach { message ->
                sessions.asSequence().map { session ->
                    scope.async {
                        if (session.isOpen.not()) return@async
                        runCatching {
                            session.sendMessage(BinaryMessage(message.toByteArray()))
                        }.onFailure {
                            logger.error(
                                "Error during forwarding message to ws session {}",
                                session.id, it
                            )
                        }
                    }
                }.toList().awaitAll()
            }.launchIn(scope)
        // periodically send ping to prevent idle timeout
        scope.launch {
            while (true) {
                delay(5000)
                sessions.asSequence().filter { it.isOpen }.map { session ->
                    scope.async {
                        runCatching {
                            session.sendMessage(PingMessage())
                        }.onFailure {
                            session.close(CloseStatus.SESSION_NOT_RELIABLE)
                        }
                    }
                }.toList().awaitAll()
            }
        }
    }

    override fun close() {
        scope.cancel()
    }
}
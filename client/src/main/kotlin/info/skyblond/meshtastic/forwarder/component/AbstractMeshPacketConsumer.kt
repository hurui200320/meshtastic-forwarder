package info.skyblond.meshtastic.forwarder.component

import build.buf.gen.meshtastic.MeshPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.slf4j.LoggerFactory

abstract class AbstractMeshPacketConsumer(
    meshPacketBus: MeshPacketBus
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(AbstractMeshPacketConsumer::class.java)
    protected val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        meshPacketBus.messageFlow
            .filter { runCatching { filter(it) }.getOrDefault(false) }
            .onEach { packet ->
                runCatching { consume(packet) }
                    .onFailure { logger.error("Failed to consume packet", it) }
            }.launchIn(scope)
    }

    protected abstract suspend fun filter(packet: MeshPacket): Boolean

    protected abstract suspend fun consume(packet: MeshPacket)

    protected abstract fun onCancel()

    override fun close() {
        onCancel()
        scope.cancel()
    }
}
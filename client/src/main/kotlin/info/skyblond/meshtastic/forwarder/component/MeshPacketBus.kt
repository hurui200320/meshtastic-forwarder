package info.skyblond.meshtastic.forwarder.component

import build.buf.gen.meshtastic.MeshPacket
import info.skyblond.meshtastic.forwarder.lib.MeshtasticForwarderClient
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.buffer
import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicReference

@Component
class MeshPacketBus(
    client: MeshtasticForwarderClient
) : HealthIndicator, AutoCloseable {
    private val logger = LoggerFactory.getLogger(MeshPacketBus::class.java)

    private val messageSharedFlow = MutableSharedFlow<MeshPacket>()
    val messageFlow = messageSharedFlow.asSharedFlow()
        // ensure others won't block us.
        // caching 4k messages should be generous enough.
        .buffer(capacity = 4096, onBufferOverflow = BufferOverflow.DROP_LATEST)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val statusIndicator = AtomicReference(Health.up().build())

    init {
        scope.launch {
            logger.info("Start forwarding mesh packets to bus")
            try {
                for (packet in client.meshPacketChannel) {
                    messageSharedFlow.emit(packet)
                }
                logger.error("Loop for forwarding message to bus is broken")
                statusIndicator.set(Health.down().build())
            } catch (t: Throwable) {
                logger.error("Failed to read mesh packet", t)
                statusIndicator.set(Health.down(t).build())
            }
        }
    }

    override fun health(): Health = statusIndicator.get()

    override fun close() {
        scope.cancel()
        statusIndicator.set(Health.down().build())
    }
}
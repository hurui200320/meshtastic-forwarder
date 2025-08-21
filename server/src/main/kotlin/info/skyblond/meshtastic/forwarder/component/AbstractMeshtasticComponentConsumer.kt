package info.skyblond.meshtastic.forwarder.component

import build.buf.gen.meshtastic.FromRadio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.slf4j.LoggerFactory

abstract class AbstractMeshtasticComponentConsumer(
    meshtasticComponent: MeshtasticComponent
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(AbstractMeshtasticComponentConsumer::class.java)
    protected val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        meshtasticComponent.messageFlow.onEach { message ->
            runCatching { consume(message) }
                .onFailure { logger.error("Failed to consume message", it) }
        }.launchIn(scope)
    }

    protected abstract suspend fun consume(message: FromRadio)

    override fun close() {
        scope.cancel()
    }
}
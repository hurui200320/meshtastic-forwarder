package info.skyblond.meshtastic.forwarder.component

import build.buf.gen.meshtastic.FromRadio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

abstract class AbstractConsumerComponent(
    meshtasticComponent: MeshtasticComponent
): AutoCloseable {
    protected val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        meshtasticComponent.messageFlow.onEach(::consume).launchIn(scope)
    }

    protected abstract suspend fun consume(message: FromRadio)

    override fun close() {
        scope.cancel()
    }
}
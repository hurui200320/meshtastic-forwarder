package info.skyblond.meshtastic.forwarder.component

import build.buf.gen.meshtastic.Config
import build.buf.gen.meshtastic.FromRadio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class ConfigStoreComponent(
    meshtasticComponent: MeshtasticComponent
) : AbstractMeshtasticComponentConsumer(meshtasticComponent) {
    private val logger = LoggerFactory.getLogger(ConfigStoreComponent::class.java)

    private val updateMutex = Mutex()

    // Config payload variant enum value -> config
    private val configs = ConcurrentHashMap<Int, Config>()
    private val configsStateFlow = MutableStateFlow(configs.toMap())
    val configsFlow = configsStateFlow.asStateFlow()

    override suspend fun consume(message: FromRadio) {
        if (message.payloadVariantCase == FromRadio.PayloadVariantCase.CONFIG) {
            updateMutex.withLock {
                logger.info(
                    "Get config: variantEnum={}, variantId={}",
                    message.config.payloadVariantCase,
                    message.config.payloadVariantCase.number
                )
                configs.compute(message.config.payloadVariantCase.number) { _, _ ->
                    message.config
                }
                configsStateFlow.emit(configs.toMap())
            }
        }
    }
}
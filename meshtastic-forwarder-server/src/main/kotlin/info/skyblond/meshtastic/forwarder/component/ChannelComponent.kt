package info.skyblond.meshtastic.forwarder.component

import build.buf.gen.meshtastic.Channel
import build.buf.gen.meshtastic.FromRadio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ChannelComponent(
    meshtasticComponent: MeshtasticComponent
) : AbstractMeshtasticComponentConsumer(meshtasticComponent) {
    private val logger = LoggerFactory.getLogger(ChannelComponent::class.java)

    private val channels = Array<Channel>(8) { Channel.getDefaultInstance() }
    private val channelStateFlow = MutableStateFlow(channels.toList())
    val channelFlow = channelStateFlow.asStateFlow()

    private val updateMutex = Mutex()

    override suspend fun consume(message: FromRadio) {
        updateMutex.withLock {
            if (message.payloadVariantCase != FromRadio.PayloadVariantCase.CHANNEL) return
            val channelMessage = message.channel
            channels[channelMessage.index] = channelMessage
            channelStateFlow.emit(channels.toList())
            if (channelMessage.role != Channel.Role.DISABLED) {
                logger.debug(
                    "Get config for active channel #{} ({}): {}",
                    channelMessage.index,
                    channelMessage.role,
                    channelMessage.settings.name.ifEmpty { "Default" }
                )
            } else {
                logger.debug(
                    "Ignore disabled disabled channel #{}: {}",
                    channelMessage.index, channelMessage.settings.name.ifEmpty { "Default" }
                )
            }
        }
    }

}
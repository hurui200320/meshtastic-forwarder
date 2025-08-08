package info.skyblond.meshtastic.forwarder.component

import build.buf.gen.meshtastic.FromRadio
import build.buf.gen.meshtastic.MyNodeInfo
import info.skyblond.meshtastic.forwarder.toHex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class MyNodeInfoComponent(
    meshtasticComponent: MeshtasticComponent
) : AbstractMeshtasticComponentConsumer(meshtasticComponent) {
    private val logger = LoggerFactory.getLogger(MyNodeInfoComponent::class.java)

    private val updateMutex = Mutex()
    private val myNodeInfoStateFlow = MutableStateFlow(MyNodeInfo.getDefaultInstance())
    val myNodeInfoFlow = myNodeInfoStateFlow.asStateFlow()

    override suspend fun consume(message: FromRadio) {
        if (message.payloadVariantCase != FromRadio.PayloadVariantCase.MY_INFO) return
        updateMutex.withLock {
            val myInfo = message.myInfo
            logger.info("Device PlatformIO environment: ${myInfo.pioEnv}")
            logger.info("Device id: ${myInfo.deviceId.toHex()}")
            logger.info("Device node number: ${myInfo.myNodeNum.toUInt()}")
            logger.info("Device node db entry size: ${myInfo.nodedbCount}")
            myNodeInfoStateFlow.emit(myInfo)
        }
    }
}
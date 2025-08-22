package info.skyblond.meshtastic.forwarder.component

import build.buf.gen.meshtastic.*
import info.skyblond.meshtastic.forwarder.common.toHex
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

    private val myUserInfoStateFlow = MutableStateFlow(User.getDefaultInstance())
    val myUserInfoFlow = myUserInfoStateFlow.asStateFlow()

    override suspend fun consume(message: FromRadio) {
        if (message.payloadVariantCase == FromRadio.PayloadVariantCase.MY_INFO) {
            handleMyNodeInfo(message.myInfo)
        }
        if (message.payloadVariantCase == FromRadio.PayloadVariantCase.PACKET) {
            handleMeshPacket(message.packet)
        }
    }

    private suspend fun handleMyNodeInfo(myInfo: MyNodeInfo) {
        updateMutex.withLock {
            if (myNodeInfoStateFlow.value != myInfo) {
                logger.info("Device PlatformIO environment: ${myInfo.pioEnv}")
                logger.info("Device id: ${myInfo.deviceId.toHex()}")
                logger.info("Device node number: ${myInfo.myNodeNum.toUInt()}")
                myNodeInfoStateFlow.emit(myInfo)
            }
        }
    }

    private suspend fun handleMeshPacket(packet: MeshPacket) {
        if (packet.payloadVariantCase != MeshPacket.PayloadVariantCase.DECODED) return
        val decoded = packet.decoded
        if (decoded.portnum != PortNum.ADMIN_APP) return
        val admin = AdminMessage.parseFrom(decoded.payload)
        if (admin.payloadVariantCase != AdminMessage.PayloadVariantCase.GET_OWNER_RESPONSE) return
        val owner = admin.getOwnerResponse
        myUserInfoStateFlow.emit(owner)
        logger.info("Device owner: ${owner.longName}")
    }
}
package info.skyblond.meshtastic.forwarder.component

import build.buf.gen.meshtastic.FromRadio
import build.buf.gen.meshtastic.MeshPacket
import build.buf.gen.meshtastic.NodeInfo
import build.buf.gen.meshtastic.PortNum
import build.buf.gen.meshtastic.User
import info.skyblond.meshtastic.forwarder.toBase64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class NodeInfoComponent(
    meshtasticComponent: MeshtasticComponent
) : AbstractMeshtasticComponentConsumer(meshtasticComponent) {
    private val logger = LoggerFactory.getLogger(NodeInfoComponent::class.java)

    private val updateMutex = Mutex()
    private val nodes = ConcurrentHashMap<Int, NodeInfo>()
    private val nodesStateFlow = MutableStateFlow(nodes.toMap())
    val nodesFlow = nodesStateFlow.asStateFlow()


    override suspend fun consume(message: FromRadio) {
        if (message.payloadVariantCase == FromRadio.PayloadVariantCase.NODE_INFO) {
            processNodeInfo(message.nodeInfo)
        } else if (message.payloadVariantCase == FromRadio.PayloadVariantCase.PACKET) {
            if (message.packet.payloadVariantCase == MeshPacket.PayloadVariantCase.DECODED) {
                if (message.packet.decoded.portnum == PortNum.NODEINFO_APP) {
                    runCatching { User.parseFrom(message.packet.decoded.payload) }
                        .getOrNull()
                        ?.let { user ->
                            processUserInfo(message.packet.from, user)
                        }
                }
            }
        }

    }

    private suspend fun processNodeInfo(nodeInfo: NodeInfo) {
        updateMutex.withLock {
            logger.info(
                "Get info for node #{}: userId={}, shortName='{}', longName='{}', pubKey='{}'",
                nodeInfo.num.toUInt(),
                nodeInfo.user.id, nodeInfo.user.shortName, nodeInfo.user.longName,
                nodeInfo.user.publicKey.toBase64()
            )
            nodes.compute(nodeInfo.num) { _, _ ->
                nodeInfo
            }
            nodesStateFlow.emit(nodes.toMap())
        }
    }

    private suspend fun processUserInfo(nodeId: Int, userInfo: User) {
        updateMutex.withLock {
            logger.info(
                "Get info for node #{}: userId={}, shortName='{}', longName='{}', pubKey='{}'",
                nodeId.toUInt(),
                userInfo.id, userInfo.shortName, userInfo.longName,
                userInfo.publicKey.toBase64()
            )
            nodes.compute(nodeId) { _, v ->
                val oldValue = v ?: NodeInfo.getDefaultInstance()
                oldValue.toBuilder().setUser(userInfo).build()
            }
            nodesStateFlow.emit(nodes.toMap())
        }
    }
}
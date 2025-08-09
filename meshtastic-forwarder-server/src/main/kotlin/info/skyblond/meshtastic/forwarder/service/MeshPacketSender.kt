package info.skyblond.meshtastic.forwarder.service

import build.buf.gen.meshtastic.MeshPacket
import com.google.protobuf.ByteString
import info.skyblond.meshtastic.forwarder.component.ConfigStoreComponent
import info.skyblond.meshtastic.forwarder.component.MeshtasticComponent
import info.skyblond.meshtastic.forwarder.component.MyNodeInfoComponent
import info.skyblond.meshtastic.forwarder.component.NodeInfoComponent
import info.skyblond.meshtastic.forwarder.getLoraConfig
import info.skyblond.meshtastic.forwarder.toNodeIdIsBroadcast
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture

/**
 * Send a message
 * */
@Service
class MeshPacketSender(
    private val myNodeInfoComponent: MyNodeInfoComponent,
    private val nodeInfoComponent: NodeInfoComponent,
    private val configStoreComponent: ConfigStoreComponent,
    private val meshtasticComponent: MeshtasticComponent,
) {
    /**
     * Send a [MeshPacket] with the following overwrite:
     *
     * + Force set [MeshPacket.from_] to our node id
     * + Set packet id to 0 if the message doesn't need ACK and is not broadcast message
     * + Set packet id with generated one if the message need ACK or is broadcast and id is 0
     * + Set [MeshPacket.pkiEncrypted_] to true if public key is not empty
     * + Set public key and enable pki encryption if the to node is in the node db (controlled by [autoEncrypt])
     * + Set lora hop to device config if 0 (controlled by [autoLoraHop])
     * */
    fun sendPacket(
        packet: MeshPacket,
        autoEncrypt: Boolean = true,
        autoLoraHop: Boolean = true
    ): CompletableFuture<Unit> {
        val packetToSend = packet.toBuilder()
            // force overwrite from id
            .setFrom(myNodeInfoComponent.myNodeInfoFlow.value.myNodeNum)
            .apply { // check id
                if (!wantAck && !to.toNodeIdIsBroadcast()) {
                    // no ack or no broadcast message, doesn't require id
                    setId(0)
                } else {
                    // an unique id is required for wantAck or broadcast message
                    if (id == 0) setId(meshtasticComponent.generatePacketId())
                }
            }
            .apply { // set pki to true if has public key
                if (publicKey != ByteString.EMPTY) {
                    setPkiEncrypted(true)
                    setChannel(0)
                }
            }
            .apply { // check if target has known public key
                if (autoEncrypt && !to.toNodeIdIsBroadcast() && !pkiEncrypted) {
                    val pubKey = nodeInfoComponent.nodesFlow.value[to]?.user?.publicKey
                    pubKey?.let {
                        setPkiEncrypted(true)
                        setPublicKey(it)
                    }
                }
            }
            .apply { // check lora hop
                if (autoLoraHop && hopLimit == 0) {
                    val hopLimit = configStoreComponent.configsFlow.value
                        .getLoraConfig()?.hopLimit ?: 3
                    setHopLimit(hopLimit)
                }
            }
            .build()
        return meshtasticComponent.sendMeshPacket(packetToSend)
    }
}
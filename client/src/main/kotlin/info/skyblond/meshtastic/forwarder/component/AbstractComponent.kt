package info.skyblond.meshtastic.forwarder.component

import build.buf.gen.meshtastic.*
import com.google.protobuf.ByteString
import info.skyblond.meshtastic.forwarder.common.MESH_PACKET_TO_BROADCAST_NODE_ID
import info.skyblond.meshtastic.forwarder.common.toNodeIdIsBroadcast
import info.skyblond.meshtastic.forwarder.lib.http.MFHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

abstract class AbstractComponent(
    meshPacketBus: MeshPacketBus,
    private val mfHttpClient: MFHttpClient
) : AbstractMeshPacketConsumer(meshPacketBus) {

    protected fun nodes(): Flow<NodeInfo> = flow {
        withContext(Dispatchers.IO) {
            mfHttpClient.getNodes()
        }.forEach {
            withContext(Dispatchers.IO) {
                mfHttpClient.getNode(it)
            }?.let { emit(it) }
        }
    }

    protected suspend fun channels(): List<Channel> =
        withContext(Dispatchers.IO) {
            mfHttpClient.getChannels()
        }

    protected suspend fun generatePacketId(): Int =
        withContext(Dispatchers.IO) {
            mfHttpClient.generateNewPacketId()
        }

    protected suspend fun myNodeInfo(): MyNodeInfo =
        withContext(Dispatchers.IO) {
            mfHttpClient.getMyNodeInfo()
        }

    protected suspend fun myUserInfo(): User =
        withContext(Dispatchers.IO) {
            mfHttpClient.getMyUserInfo()
        }

    private inline fun <reified T> Map<Int, Config>.getConfig(
        variantCase: Config.PayloadVariantCase
    ): T {
        val config = this[variantCase.number] ?: error("Config variant $variantCase not found")
        val oneOf = Config.getDescriptor().oneofs.find { it.name == "payload_variant" }!!
        val desc = config.getOneofFieldDescriptor(oneOf)
        return config.getField(desc) as T
    }

    protected suspend fun deviceConfig(): Config.DeviceConfig =
        withContext(Dispatchers.IO) {
            mfHttpClient.getConfigs()
        }.getConfig(Config.PayloadVariantCase.DEVICE)

    protected suspend fun positionConfig(): Config.PositionConfig =
        withContext(Dispatchers.IO) {
            mfHttpClient.getConfigs()
        }.getConfig(Config.PayloadVariantCase.POSITION)

    protected suspend fun powerConfig(): Config.PowerConfig =
        withContext(Dispatchers.IO) {
            mfHttpClient.getConfigs()
        }.getConfig(Config.PayloadVariantCase.POWER)

    protected suspend fun networkConfig(): Config.NetworkConfig =
        withContext(Dispatchers.IO) {
            mfHttpClient.getConfigs()
        }.getConfig(Config.PayloadVariantCase.NETWORK)

    protected suspend fun displayConfig(): Config.DisplayConfig =
        withContext(Dispatchers.IO) {
            mfHttpClient.getConfigs()
        }.getConfig(Config.PayloadVariantCase.DISPLAY)

    protected suspend fun loraConfig(): Config.LoRaConfig =
        withContext(Dispatchers.IO) {
            mfHttpClient.getConfigs()
        }.getConfig(Config.PayloadVariantCase.LORA)

    protected suspend fun bluetoothConfig(): Config.BluetoothConfig =
        withContext(Dispatchers.IO) {
            mfHttpClient.getConfigs()
        }.getConfig(Config.PayloadVariantCase.BLUETOOTH)

    protected suspend fun securityConfig(): Config.SecurityConfig =
        withContext(Dispatchers.IO) {
            mfHttpClient.getConfigs()
        }.getConfig(Config.PayloadVariantCase.SECURITY)

    protected suspend fun sessionKeyConfig(): Config.SessionkeyConfig =
        withContext(Dispatchers.IO) {
            mfHttpClient.getConfigs()
        }.getConfig(Config.PayloadVariantCase.SESSIONKEY)

    protected suspend fun deviceUiConfig(): DeviceUIConfig =
        withContext(Dispatchers.IO) {
            mfHttpClient.getConfigs()
        }.getConfig(Config.PayloadVariantCase.DEVICE_UI)

    protected suspend fun sendMeshPacket(
        meshPacket: MeshPacket,
        autoEncrypt: Boolean = true,
        autoLoraHop: Boolean = true,
        timeout: Long = 120,
    ): Result<Int> {
        val packetId = if (meshPacket.wantAck) {
            if (meshPacket.id == 0) generatePacketId()
            else meshPacket.id
        } else 0
        val packetWithId = meshPacket.toBuilder().setId(packetId).build()

        return runCatching {
            withContext(Dispatchers.IO) {
                mfHttpClient.sendMeshPacket(packetWithId, autoEncrypt, autoLoraHop, timeout)
            }
        }.map { packetId }
    }

    protected suspend fun sendBroadcastTextMessage(
        message: String, channelIndex: Int = 0
    ) = sendMeshPacket(
        MeshPacket.newBuilder()
            .setId(generatePacketId())
            .setTo(MESH_PACKET_TO_BROADCAST_NODE_ID)
            .setChannel(channelIndex)
            .setWantAck(true)
            .setDecoded(
                Data.newBuilder()
                    .setPortnum(PortNum.TEXT_MESSAGE_APP)
                    .setPayload(ByteString.copyFromUtf8(message))
                    .build()
            )
            .build()
    )

    protected suspend fun sendPrivateTextMessage(
        message: String, toNodeNum: Int
    ) = sendMeshPacket(
        MeshPacket.newBuilder()
            .setId(generatePacketId())
            .setTo(toNodeNum)
            .setWantAck(true)
            .setDecoded(
                Data.newBuilder()
                    .setPortnum(PortNum.TEXT_MESSAGE_APP)
                    .setPayload(ByteString.copyFromUtf8(message))
                    .build()
            )
            .build()
    )

    protected suspend fun replyTextMessage(packet: MeshPacket, replyMessage: String) =
        if (packet.to.toNodeIdIsBroadcast()) {
            sendMeshPacket(
                MeshPacket.newBuilder()
                    .setId(generatePacketId())
                    .setTo(MESH_PACKET_TO_BROADCAST_NODE_ID)
                    .setChannel(packet.channel)
                    .setWantAck(true)
                    .setDecoded(
                        Data.newBuilder()
                            .setPortnum(PortNum.TEXT_MESSAGE_APP)
                            .setPayload(ByteString.copyFromUtf8(replyMessage))
                            .setReplyId(packet.id)
                            .build()
                    )
                    .build()
            )
        } else {
            sendMeshPacket(
                MeshPacket.newBuilder()
                    .setId(generatePacketId())
                    .setTo(packet.from)
                    .setWantAck(true)
                    .setDecoded(
                        Data.newBuilder()
                            .setPortnum(PortNum.TEXT_MESSAGE_APP)
                            .setPayload(ByteString.copyFromUtf8(replyMessage))
                            .setReplyId(packet.id)
                            .build()
                    )
                    .build()
            )
        }
}

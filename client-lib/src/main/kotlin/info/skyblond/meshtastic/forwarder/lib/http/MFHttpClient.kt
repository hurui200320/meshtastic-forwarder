package info.skyblond.meshtastic.forwarder.lib.http

import build.buf.gen.meshtastic.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.*


class MFHttpClient(
    private val deviceInfoService: DeviceInfoService,
    private val sendMessageService: SendMessageService,
) {
    private fun String.decodeBase64(): ByteArray =
        Base64.getDecoder().decode(this)

    fun getChannels(): List<Channel> = runBlocking(Dispatchers.IO) {
        deviceInfoService.getChannels()
            .map { Channel.parseFrom(it.decodeBase64()) }
    }

    fun getConfigs(): Map<Int, Config> = runBlocking(Dispatchers.IO) {
        deviceInfoService.getConfigs()
            .mapValues { Config.parseFrom(it.value.decodeBase64()) }
    }

    fun getMyNodeInfo(): MyNodeInfo = runBlocking(Dispatchers.IO) {
        MyNodeInfo.parseFrom(deviceInfoService.getMyNodeInfo().decodeBase64())
    }

    /**
     * Generate a unique packet ID that has not been used yet.
     * */
    fun generateNewPacketId(): Int = runBlocking(Dispatchers.IO) {
        deviceInfoService.generateNewPacketId()
    }

    /**
     * A set of node numbers.
     * */
    fun getNodes(): Set<Int> = runBlocking(Dispatchers.IO) {
        deviceInfoService.getNodes()
    }

    fun getNode(nodeNum: Int): NodeInfo? = runBlocking(Dispatchers.IO) {
        deviceInfoService.getNode(nodeNum)
            ?.let { NodeInfo.parseFrom(it.decodeBase64()) }
    }

    @Throws(RequestFailedException::class)
    fun sendMeshPacket(
        meshPacket: MeshPacket,
        autoEncrypt: Boolean = true,
        autoLoraHop: Boolean = true,
        timeout: Long = 120,
    ) {
        val resp = runBlocking(Dispatchers.IO) {
            sendMessageService.sendMeshPacket(
                meshPacket = meshPacket.toByteArray(),
                autoEncrypt = autoEncrypt,
                autoLoraHop = autoLoraHop,
                timeout = timeout
            )
        }
        if (resp.isSuccessful)
            return // we're good

        throw RequestFailedException(resp)
    }
}
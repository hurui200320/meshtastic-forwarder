package info.skyblond.meshtastic.forwarder.controller

import build.buf.gen.meshtastic.Channel
import build.buf.gen.meshtastic.Config
import build.buf.gen.meshtastic.MyNodeInfo
import build.buf.gen.meshtastic.NodeInfo
import info.skyblond.meshtastic.forwarder.component.*
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/device")
class DeviceInfoController(
    private val channelController: ChannelComponent,
    private val configStoreComponent: ConfigStoreComponent,
    private val myNodeInfoComponent: MyNodeInfoComponent,
    private val meshtasticComponent: MeshtasticComponent,
    private val nodeInfoComponent: NodeInfoComponent
) {

    @GetMapping("/channels")
    fun getChannels(): List<Channel> {
        return channelController.channelFlow.value
    }

    @GetMapping("/configs")
    fun getConfigs(): Map<Int, Config> {
        return configStoreComponent.configsFlow.value
    }

    @GetMapping("/myNodeInfo")
    fun getMyNodeInfo(): MyNodeInfo {
        return myNodeInfoComponent.myNodeInfoFlow.value
    }

    @GetMapping("/generateNewPacketId")
    fun generateNewPacketId(): Int {
        return meshtasticComponent.generatePacketId()
    }

    @GetMapping("/nodes")
    fun listNodeNumbers(): Set<Int> {
        return nodeInfoComponent.nodesFlow.value.keys
    }

    @GetMapping("/nodes/{nodeNum}")
    fun getNodeInfo(@PathVariable("nodeNum") nodeNum: Int): NodeInfo? {
        return nodeInfoComponent.nodesFlow.value[nodeNum]
    }
}
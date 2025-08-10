package info.skyblond.meshtastic.forwarder.service

import build.buf.gen.meshtastic.AdminMessage
import build.buf.gen.meshtastic.Data
import build.buf.gen.meshtastic.PortNum
import build.buf.gen.meshtastic.ToRadio
import info.skyblond.meshtastic.forwarder.component.ConfigStoreComponent
import info.skyblond.meshtastic.forwarder.component.MeshtasticComponent
import info.skyblond.meshtastic.forwarder.component.MyNodeInfoComponent
import info.skyblond.meshtastic.forwarder.config.MeshtasticClientConfigProperties
import info.skyblond.meshtastic.forwarder.getSecurityConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Service
class InfoRefreshService(
    configProperties: MeshtasticClientConfigProperties,
    private val meshtasticComponent: MeshtasticComponent,
    private val myNodeInfoComponent: MyNodeInfoComponent,
    private val configStoreComponent: ConfigStoreComponent,
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(InfoRefreshService::class.java)

    private val executor = Executors.newSingleThreadScheduledExecutor()

    init {
        executor.scheduleAtFixedRate(
            {
                sendWantConfig()
                sendSetTime()
            },
            configProperties.configRefreshMinutes,
            configProperties.configRefreshMinutes,
            TimeUnit.MINUTES
        )
    }

    private fun sendWantConfig() {
        logger.info("Sending want_config to fresh info")
        meshtasticComponent.sendMessage(
            ToRadio.newBuilder()
                .setWantConfigId(meshtasticComponent.generatePacketId())
                .build()
        )
    }

    private fun sendSetTime() {
        val pubKey = configStoreComponent.configsFlow.value.getSecurityConfig()?.publicKey
            ?: return
        logger.info("Sending set time")
        meshtasticComponent.sendDataMeshPacket(
            from = myNodeInfoComponent.myNodeInfoFlow.value.myNodeNum,
            to = myNodeInfoComponent.myNodeInfoFlow.value.myNodeNum,
            wantAck = false,
            hopLimit = 0,
            publicKey = pubKey,
            pkiEncrypted = true,
            data = Data.newBuilder()
                .setPortnum(PortNum.ADMIN_APP)
                .setPayload(
                    AdminMessage.newBuilder()
                        .setSetTimeOnly((System.currentTimeMillis() / 1000).toInt())
                        .build()
                        .toByteString()
                )
                .build()
        )
    }

    override fun close() {
        executor.shutdown()
    }
}
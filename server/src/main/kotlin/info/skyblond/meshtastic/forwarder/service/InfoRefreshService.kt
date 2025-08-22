package info.skyblond.meshtastic.forwarder.service

import build.buf.gen.meshtastic.*
import info.skyblond.meshtastic.forwarder.common.getSecurityConfig
import info.skyblond.meshtastic.forwarder.component.ConfigStoreComponent
import info.skyblond.meshtastic.forwarder.component.MeshtasticComponent
import info.skyblond.meshtastic.forwarder.component.MyNodeInfoComponent
import info.skyblond.meshtastic.forwarder.config.MeshtasticClientConfigProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@Service
class InfoRefreshService(
    configProperties: MeshtasticClientConfigProperties,
    private val meshtasticComponent: MeshtasticComponent,
    private val myNodeInfoComponent: MyNodeInfoComponent,
    private val configStoreComponent: ConfigStoreComponent,
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(InfoRefreshService::class.java)

    private val executor = Executors.newSingleThreadScheduledExecutor()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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
        meshtasticComponent.messageFlow.onEach { message ->
            if (message.payloadVariantCase != FromRadio.PayloadVariantCase.CONFIG_COMPLETE_ID)
                return@onEach
            val configCompleteId = message.configCompleteId
            val startTime = configIdToTimeMap[configCompleteId] ?: return@onEach
            val duration = System.currentTimeMillis() - startTime
            val minute = duration / 1000 / 60.0
            if (minute >= configProperties.configRefreshMinutes) {
                logger.warn(
                    "Config fresh took {} minutes, which is longer than the refresh interval",
                    minute
                )
            } else {
                logger.info("Config refresh took {} minutes", minute)
            }
        }.launchIn(scope)
    }

    private val configIdToTimeMap = ConcurrentHashMap<Int, Long>()

    private fun sendWantConfig() {
        logger.info("Sending want_config to fresh info")
        val id = Random.nextInt()
        meshtasticComponent.sendMessage(
            ToRadio.newBuilder()
                .setWantConfigId(id)
                .build()
        ).thenAccept {
            configIdToTimeMap[id] = System.currentTimeMillis()
        }
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
        scope.cancel()
    }
}
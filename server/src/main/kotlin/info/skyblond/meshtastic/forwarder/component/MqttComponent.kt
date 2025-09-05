package info.skyblond.meshtastic.forwarder.component

import build.buf.gen.meshtastic.*
import com.google.protobuf.ByteString
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.MqttAsyncClient.generateClientId
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URI
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Component for handling MQTT proxy.
 * */
@Component
class MqttComponent(
    private val meshtasticComponent: MeshtasticComponent,
    private val channelComponent: ChannelComponent
) : AbstractMeshtasticComponentConsumer(meshtasticComponent) {
    private val logger = LoggerFactory.getLogger(MqttComponent::class.java)

    private val mqttClientRef = AtomicReference<MqttAsyncClient?>()
    private val subscribedTopics = ConcurrentSkipListSet<String>()

    @Volatile
    private var rootTopic: String = DEFAULT_TOPIC_ROOT

    init {
        channelComponent.channelFlow.onEach(::onChannelChange).launchIn(scope)
    }

    override suspend fun consume(message: FromRadio) {
        when (message.payloadVariantCase) {
            FromRadio.PayloadVariantCase.MODULECONFIG ->
                if (message.moduleConfig.payloadVariantCase == ModuleConfig.PayloadVariantCase.MQTT) {
                    connect(message.moduleConfig.mqtt)
                }

            FromRadio.PayloadVariantCase.MQTTCLIENTPROXYMESSAGE ->
                publish(message.mqttClientProxyMessage)

            else -> {
                /* nop */
            }
        }
    }

    @Synchronized
    fun connect(config: ModuleConfig.MQTTConfig) {
        if (!config.enabled || !config.proxyToClientEnabled) {
            logger.info("MQTT is not enabled or proxy to client is not enabled, skip MQTT connecting attempt")
            return
        }
        val scheme = if (config.tlsEnabled) "ssl" else "tcp"
        val (host, port) = config.address.ifEmpty { DEFAULT_SERVER_ADDRESS }
            .split(":", limit = 2).let { it[0] to (it.getOrNull(1)?.toIntOrNull() ?: 1883) }

        // check current if connected to the same server
        mqttClientRef.get()?.let { currentClient ->
            val currentServerUri = currentClient.currentServerURI ?: ""
            val targetServerUri = "$scheme://$host:$port"
            if (currentClient.isConnected && currentServerUri == targetServerUri) {
                return
            }
        }

        val mqttClient = MqttAsyncClient(
            URI(scheme, null, host, port, "", "", "").toString(),
            generateClientId(),
            MemoryPersistence(),
        )
        // disconnect old one
        disconnect()
        subscribedTopics.clear()
        mqttClientRef.set(mqttClient)
        // warn json topic
        if (config.jsonEnabled) {
            logger.warn("This implementation doesn't support MQTT JSON topic, ignored...")
        }

        rootTopic = config.root.ifEmpty { DEFAULT_TOPIC_ROOT }
        logger.info("MQTT root topic: $rootTopic")

        mqttClient.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String) {
                logger.info("MQTT connected to $serverURI, reconnect=$reconnect")
                onChannelChange(channelComponent.channelFlow.value)
            }

            override fun connectionLost(cause: Throwable) {
                logger.warn("MQTT connection lost: $cause")
                disconnect(force = true)
            }

            private val lastPrintMqttReceive = AtomicLong(System.nanoTime())
            private val receivedMessageCounter = AtomicInteger(0)

            override fun messageArrived(topic: String, message: MqttMessage) {
                logger.debug("Forward MQTT message to device: topic=${topic}, size=${message.payload.size}")
                synchronized(receivedMessageCounter) {
                    val counter = receivedMessageCounter.incrementAndGet()
                    val lastPrint = lastPrintMqttReceive.get()
                    // duration in seconds
                    val duration = (System.nanoTime() - lastPrint) / 1_000_000_000
                    // min interval 60s, or print every 100 messages
                    if (duration >= 60 || counter >= 100) {
                        lastPrintMqttReceive.set(System.nanoTime())
                        receivedMessageCounter.set(0)
                        logger.info("Received $counter MQTT messages in last $duration seconds")
                    }
                }
                meshtasticComponent.sendMessage(
                    ToRadio.newBuilder()
                        .setMqttClientProxyMessage(
                            MqttClientProxyMessage.newBuilder()
                                .setTopic(topic)
                                .setData(ByteString.copyFrom(message.payload))
                                .setRetained(message.isRetained)
                                .build()
                        )
                        .build()
                )
            }

            override fun deliveryComplete(token: IMqttDeliveryToken) {
                logger.debug("MQTT deliveryComplete messageId: ${token.messageId}")
            }
        })
        mqttClient.setBufferOpts(DisconnectedBufferOptions().apply {
            isBufferEnabled = true
            bufferSize = 512
            isPersistBuffer = false
            isDeleteOldestMessages = true
        })
        mqttClient.connect(generateConnectionOptions(config)).waitForCompletion()
        logger.info("MQTT client connected")
    }

    fun onChannelChange(channelList: List<Channel>) {
        val mqttClient = mqttClientRef.get() ?: return
        val topicNames = channelList
            .filter { it.role != Channel.Role.DISABLED }
            .filter { it.settings.downlinkEnabled }
            .map { "$rootTopic$DEFAULT_TOPIC_LEVEL${it.settings.name}/+" }
            .toMutableSet()

        // PKI for dm
        topicNames.add("$rootTopic${DEFAULT_TOPIC_LEVEL}PKI/+")

        synchronized(subscribedTopics) {
            (subscribedTopics - topicNames).forEach {
                logger.info("Unsubscribing from topic: $it")
                mqttClient.unsubscribe(it)
                subscribedTopics.remove(it)
            }

            topicNames.forEach {
                if (!subscribedTopics.contains(it)) {
                    logger.info("Subscribing to topic: $it")
                    mqttClient.subscribe(it, DEFAULT_QOS)
                    subscribedTopics.add(it)
                }
            }
        }
    }

    fun publish(message: MqttClientProxyMessage) {
        logger.info("Forward MQTT message from device: topic=${message.topic}, size=${message.data.size()}")

        val messageBytes = when (message.payloadVariantCase) {
            MqttClientProxyMessage.PayloadVariantCase.DATA -> message.data.toByteArray()
            MqttClientProxyMessage.PayloadVariantCase.TEXT -> message.text.encodeToByteArray()
            MqttClientProxyMessage.PayloadVariantCase.PAYLOADVARIANT_NOT_SET -> ByteArray(0)
        }

        mqttClientRef.get()?.publish(
            message.topic, messageBytes, DEFAULT_QOS, message.retained
        ) ?: logger.warn("MQTT client is not connected, skip publishing")
    }

    @Synchronized
    fun disconnect(force: Boolean = false) {
        val client = mqttClientRef.getAndSet(null) ?: return
        subscribedTopics.clear()
        logger.info("Disconnecting MQTT client...")
        if (force) {
            runCatching { client.disconnectForcibly() }
        } else {
            // give 3s timeout for disconnecting
            runCatching { client.disconnect().waitForCompletion(3000) }
        }
        logger.info("Closing MQTT client...")
        // force to close the connection anyway
        runCatching { client.close(true) }
    }

    private fun generateConnectionOptions(config: ModuleConfig.MQTTConfig): MqttConnectOptions {
        val sslContext = SSLContext.getInstance("TLS")
        // Create a custom SSLContext that trusts all certificates
        sslContext.init(
            null, arrayOf<TrustManager>(
                // an impl to trust all certificates
                object : X509TrustManager {
                    override fun checkClientTrusted(
                        chain: Array<out X509Certificate>?, authType: String?
                    ) {
                    }

                    override fun checkServerTrusted(
                        chain: Array<out X509Certificate>?, authType: String?
                    ) {
                    }

                    override fun getAcceptedIssuers(): Array<out X509Certificate> = arrayOf()
                }
            ), SecureRandom()
        )

        return MqttConnectOptions().apply {
            userName = config.username
            password = config.password.toCharArray()
            isAutomaticReconnect = true
            if (config.tlsEnabled) {
                socketFactory = sslContext.socketFactory
            }
            maxInflight = 100
            keepAliveInterval = 30
        }
    }

    override fun close() {
        super.close()
        disconnect(force = true)
    }

    companion object {
        /**
         * Quality of Service (QoS) levels in MQTT:
         * - QoS 0: "at most once". Packets are sent once without validation if it has been received.
         * - QoS 1: "at least once". Packets are sent and stored until the client receives confirmation from the server. MQTT ensures delivery, but duplicates may occur.
         * - QoS 2: "exactly once". Similar to QoS 1, but with no duplicates.
         */
        private const val DEFAULT_QOS = 1
        private const val DEFAULT_TOPIC_ROOT = "msh"
        private const val DEFAULT_TOPIC_LEVEL = "/2/e/"
        private const val DEFAULT_SERVER_ADDRESS = "mqtt.meshtastic.org"
    }
}
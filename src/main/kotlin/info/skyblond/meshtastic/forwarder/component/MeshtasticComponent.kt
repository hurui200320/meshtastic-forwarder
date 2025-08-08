package info.skyblond.meshtastic.forwarder.component

import build.buf.gen.meshtastic.*
import com.google.protobuf.ByteString
import info.skyblond.meshtastic.forwarder.api.client.MeshtasticClientApiReader
import info.skyblond.meshtastic.forwarder.api.client.MeshtasticClientApiWriter
import info.skyblond.meshtastic.forwarder.api.client.MeshtasticClientPort
import info.skyblond.meshtastic.forwarder.toNodeIdIsBroadcast
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock
import kotlin.random.Random

/**
 * This is a low-level component that assembles the [MeshtasticClientApiReader] and [MeshtasticClientApiWriter]
 * to provide a basic interface for interacting with the meshtastic device.
 * */
@Component
class MeshtasticComponent(
    private val clientPort: MeshtasticClientPort
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(MeshtasticComponent::class.java)

    @Volatile
    private var connectionPack: ConnectionPack? = null
    private val connectionLock = ReentrantReadWriteLock()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun connect() {
        logger.info("Connecting to meshtastic device...")
        connectionLock.writeLock().withLock {
            val reader = MeshtasticClientApiReader(clientPort)
            connectionPack = ConnectionPack(
                reader = reader,
                writer = MeshtasticClientApiWriter(clientPort),
                readerMessagesLoopJob = processMessagesFromReader(reader)
            )
            // send want config id as the first message
            ensureSendMessage(
                ToRadio.newBuilder().setWantConfigId(Random.nextInt()).build()
            )
        }
        logger.info("Connected to meshtastic device")
    }

    fun disconnect() {
        connectionLock.writeLock().withLock {
            connectionPack?.let {
                logger.info("Disconnecting from meshtastic device...")
            }
            // try to tell the device we're about to disconnect
            trySendMessage(ToRadio.newBuilder().setDisconnect(true).build())
            connectionPack?.writer?.close()
            connectionPack?.reader?.close()
            connectionPack?.readerMessagesLoopJob?.cancel()
            connectionPack?.let {
                logger.info("Disconnected from meshtastic device")
            }
            connectionPack = null
        }
    }

    /**
     * Try to send a message. If the connection is not ready,
     * the message will be discarded without throwing any error.
     *
     * Return after the message is sent to the device (if connection is ready).
     * */
    private fun trySendMessage(message: ToRadio) {
        connectionLock.readLock().withLock {
            runBlocking(Dispatchers.IO) {
                connectionPack?.writer?.enqueueMessageToSend(message)?.join()
            }
        }
    }

    /**
     * Ensure the connection is ready and send a message.
     * If the connection is not ready after three times of retry,
     * will throw an [IllegalStateException].
     *
     * Return after the message is sent to the device.
     * */
    private fun ensureSendMessage(message: ToRadio) {
        repeat(3) {
            connectionLock.readLock().withLock {
                if (connectionPack != null) return@repeat
                logger.warn(
                    "Meshtastic port connection not ready, cannot send message. Retry ({}/{})...",
                    it + 1, 3
                )
                Thread.sleep(1000)
            }
        }
        connectionLock.readLock().withLock {
            if (connectionPack == null) {
                throw IllegalStateException("Read interface is not open")
            }
            runBlocking(Dispatchers.IO) {
                connectionPack?.writer?.enqueueMessageToSend(message)?.join()
            }
        }
    }

    private val packetCounter = AtomicLong(Random(System.currentTimeMillis()).nextLong())

    fun generatePacketId(): Int {
        // 32bit unsigned max value
        val mask = 0xFFFFFFFFuL
        // now the result must be smaller than the MAX 32bit unsigned value
        val safeValue = packetCounter.getAndIncrement().toULong() % mask
        // use the lower 32bit as packet id, +1 to avoid 0
        return (safeValue.toUInt() + 1u).toInt()
    }

    /**
     * Send message, return a future which completes when the message is ACKed.
     *
     * For messages with payload [MeshPacket], see [sendMeshPacket].
     * */
    fun sendMessage(message: ToRadio): CompletableFuture<Unit> {
        return when (message.payloadVariantCase) {
            ToRadio.PayloadVariantCase.PACKET -> sendMeshPacket(message.packet)
            else -> {
                ensureSendMessage(message)
                // this type of message doesn't require any ack
                CompletableFuture.completedFuture(Unit)
            }
        }
    }

    /**
     * Send a [MeshPacket] message, return a future which completes when the message is ACKed.
     *
     * For messages with payload [Data], see [sendDataMeshPacket].
     * */
    fun sendMeshPacket(packet: MeshPacket): CompletableFuture<Unit> {
        return when (packet.payloadVariantCase) {
            MeshPacket.PayloadVariantCase.DECODED -> sendDataMeshPacket(
                from = packet.from,
                to = packet.to,
                wantAck = packet.wantAck,
                hopLimit = packet.hopLimit,
                data = packet.decoded,
                packetId = if (packet.id != 0) packet.id else generatePacketId(),
                priority = packet.priority,
                channel = packet.channel,
                publicKey = packet.publicKey,
                pkiEncrypted = packet.pkiEncrypted,
            )

            else -> {
                ensureSendMessage(ToRadio.newBuilder().setPacket(packet).build())
                // this type of message doesn't require any ack
                CompletableFuture.completedFuture(Unit)
            }
        }
    }

    /**
     * A map of pending ack packets.
     *
     * Packet id -> (to, future)
     * */
    private val pendingAckPackets = ConcurrentHashMap<Int, Pair<Int, CompletableFuture<Unit>>>()

    /**
     * Send a [MeshPacket] with [Data] payload, return a future which completes when the message is ACKed.
     *
     * Ref: https://buf.build/meshtastic/protobufs/docs/master:meshtastic#meshtastic.MeshPacket
     * */
    fun sendDataMeshPacket(
        from: Int,
        to: Int,
        wantAck: Boolean,
        hopLimit: Int,
        data: Data,
        packetId: Int = generatePacketId(),
        priority: MeshPacket.Priority = MeshPacket.Priority.UNSET,
        channel: Int = 0,
        publicKey: ByteString? = null,
        pkiEncrypted: Boolean = false,
    ): CompletableFuture<Unit> {
        val meshPacket = MeshPacket.newBuilder()
            .setId(packetId)
            .setFrom(from)
            .setTo(to)
            .setHopLimit(hopLimit)
            .setPriority(priority)
            .setChannel(channel)
            .setWantAck(wantAck)
            .setDecoded(data)
            .setPublicKey(publicKey)
            .setPkiEncrypted(pkiEncrypted)
            .build()

        val pendingPacket = meshPacket.to to CompletableFuture<Unit>()
        pendingAckPackets.putIfAbsent(packetId, pendingPacket)
        if (pendingAckPackets[packetId] !== pendingPacket) {
            // the entry in the map is not the one we created,
            // which suggests that the packet id is already used and pending for ACK.
            logger.error("Packet id collision, packet id: {}", packetId)
            return CompletableFuture.failedFuture(IllegalStateException("Packet id collision"))
        }
        logger.info(
            "Sending data mesh packet: packetId={}, to={}, " +
                    "channel={}, wantAck={}, priority={}, hopLimit={}, pkiEncrypted={}",
            packetId,
            pendingPacket.first.let { n -> if (n.toNodeIdIsBroadcast()) "broadcast" else n.toUInt() },
            channel, wantAck, priority, hopLimit, pkiEncrypted
        )
        ensureSendMessage(ToRadio.newBuilder().setPacket(meshPacket).build())
        return pendingPacket.second
    }

    // TODO: send text message (broadcast, private) method for MeshtasticMessageSender

    private fun processMessageForAck(message: FromRadio) {
        if (message.payloadVariantCase != FromRadio.PayloadVariantCase.PACKET) return
        if (message.packet.payloadVariantCase != MeshPacket.PayloadVariantCase.DECODED) return
        if (message.packet.decoded.portnum != PortNum.ROUTING_APP) return
        val fromId = message.packet.from
        val routing = Routing.parseFrom(message.packet.decoded.payload)
        val ackPacketId = message.packet.decoded.requestId
        val errorReason = routing.errorReason

        val pendingPacket = pendingAckPackets.remove(ackPacketId)
            ?: run {
                logger.warn("[ACK] Received ack for unknown packet id: $ackPacketId")
                return
            }
        if (errorReason != Routing.Error.NONE) {
            pendingPacket.second.completeExceptionally(
                IOException("Packet delivery failed due to $errorReason")
            )
            logger.error(
                "[ACK] Mesh packet failed to deliver: ackPacketId={}, ackFrom={}, error={}",
                ackPacketId, fromId.toUInt(), errorReason
            )
        }
        if (pendingPacket.first.toNodeIdIsBroadcast()) {
            // broadcast message
            pendingPacket.second.complete(Unit)
            logger.info(
                "[ACK] Broadcast message delivered: ackPacketId={}, ackFrom={}",
                ackPacketId, fromId.toUInt()
            )
        } else {
            // private message
            if (fromId == pendingPacket.first) {
                pendingPacket.second.complete(Unit)
                logger.info(
                    "[ACK] Private message received by {}: ackPacketId={}",
                    fromId.toUInt(), ackPacketId
                )
            } else {
                logger.info(
                    "[ACK] Private message delivered to mesh, but not received yet: ackPacketId={}, ackFrom={}",
                    ackPacketId, fromId.toUInt()
                )
            }
        }
    }

    private val messageSharedFlow = MutableSharedFlow<FromRadio>()
    val messageFlow = messageSharedFlow.asSharedFlow()

    private fun processMessagesFromReader(reader: MeshtasticClientApiReader) = scope.launch {
        for (message in reader.messageChannel) {
            messageSharedFlow.emit(message)
        }
        disconnect()
    }

    init {
        messageFlow.onEach(::processMessageForAck).launchIn(scope)
        connect()
    }

    override fun close() {
        disconnect()
        scope.cancel()
    }

    private data class ConnectionPack(
        val reader: MeshtasticClientApiReader,
        val writer: MeshtasticClientApiWriter,
        val readerMessagesLoopJob: Job
    )
}
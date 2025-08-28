package info.skyblond.meshtastic.forwarder.api.client

import build.buf.gen.meshtastic.MeshPacket
import build.buf.gen.meshtastic.PortNum
import build.buf.gen.meshtastic.ToRadio
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.io.IOException

/**
 * The Reader for Meshtastic Client API.
 * */
class MeshtasticClientApiWriter(
    clientPort: MeshtasticClientPort
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(MeshtasticClientApiWriter::class.java)
    private val outputStream = clientPort.outputStream

    /**
     * A scope for all coroutines inside this object.
     * The [SupervisorJob] is used to ensure that the coroutines don't fail the whole scope.
     * */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val sendLock = Mutex()


    /**
     * For TEXT_MESSAGE_APP messages, the firmware requires an interval between sending messages.
     * */
    private val textMessageChannel =
        Channel<Pair<ToRadio, CompletableDeferred<Unit>>>(capacity = BUFFERED)

    private val textMessageSendJob = scope.launch {
        for ((message, deferred) in textMessageChannel) {
            try {
                val start = System.currentTimeMillis()
                logger.debug(
                    "Sending text message with 2s delay: packedId={}, size={}B",
                    message.packet.id.toUInt(), message.serializedSize
                )
                writeMessage(message)
                deferred.complete(Unit)
                // delay 2 sec between text messages, with some extra for safety
                if (System.currentTimeMillis() - start < 2050) {
                    delay(2100 - System.currentTimeMillis() + start)
                }
            } catch (e: IOException) {
                logger.error("Error sending text message", e)
                deferred.completeExceptionally(e)
                close()
            }
        }
    }

    private val defaultMessageChannel =
        Channel<Pair<ToRadio, CompletableDeferred<Unit>>>(capacity = BUFFERED)

    private val defaultMessageSendJob = scope.launch {
        for ((message, deferred) in defaultMessageChannel) {
            try {
                logger.debug(
                    "Sending {} message: size={}B",
                    message.payloadVariantCase, message.serializedSize
                )
                writeMessage(message)
                deferred.complete(Unit)
            } catch (t: Throwable) {
                logger.error("Error sending ToRadio message", t)
                deferred.completeExceptionally(t)
                close()
            }
        }
    }

    private suspend fun writeMessage(message: ToRadio) {
        sendLock.withLock {
            // 1 minutes should be more than enough to write and flush message.
            // This is used to detect if device is dead (not responding).
            withTimeout(60 * 1000) {
                val bytes = message.toByteArray()
                val size = bytes.size
                if (size > CLIENT_API_TO_RADIO_MAX_MESSAGE_LENGTH) {
                    throw IllegalArgumentException(
                        "ToRadio message too large, " +
                                "max: $CLIENT_API_TO_RADIO_MAX_MESSAGE_LENGTH bytes, " +
                                "actual: $size bytes"
                    )
                }
                // write wake up sequence
                outputStream.write(WAKE_UP_SEQUENCE)
                outputStream.flush()
                // then start writing the message
                outputStream.write(0x94)
                outputStream.write(0xc3)
                outputStream.write(size shr 8)
                outputStream.write(size and 0xff)
                outputStream.write(bytes)
                outputStream.flush()
            }
        }
    }

    private fun ToRadio.isTextMessageApp(): Boolean {
        if (payloadVariantCase != ToRadio.PayloadVariantCase.PACKET) return false
        if (packet.payloadVariantCase != MeshPacket.PayloadVariantCase.DECODED) return false
        return packet.decoded.portnum == PortNum.TEXT_MESSAGE_APP
    }

    suspend fun enqueueMessageToSend(message: ToRadio): Deferred<Unit> {
        val deferred = CompletableDeferred<Unit>()
        if (message.isTextMessageApp()) {
            textMessageChannel.send(message to deferred)
        } else {
            defaultMessageChannel.send(message to deferred)
        }
        return deferred
    }

    override fun close() {
        textMessageSendJob.cancel()
        defaultMessageSendJob.cancel()

        textMessageChannel.close()
        defaultMessageChannel.close()

        scope.cancel()
        runCatching { outputStream.flush() }
        outputStream.close()
    }
}
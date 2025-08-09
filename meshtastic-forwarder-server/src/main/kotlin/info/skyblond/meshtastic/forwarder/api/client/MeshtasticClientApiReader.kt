package info.skyblond.meshtastic.forwarder.api.client

import build.buf.gen.meshtastic.FromRadio
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import org.slf4j.LoggerFactory
import java.io.EOFException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The Reader for Meshtastic Client API.
 * */
class MeshtasticClientApiReader(
    clientPort: MeshtasticClientPort
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(MeshtasticClientApiReader::class.java)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val readLoopJob: Job

    private val inputStream = clientPort.inputStream
    private val readBufferSize = clientPort.readBufferSize

    init { // it's important to launch the read loop after other fields are initialized.
        readLoopJob = scope.launch { readLoop() }
    }

    private suspend fun readLoop() {
        // fixed buffer size for raw mode
        val readBuffer = ByteArray(readBufferSize)
        // state
        var state: State = State.WaitingForStart1
        try {
            while (true) {
                val readCount = inputStream.read(readBuffer)
                if (readCount == -1) {
                    throw EOFException("Input stream closed")
                }
                for (i in 0 until readCount) {
                    state = processByte(state, readBuffer[i])
                }
            }
        } catch (e: Throwable) {
            if (!closed.get()) {
                logger.error("Read loop failed", e)
                close(e)
            }
        } finally {
            close(null)
        }
    }

    private suspend fun processByte(state: State, byte: Byte): State {
        val nextState = when (state) {
            State.WaitingForStart1 -> onWaitingForStart1(byte)
            State.WaitingForStart2 -> onWaitingForStart2(byte)
            State.WaitingForMessageLengthMsb -> onWaitingForMessageLengthMsb(byte)
            is State.WaitingForMessageLengthLsb -> onWaitingForMessageLengthLsb(state, byte)
            is State.ReadingMessageBody -> onReadingMessageBody(state, byte)
        }
        return nextState
    }

    private fun onWaitingForStart1(byte: Byte): State {
        if (byte == CLIENT_API_MAGIC_START1) {
            logger.debug("Detected START1")
            return State.WaitingForStart2
        } else {
            // TODO: instead of discard log, figure out a way to provide it?
            return State.WaitingForStart1
        }
    }

    private fun onWaitingForStart2(byte: Byte): State {
        if (byte == CLIENT_API_MAGIC_START2) {
            logger.debug("Detected START2")
            return State.WaitingForMessageLengthMsb
        } else {
            logger.warn("START2 not detected, lost sync...")
            return State.WaitingForStart1
        }
    }

    private fun onWaitingForMessageLengthMsb(byte: Byte): State {
        val msb = byte.toInt() and 0xff
        logger.debug("Detected MSB: $msb")
        return State.WaitingForMessageLengthLsb(msb)
    }

    private fun onWaitingForMessageLengthLsb(
        state: State.WaitingForMessageLengthLsb, byte: Byte
    ): State {
        val msb = state.msb
        val lsb = byte.toInt() and 0xff
        logger.debug("Detected LSB: $lsb")
        val messageLength = (msb shl 8) or lsb
        logger.debug("Decoded message length: $messageLength")
        if (messageLength > CLIENT_API_FROM_RADIO_MAX_MESSAGE_LENGTH) {
            logger.warn("Message length too large ($messageLength), lost sync...")
            return State.WaitingForStart1
        } else if (messageLength == 0) {
            return State.WaitingForStart1
        } else {
            logger.debug("Start reading message body...")
            return State.ReadingMessageBody(
                messageBuffer = ByteArray(messageLength),
                bufferPos = 0
            )
        }
    }

    private val _messageChannel = Channel<FromRadio>()
    val messageChannel: ReceiveChannel<FromRadio> = _messageChannel

    private suspend fun onReadingMessageBody(state: State.ReadingMessageBody, byte: Byte): State {
        state.messageBuffer[state.bufferPos] = byte
        if (state.isLastByte()) {
            logger.debug("Message received with size ${state.messageBuffer.size}")
            runCatching { state.parseMessage() }
                .onFailure { logger.error("Failed to parse message", it) }
                .getOrNull()?.let { message ->
                    _messageChannel.send(message)
                }
        }
        return state.nextState()
    }

    private val closed = AtomicBoolean(false)

    private fun close(cause: Throwable?) {
        if (closed.compareAndSet(false, true)) {
            cause?.let {
                logger.error("MeshtasticClientApiReader closed due to an error", it)
            } ?: run {
                logger.info("MeshtasticClientApiReader closed")
            }
            readLoopJob.cancel()
            inputStream.close()
            _messageChannel.close(cause)
            scope.cancel()
        }
    }

    override fun close() = runBlocking(Dispatchers.IO) {
        close(null)
    }


    private sealed class State {
        /**
         * State for waiting START1.
         * */
        object WaitingForStart1 : State()

        /**
         * State for waiting START2.
         * */
        object WaitingForStart2 : State()

        /**
         * State for waiting MSB of message length.
         * */
        object WaitingForMessageLengthMsb : State()

        /**
         * State for waiting LSB of message length.
         * */
        data class WaitingForMessageLengthLsb(val msb: Int) : State()

        /**
         * State for reading message body.
         * */
        class ReadingMessageBody(
            val messageBuffer: ByteArray,
            val bufferPos: Int = 0,
        ) : State() {
            /**
             * Test if the current [bufferPos] points to the last bytes.
             * */
            fun isLastByte(): Boolean = bufferPos == messageBuffer.size - 1

            fun nextState() =
                if (isLastByte()) WaitingForStart1
                else ReadingMessageBody(messageBuffer, bufferPos + 1)

            fun parseMessage(): FromRadio =
                if (isLastByte()) FromRadio.parseFrom(messageBuffer)
                else throw IllegalStateException("Message body not fully read")
        }
    }
}
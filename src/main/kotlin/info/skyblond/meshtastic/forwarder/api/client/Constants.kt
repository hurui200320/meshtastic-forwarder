package info.skyblond.meshtastic.forwarder.api.client

/**
 * Max length for messages sent via the client api.
 * */
const val CLIENT_API_FROM_RADIO_MAX_MESSAGE_LENGTH = 512

/**
 * Max length for messages received via the client api.
 * */
const val CLIENT_API_TO_RADIO_MAX_MESSAGE_LENGTH = 512

/**
 * Magic byte 1 for the client api message header.
 * */
const val CLIENT_API_MAGIC_START1 = 0x94.toByte()

/**
 * Magic byte 2 for the client api message header.
 * */
const val CLIENT_API_MAGIC_START2 = 0xc3.toByte()

/**
 * Before writing the command, send this sequence to wake up the device in sleeping mode.
 * */
val WAKE_UP_SEQUENCE = ByteArray(32) { CLIENT_API_MAGIC_START1 }
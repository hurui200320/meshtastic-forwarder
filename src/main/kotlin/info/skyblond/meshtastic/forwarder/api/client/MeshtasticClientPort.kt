package info.skyblond.meshtastic.forwarder.api.client

import java.io.InputStream
import java.io.OutputStream

interface MeshtasticClientPort {
    val inputStream: InputStream
    val readBufferSize: Int
    val outputStream: OutputStream
}
package info.skyblond.meshtastic.forwarder.serial.linux

import info.skyblond.meshtastic.forwarder.api.client.MeshtasticClientPort
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class LinuxSerialMeshtasticClientPort(
    private val path: String,
    private val baudRate: Int,
) : MeshtasticClientPort {
    // fixed buffer size
    override val readBufferSize: Int = 4096

    override val inputStream: InputStream
        get() {
            setupSerialPort()
            return File(path).inputStream().buffered(readBufferSize)
        }

    override val outputStream: OutputStream
        get() {
            setupSerialPort()
            return File(path).outputStream()
        }

    private fun setupSerialPort() {
        try {
            val process = Runtime.getRuntime().exec(
                arrayOf(
                    "stty", "-F", path, baudRate.toString(), "raw", "-echo"
                )
            )
            if (process.waitFor() != 0) {
                throw IOException(
                    "Failed to configure serial port: ${
                        process.errorStream.bufferedReader().readText()
                    }"
                )
            }
        } catch (e: Exception) {
            throw IOException("Error configuring serial port", e)
        }
    }
}
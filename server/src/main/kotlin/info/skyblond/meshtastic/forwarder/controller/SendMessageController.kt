package info.skyblond.meshtastic.forwarder.controller

import build.buf.gen.meshtastic.MeshPacket
import info.skyblond.meshtastic.forwarder.service.MeshPacketSender
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.request.async.DeferredResult

@RestController
@RequestMapping("/send")
class SendMessageController(
    private val meshPacketSender: MeshPacketSender
) {

    @PostMapping("/meshPacket", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun sendMeshPacket(
        @RequestParam(value = "meshPacket", required = true)
        meshPacketByte: ByteArray,
        @RequestParam(value = "autoEncrypt", defaultValue = "true") autoEncrypt: Boolean,
        @RequestParam(value = "autoLoraHop", defaultValue = "true") autoLoraHop: Boolean,
        // second for timeout
        @RequestParam(value = "timeout", defaultValue = "120") timeout: Long,
    ): DeferredResult<ResponseEntity<String>> {
        val result = DeferredResult<ResponseEntity<String>>(
            timeout * 1000,
        ) {
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(
                    "Timeout when sending mesh packet. The packet might be send, " +
                            "but we didn't get ack before timeout."
                )
        }

        meshPacketSender.sendPacket(
            MeshPacket.parseFrom(meshPacketByte),
            autoEncrypt = autoEncrypt,
            autoLoraHop = autoLoraHop,
        )
            .thenAccept { result.setResult(ResponseEntity.status(HttpStatus.NO_CONTENT).build()) }
            .exceptionally {
                result.setErrorResult(
                    ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("Failed to send packet due to the following error: ${it.message}")
                )
                null
            }

        return result
    }
}
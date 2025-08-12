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
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/send")
class SendMessageController(
    private val meshPacketSender: MeshPacketSender
) {
    // TODO: auth, configure spring to decrypt token in auth header, and use the content as username
    //       PSK: SHA256 or blake3b 256 for key, and AES256 GCM for algo?

    @PostMapping("/meshPacket", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun sendMeshPacket(
        @RequestParam(value = "meshPacket", required = true)
        meshPacketMultipart: MultipartFile,
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
            MeshPacket.parseFrom(meshPacketMultipart.bytes),
            autoEncrypt = autoEncrypt,
            autoLoraHop = autoLoraHop,
        )
            .exceptionally {
                result.setErrorResult(
                    ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("Failed to send packet due to the following error: ${it.message}")
                )
            }
            .thenAccept { result.setResult(ResponseEntity.status(HttpStatus.NO_CONTENT).build()) }

        return result
    }
}
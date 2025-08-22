package info.skyblond.meshtastic.forwarder.component

import build.buf.gen.meshtastic.MeshPacket
import com.openai.client.OpenAIClient
import com.openai.core.JsonObject
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.StructuredChatCompletionCreateParams
import info.skyblond.meshtastic.forwarder.common.isNotBroadcast
import info.skyblond.meshtastic.forwarder.common.isNotEmojiReaction
import info.skyblond.meshtastic.forwarder.common.isTextMessage
import info.skyblond.meshtastic.forwarder.lib.http.MFHttpClient
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
@ConditionalOnProperty("meshtastic.client.features.enabled-gemini-on-private-chat")
class GeminiOnPrivateChatComponent(
    meshPacketBus: MeshPacketBus,
    mfHttpClient: MFHttpClient,
    private val openaiClient: OpenAIClient
) : AbstractComponent(meshPacketBus, mfHttpClient) {
    private val logger = LoggerFactory.getLogger(GeminiOnPrivateChatComponent::class.java)
    private val sessionMap =
        ConcurrentHashMap<Int, StructuredChatCompletionCreateParams.Builder<PrivateChatResponse>>()

    init {
        logger.info("Gemini on private chat service enabled, will use gemini to respond on private chat")
    }

    override suspend fun filter(packet: MeshPacket): Boolean =
        packet.isTextMessage()
                && packet.isNotBroadcast()
                && packet.isNotEmojiReaction()

    class PrivateChatResponse(
        val shouldEndConversation: Boolean,
        val response: String
    )

    private fun createNewParameterBuilder() =
        ChatCompletionCreateParams.builder()
            // TODO: config model?
            .model("gemini-2.5-pro")
            .maxCompletionTokens(5000)
            .responseFormat(PrivateChatResponse::class.java)
//            .putAdditionalBodyProperty(
//                // TODO: add content filter
//                "extra_body", JsonObject.of(
//                    mapOf(
//                        "google" to JsonObject.of(
//                            mapOf(
//                                "cached_content" to JsonObject.of(mapOf()),
//                                "safety_settings" to JsonObject.of(mapOf()),
//                                "thinking_config" to JsonObject.of(mapOf()),
//                            )
//                        )
//                    )
//                )
//            )
            .addDeveloperMessage(
                // TODO: give self node identity? build a better prompt via gemini 2.5?
                "You're answering user from a meshtastic device, " +
                        "this type of devices work like SMS, which has a limit of 200 bytes. " +
                        "Your response will be encoded using UTF-8, and you should reply in the same " +
                        "language as the user. To play it safe, you MUST limit your response to 140 characters. " +
                        "Longer than that will cause the message to be truncated. " +
                        "If you have more to say, you should offer a option for user to let you continue your " +
                        "response. If you have said all your response, then you should not repeat yourself, " +
                        "instead, you should explicitly ask user for future instructions. \n\n" +
                        "Also, since the meshtastic is using LoRa to send messages, " +
                        "your response may not be delivered to the user based on the signal strength, " +
                        "however, we have no way knowing if the user received your message or not. " +
                        "For user to us, it's the same, so if you don't understand what user means, " +
                        "you should ask the user to repeat or clarify the question or instruction. \n\n" +
                        "Once you finished the conversation, you may confirm with the user to close it. " +
                        "If confirmed, set `shouldEndConversation` to `true`, so the backend will " +
                        "automatically close the conversation and start a new one if user requested. " +
                        "you MUST confirm with user that if there is no further instructions " +
                        "and it's ok to end the conversation. Remember, it's rude to end the conversation " +
                        "without asking."
            )

    override suspend fun consume(packet: MeshPacket) {
        val textMessage = packet.decoded.payload.toStringUtf8()
        val fromNodeNum = packet.from
        logger.info(
            "Received message for gemini private chat: from={}, messageId={}. content={}",
            fromNodeNum.toUInt(), packet.id.toUInt(), textMessage
        )
        val session = sessionMap.getOrPut(fromNodeNum) { createNewParameterBuilder() }
        session.addUserMessage(textMessage)
        val messages = openaiClient.chat().completions()
            .create(session.build())
            .choices()
            .mapNotNull { it.message().content().orElse(null) }

        val shouldEndConversation = messages.any { it.shouldEndConversation }
        val replyText = messages.joinToString("\n") { it.response }.trim()

        logger.info(
            "Gemini reply for node #{} message #{}: length={}, size={}B, content={}",
            fromNodeNum.toUInt(), packet.id.toUInt(),
            replyText.length, replyText.toByteArray().size,
            replyText
        )

        replyTextMessage(packet, replyText)
            .onFailure {
                logger.error(
                    "Failed to reply gemini private chat response for node #{} message #{}",
                    fromNodeNum.toUInt(), packet.id.toUInt(), it
                )
            }
            .onSuccess {
                logger.info(
                    "Gemini private chat response delivered to node #{} message #{}",
                    fromNodeNum.toUInt(), packet.id.toUInt()
                )
            }

        if (shouldEndConversation) {
            logger.info("Gemini private chat conversation ended for node #{}", fromNodeNum.toUInt())
            sessionMap.remove(fromNodeNum)
        }
    }

    override fun onCancel() {
        sessionMap.clear()
    }
}
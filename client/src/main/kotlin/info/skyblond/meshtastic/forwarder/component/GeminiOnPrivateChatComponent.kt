package info.skyblond.meshtastic.forwarder.component

import build.buf.gen.meshtastic.MeshPacket
import com.google.genai.Chat
import com.google.genai.Client
import com.google.genai.types.*
import info.skyblond.meshtastic.forwarder.common.isNotBroadcast
import info.skyblond.meshtastic.forwarder.common.isNotEmojiReaction
import info.skyblond.meshtastic.forwarder.common.isTextMessage
import info.skyblond.meshtastic.forwarder.lib.http.MFHttpClient
import info.skyblond.meshtastic.forwarder.utils.sliceMessage
import info.skyblond.meshtastic.forwarder.utils.thought
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
@ConditionalOnProperty("meshtastic.client.features.enable-gemini-on-private-chat")
class GeminiOnPrivateChatComponent(
    meshPacketBus: MeshPacketBus,
    mfHttpClient: MFHttpClient,
    private val gemini: Client
) : AbstractComponent(meshPacketBus, mfHttpClient) {
    private val logger = LoggerFactory.getLogger(GeminiOnPrivateChatComponent::class.java)
    private val sessionMap =
        ConcurrentHashMap<Int, Chat>()

    init {
        logger.info("Gemini on private chat service enabled, will use gemini to respond on private chat")
    }

    override suspend fun filter(packet: MeshPacket): Boolean =
        packet.isTextMessage() && packet.isNotBroadcast() && packet.isNotEmojiReaction()

    private fun createNewParameterBuilder(userNodeNum: Int): Chat {
        val myNodeInfo = runBlocking(Dispatchers.IO) { myNodeInfo() }
        val myUserInfo = runBlocking(Dispatchers.IO) { myUserInfo() }
        val userNodeInfo = runBlocking(Dispatchers.IO) {
            nodes().filter { it.num == userNodeNum }.first()
        }

        val config = GenerateContentConfig.builder()
            .systemInstruction(
                Content.fromParts(
                    Part.fromText(
                        """
                            |# AI Assistance on Meshtastic Private Chat
                            |
                            |## Brief
                            |
                            |Your role: You are a specialized AI assistant, which operating on a Meshtastic device.
                            |Such device uses low power LoRa to communicate with each other, thus the bandwidth and data size is limited,
                            |and the communication is not reliable.
                            |
                            |Your primary directive: be BREVITY. Your entire response SHOULD be under 200 bytes in UTF8 encoding
                            |(about 120 characters on average) to be fit into a single meshtastic text message.
                            |This is an critical technical limitation. A response over the limit will be automatically sliced
                            |into multiple messages. Due to how LoRa works, part of your reply might be lost.
                            |You may exceed the limit if the user knows the potential loss of the message
                            |and still want you to do that. You should let user know about the limitations
                            |and ask how they want to handle this. Once the user confirm to have the message sliced,
                            |you should reply all your content in one go and let the backend do the auto slicing.
                            |
                            |Note: even with auto slicing, there is a max output token limit of 5000,
                            |also, the longer the response, the more likely the user will lose a part of it.
                            |Try your best to keep the response short.
                            |
                            |## Rules
                            |
                            |Behavioral rules:
                            |+ Reply in the same language as the user.
                            |+ Reply in plain text, no markdown, no html, just simple plain text.
                            |+ If the user's request is complicated and you have more than 140 character to say, slice your content and offer user an option to continue.
                            |+ If you have finished your response, then you should not repeat yourself, instead, you should explicitly ask user for further instructions.
                            |+ LoRa devices are not reliable amd message might be lost. If you don't understand what user means, you should ask the user to repeat or clarify the question or instruction.
                            |+ Once you fulfilled user's request, you should tell user if there is no further request, they can end the conversation by sending a `/end` command.
                            |
                            |You must adhere to these rules for all subsequent user queries.
                            |
                            |## Your identity
                            |
                            |Each meshtastic node has a unique node number, along with a user id, long username and a short username.
                            |Right now you're running on this meshtastic node:
                            |
                            |+ Node number: ${myNodeInfo.myNodeNum.toUInt()}
                            |+ User id: ${myUserInfo.id}
                            |+ Long username: ${myUserInfo.longName}
                            |+ Short username: ${myUserInfo.shortName}
                            |+ Device model: ${myUserInfo.hwModel}
                            |
                            |Your user has the following identity:
                            |+ Node number: ${userNodeInfo.num.toUInt()}
                            |+ User id: ${userNodeInfo.user.id}
                            |+ Long username: ${userNodeInfo.user.longName}
                            |+ Short username: ${userNodeInfo.user.shortName}
                            |+ Device model: ${userNodeInfo.user.hwModel}
                            |
                            |Both your identity and user identity are publicly available,
                            |thus it's safe to reference them and provide it to user.
                            |
                            |Please be noticed that user info might be changed at anytime, but these info
                            |will not automatically updated. If the info is outdated and latest info is required
                            |to fulfill user's request, you should guide user to end current conversation
                            |and start a new one. This will refresh the user info.
                            |
                        """.trimMargin()
                    )
                )
            )
            .tools(
                // Google search, first 1500 requests per day is free
                Tool.builder()
                    .googleSearch(GoogleSearch.builder().build())
                    .build(),
                // you can only use one tool at a time, either google search or tool calling.
            )
            .thinkingConfig(
                ThinkingConfig.builder()
                    // dynamicThinking
                    .thinkingBudget(-1)
                    .includeThoughts(true)
                    .build()
            )
            .safetySettings(
                listOf(
                    SafetySetting.builder()
                        .category(HarmCategory.Known.HARM_CATEGORY_SEXUALLY_EXPLICIT)
                        .threshold(HarmBlockThreshold.Known.OFF).build()
                )
            )
            .temperature(0.8f)
            .topP(0.95f)
            .topK(0f)
            .maxOutputTokens(5000)
            .build()

        return gemini.chats.create("gemini-2.5-pro", config)
    }

    private fun handleCommand(textMessage: String, fromNodeNum: Int): Boolean {
        when (textMessage.lowercase().trim()) {
            "/end" -> {
                logger.info(
                    "Gemini private chat conversation ended for node #{}",
                    fromNodeNum.toUInt()
                )
                sessionMap.remove(fromNodeNum)
                return true
            }
        }
        return false
    }

    override suspend fun consume(packet: MeshPacket) {
        val textMessage = packet.decoded.payload.toStringUtf8()
        val fromNodeNum = packet.from
        logger.info(
            "Received message for gemini private chat: from={}, messageId={}, content={}",
            fromNodeNum.toUInt(), packet.id.toUInt(), textMessage
        )

        if (handleCommand(textMessage, fromNodeNum)) {
            return
        }

        val session = sessionMap.getOrPut(fromNodeNum) { createNewParameterBuilder(fromNodeNum) }
        val response = runCatching {
            session.sendMessage(textMessage)
                .also { it.checkFinishReason() }
                .also { resp ->
                    resp.thought()?.let {
                        logger.info(
                            "Gemini private chat thought for message #{}:\n{}",
                            packet.id.toUInt(), it
                        )
                    }
                }
                .text() ?: "(Gemini reply is empty)"
        }.getOrElse {
            logger.error("Failed to get reply from gemini", it)
            "(Failed to call gemini, your latest input will be ignored. Please retry later.)"
        }
        val replyText = response.trim()

        logger.info(
            "Gemini reply for node #{} message #{}: length={}, size={}B, content=\n{}",
            fromNodeNum.toUInt(), packet.id.toUInt(),
            replyText.length, replyText.toByteArray().size,
            replyText
        )

        val messages = sliceMessage(replyText).toList()
        for (i in messages.indices) {
            val m = messages[i]
            val r = replyTextMessage(packet, m)
            if (r.isSuccess) {
                logger.info(
                    "Gemini private chat response delivered to node #{} message #{} ({}/{})",
                    fromNodeNum.toUInt(), packet.id.toUInt(), i + 1, messages.size,
                )
            } else {
                logger.error(
                    "Failed to reply gemini private chat response for node #{} message #{} ({}/{})",
                    fromNodeNum.toUInt(), packet.id.toUInt(), i + 1, messages.size,
                )
                break
            }
        }
    }

    override fun onCancel() {
        sessionMap.clear()
    }
}
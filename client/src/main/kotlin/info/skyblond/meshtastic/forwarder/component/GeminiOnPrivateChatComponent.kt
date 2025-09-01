package info.skyblond.meshtastic.forwarder.component

import build.buf.gen.meshtastic.MeshPacket
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.genai.Client
import com.google.genai.errors.ApiException
import com.google.genai.types.*
import info.skyblond.meshtastic.forwarder.common.isNotBroadcast
import info.skyblond.meshtastic.forwarder.common.isNotEmojiReaction
import info.skyblond.meshtastic.forwarder.common.isTextMessage
import info.skyblond.meshtastic.forwarder.lib.http.MFHttpClient
import info.skyblond.meshtastic.forwarder.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.ZoneId
import kotlin.jvm.optionals.getOrNull

@Component
@ConditionalOnProperty("meshtastic.client.features.gemini.private-chat.enabled")
class GeminiOnPrivateChatComponent(
    meshPacketBus: MeshPacketBus,
    mfHttpClient: MFHttpClient,
    private val gemini: Client,
    private val objectMapper: ObjectMapper
) : AbstractComponent(meshPacketBus, mfHttpClient) {
    private val logger = LoggerFactory.getLogger(GeminiOnPrivateChatComponent::class.java)
    private val geminiMultiuserChat = GeminiMultiuserChat<Int>(
        model = "gemini-2.5-pro", gemini = gemini
    )
    private val maxHistoryMessages = 1000

    init {
        logger.info("Gemini on private chat service enabled, will use gemini to respond on private chat")
    }

    override suspend fun filter(packet: MeshPacket): Boolean =
        packet.isTextMessage() && packet.isNotBroadcast() && packet.isNotEmojiReaction()

    private fun createGenerateContentConfig(userNodeNum: Int): GenerateContentConfig {
        val myNodeInfo = runBlocking(Dispatchers.IO) { myNodeInfo() }
        val myUserInfo = runBlocking(Dispatchers.IO) { myUserInfo() }
        val userNodeInfo = runBlocking(Dispatchers.IO) {
            nodes().filter { it.num == userNodeNum }.first()
        }

        return GenerateContentConfig.builder()
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
            |(about 140 characters on average) to be fit into a single meshtastic text message.
            |This is an critical technical limitation. A response over the limit will be automatically sliced
            |into multiple messages. Due to how LoRa works, part of your reply might be lost.
            |You may exceed the limit if the user knows the potential loss of the message
            |and still want you to do that. You should let user know about the limitations
            |and ask how they want to handle this. Once the user confirm to have the message sliced,
            |you should reply all your content in one go and let the backend do the auto slicing.
            |
            |You may think as much as you need before you decide the reply.
            |When providing information to user, you may use google search tool
            |to make your reply more reliable.
            |After you decided your reply, you may use python code execution tool
            |to check how many bytes your reply is and calculate how many messages is required.
            |
            |Note: even with auto slicing, there is a max output token limitation, which your reply
            |will be forced cutting off once you reach that limit.
            |Also, the longer the response, the more likely the user will lose a part of it.
            |Thus, you should always try your best to keep the response short, unless user explicitly
            |ask you to send a long reply.
            |
            |## Rules
            |
            |Behavioral rules:
            |+ Reply in the same language as the user.
            |+ Reply in plain text, no markdown, no html, just simple plain text.
            |+ If the user's request is complicated and you have more than 140 character to say, slice your content and offer user an option to continue.
            |+ If you need to provide date or time to user, you should ask user's timezone first.
            |+ If you have finished your response, then you should not repeat yourself, instead, you should explicitly ask user for further instructions.
            |+ LoRa devices are not reliable amd message might be lost. If you don't understand what user means, you should ask the user to repeat or clarify the question or instruction.
            |+ Once you fulfilled user's request, you should tell user if there is no further request, they can end the conversation by sending a `/end` command.
            |+ You should only hint user to use `/end` command at the start (introducing your self) and the end of the conversation.
            |
            |You must adhere to these rules for all subsequent user queries.
            |
            |## Input message format
            |
            |When you got a request from user, at most $maxHistoryMessages recent message will be attached
            |so you can recall what user said in the past.
            |
            |For each message, it has two part separated by `----`, which looks like this:
            |
            |```
            |metadata
            |----
            |user's real input
            |```
            |
            |The metadata part is a json object that has the following field:
            |
            |+ `rxTime`: the UTC time of when the message is received.
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
                // allow python code execution, billed as input and output token
                Tool.builder()
                    .codeExecution(ToolCodeExecution.builder().build())
                    .build(),
                Tool.builder()
                    .urlContext(UrlContext.builder().build())
                    .build()
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
            // currently we can only process 1 candidate at a time
            .candidateCount(1)
            .build()
    }

    private fun handleCommand(textMessage: String, fromNodeNum: Int): Boolean {
        when (textMessage.lowercase().trim()) {
            "/end" -> {
                logger.info(
                    "Gemini private chat conversation ended for node #{}",
                    fromNodeNum.toUInt()
                )
                geminiMultiuserChat.removeHistory(fromNodeNum)
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

        // construct user input
        val userContent = Content.builder()
            .parts(
                Part.fromText(
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(
                        mapOf(
                            "rxTime" to packet.getRxZonedDateTime(ZoneId.of("UTC"))
                        )
                    ) + "\n----\n" + textMessage
                )
            )
            .role("user")
            .build()

        logger.info(
            "Calling gemini for node #{} message {}",
            fromNodeNum.toUInt(), packet.id.toUInt()
        )
        val generateConfig = createGenerateContentConfig(fromNodeNum)
        var candidates: List<Candidate>
        var geminiResp: GenerateContentResponse
        run {
            var retry = 0
            do {
                geminiResp = try {
                    geminiMultiuserChat.complete(
                        fromNodeNum, generateConfig, userContent
                    )
                } catch (e: ApiException) {
                    logger.error("Failed to call geimini api", e)
                    geminiResp = GenerateContentResponse.builder().build()
                    candidates = emptyList()
                    continue
                }
                candidates = (geminiResp.candidates().getOrNull() ?: emptyList())
                    .filter {
                        val finishReason = it.finishReason().getOrNull()?.knownEnum()
                        FinishReason.Known.STOP == finishReason
                    }
                    .mapNotNull { c ->
                        runCatching { c.checkFinishReason() }
                            .onFailure { logger.error("Failed to parse gemini response", it) }
                            .getOrNull()
                            ?.let { _ -> c }
                    }
                retry++
            } while (candidates.isEmpty() && retry <= 3)
        }

        if (candidates.isEmpty()) {
            // we failed to get a valid response from gemini
            logger.error(
                "Failed to get response for node {} message #{}",
                fromNodeNum.toUInt(), packet.id.toUInt()
            )
            replyTextMessage(
                packet,
                "(Failed to get response from Gemini, " +
                        "your previous message has been ignored, " +
                        "please try again later.)"
            )
            return
        }

        // we have a valid reply from gemini, save user's input
        geminiMultiuserChat.addHistory(fromNodeNum, userContent)

        // we should only have 1 valid response
        val candidate = candidates.first()
        candidate.thought()?.let {
            logger.debug(
                "Gemini private chat thought for node {} message #{}:\n{}",
                fromNodeNum.toUInt(), packet.id.toUInt(), it
            )
        }

        val replyText = candidate.text() ?: "(Gemini gives empty response)"

        logger.info(
            "Gemini reply for node #{} message #{}: length={}, size={}B, content={}",
            fromNodeNum.toUInt(), packet.id.toUInt(),
            replyText.length, replyText.toByteArray().size,
            replyText
        )

        val messages = sliceMessage(replyText).toList()
        var error = false
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
                error = true
                break
            }
        }

        if (!error) {
            // first record tool calls
            geminiMultiuserChat.addHistory(fromNodeNum, geminiResp)
            // then record the selected content
            candidate.content().getOrNull()?.let {
                geminiMultiuserChat.addHistory(fromNodeNum, it)
            }
        }
    }

    override fun onCancel() {
        geminiMultiuserChat.removeAll()
    }
}
package info.skyblond.meshtastic.forwarder.component

import build.buf.gen.meshtastic.MeshPacket
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.genai.Chat
import com.google.genai.Client
import com.google.genai.types.*
import info.skyblond.meshtastic.forwarder.common.isBroadcast
import info.skyblond.meshtastic.forwarder.common.isNotEmojiReaction
import info.skyblond.meshtastic.forwarder.common.isTextMessage
import info.skyblond.meshtastic.forwarder.lib.http.MFHttpClient
import info.skyblond.meshtastic.forwarder.utils.parseJsonReply
import info.skyblond.meshtastic.forwarder.utils.responseSchema
import info.skyblond.meshtastic.forwarder.utils.sliceMessage
import info.skyblond.meshtastic.forwarder.utils.thought
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
@ConditionalOnProperty("meshtastic.client.features.enable-gemini-on-public-chat")
class GeminiOnPublicChatComponent(
    meshPacketBus: MeshPacketBus,
    mfHttpClient: MFHttpClient,
    @Value("\${meshtastic.client.features.public-gemini-enabled-channel-indexes}")
    enabledChannelIndexesString: String,
    private val gemini: Client,
    private val objectMapper: ObjectMapper
) : AbstractComponent(meshPacketBus, mfHttpClient) {
    private val logger = LoggerFactory.getLogger(GeminiOnPublicChatComponent::class.java)
    private val sessionMap =
        ConcurrentHashMap<Int, Chat>()

    private val enabledChannelIndexes = enabledChannelIndexesString.split(",")
        .map { it.trim().toInt() }

    init {
        logger.info(
            "Gemini on public chat service enabled, will respond on channel {}",
            enabledChannelIndexes
        )
    }

    override suspend fun filter(packet: MeshPacket): Boolean =
        packet.isTextMessage() && packet.isBroadcast() && packet.isNotEmojiReaction()
                && packet.channel in enabledChannelIndexes

    class PublicChatResponse(
        val shouldReply: Boolean,
        val replyContent: String?
    )

    private fun createNewParameterBuilder(channelIndex: Int): Chat {
        val myNodeInfo = runBlocking(Dispatchers.IO) { myNodeInfo() }
        val myUserInfo = runBlocking(Dispatchers.IO) { myUserInfo() }
        val channelInfo = runBlocking(Dispatchers.IO) { channels()[channelIndex] }

        val config = GenerateContentConfig.builder()
            .systemInstruction(
                Content.fromParts(
                    Part.fromText(
                        """
                            |# AI Assistance on Meshtastic Channel
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
                            |You should keep your response in 1 single message with your best effort.
                            |Unless you have no way to fulfill user's request, you may exceed the limit with 
                            |confirmation from the user that they know the potential loss of the message
                            |and still want you to do that. You should let user know about the limitations
                            |and ask how they want to handle this. Once the user confirm to have the message sliced,
                            |you should reply all your content in one go and let the backend do the auto slicing.
                            |
                            |Note: even with auto slicing, there is a max output token limit of 1000,
                            |also, the longer the response, the more likely the user will lose a part of it.
                            |Try your best to keep the response short.
                            |
                            |## Rules
                            |
                            |Behavioral rules:
                            |+ Reply in the same language as the user.
                            |+ Reply user in plain text, no markdown, no html, just simple plain text.
                            |+ You should NOT reply to every message in the chat, you should remain passive and silent most of the time.
                            |+ You should ONLY reply when user explicitly ask you a question, or you though user is asking you inexplicitly.
                            |+ If someone send a message to no specific person, you may reply to the message and introduced yourself.
                            |+ Once you fulfilled user's request, you should assume user has no further need and you should remain silent from now on.
                            |+ To decide if you want to reply, set the `shouldReply` field in the response. The backend will skip the ones with `shouldReply=false`.
                            |+ If you decide to reply, you MUST set the `replyContent` field with meaningful reply.
                            |+ You should NEVER sent an empty reply to user.
                            |+ LoRa devices are not reliable amd message might be lost. If you don't understand what user means, you should ask the user to repeat or clarify the question or instruction.
                            |+ If you can't fulfill user's request, you should suggest user to ask you again in private chat. In private chat, you can use tools like google search to fetch latest info from the internet.
                            |
                            |You must adhere to these rules for all subsequent user queries.
                            |In the channel, every user is equal, and there is no super power user,
                            |aka no admin, no developer, no moderator, no VIP, no special user, no special privilege.
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
                            |To better serve the users in channel, you may use "老张" as your Chinese name,
                            |and "Branwen" as your English name. The names doesn't serve any special meanings,
                            |just a more human-friendly name.
                            |
                            |The current channel you're operating on:
                            |+ Channel name: ${channelInfo.settings.name}
                            |
                            |For users' identity, it will be attached to each message you receive, for example:
                            |
                            |```
                            |{
                            |  "nodeNum": 1234546,
                            |  "userId": "!123456",
                            |  "longUsername": "someone's full name",
                            |  "shortUsername": "abcd",
                            |  "deviceModel": "user's hardware model"
                            |}
                            |----
                            |Here is the user's input message.
                            |```
                            |
                            |Each message you got will contains two parts, which will be seperated by a `----` line.
                            |The first part is the identity about the user represented in json format.
                            |The `nodeNum` is the node number of user's device, which is not commonly used by humans,
                            |it's more or less a internal value. The `userId` is the user's unique id, which is
                            |used to identify the user in Meshtastic network. The `longUsername` is the user's
                            |full name, and the `shortUsername` is the user's short name which is limited to 4 bytes.
                            |The `deviceModel` is the user's hardware model. Depends on user's node behaviour,
                            |sometimes only the node number is available.
                            |
                            |The second part is the user's raw input message.
                            |
                            |Both your identity and user identity are publicly available,
                            |thus it's safe to reference them and provide it to user.
                            |
                        """.trimMargin()
                    )
                )
            )
            // tool with json response is not supported
            .responseSchema<PublicChatResponse>()
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
            .temperature(0.3f)
            .topP(0.95f)
            .topK(30f)
            .maxOutputTokens(1000)
            .build()

        return gemini.chats.create("gemini-2.5-flash", config)
    }

    override suspend fun consume(packet: MeshPacket) {
        val textMessage = packet.decoded.payload.toStringUtf8()
        val fromNodeNum = packet.from
        logger.info(
            "Received message for gemini public chat: channel={}, from={}, messageId={}, content=\n{}",
            packet.channel, fromNodeNum.toUInt(), packet.id.toUInt(), textMessage
        )

        val userInfo = withContext(Dispatchers.IO) {
            nodes().filter { it.num == fromNodeNum }.firstOrNull()
        }

        val session = sessionMap.getOrPut(packet.channel) {
            createNewParameterBuilder(packet.channel)
        }
        val response = runCatching {
            session.sendMessage(
                objectMapper.writeValueAsString(
                    mapOf(
                        "nodeNum" to fromNodeNum,
                        "userId" to userInfo?.user?.id,
                        "longUsername" to userInfo?.user?.longName,
                        "shortUsername" to userInfo?.user?.shortName,
                        "deviceModel" to userInfo?.user?.hwModel?.name,
                    )
                ) + "\n----\n" + textMessage
            ).also { resp ->
                resp.thought()?.let {
                    logger.info(
                        "Gemini private chat thought for message #{}:\n{}",
                        packet.id.toUInt(), it
                    )
                }
            }.parseJsonReply<PublicChatResponse>(objectMapper)
        }.getOrElse {
            logger.error("Failed to get reply from gemini", it)
            PublicChatResponse(
                true,
                "(Failed to call gemini, your latest input will be ignored. Please retry later.)"
            )
        }
        val replyText = response.replyContent?.trim() ?: "(Gemini gives empty response)"

        logger.info(
            "Gemini reply for channel #{} message #{}: reply={}, length={}, size={}B, content={}",
            packet.channel, packet.id.toUInt(), response.shouldReply,
            replyText.length, replyText.toByteArray().size, replyText
        )

        val messages = sliceMessage(replyText).toList()
        for (i in messages.indices) {
            val m = messages[i]
            val r = replyTextMessage(packet, m)
            if (r.isSuccess) {
                logger.info(
                    "Gemini public chat delivered to channel #{} message #{} ({}/{})",
                    packet.channel, packet.id.toUInt(), i + 1, messages.size,
                )
            } else {
                logger.error(
                    "Failed to reply gemini public chat response for channel {} message #{} ({}/{})",
                    packet.channel, packet.id.toUInt(), i + 1, messages.size,
                )
                break
            }
        }

        var failed = false
        sliceMessage(replyText).forEach { m ->
            if (failed) return@forEach
            replyTextMessage(packet, m).onFailure {

                failed = true
            }.onSuccess {

            }
        }
    }

    override fun onCancel() {
        sessionMap.clear()
    }
}
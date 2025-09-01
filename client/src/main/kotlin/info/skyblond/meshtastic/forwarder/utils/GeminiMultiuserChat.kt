package info.skyblond.meshtastic.forwarder.utils

import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.GenerateContentResponse
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap

/**
 * Gemini sdk's chat is so bad that I have to wrote my own wrapper for it.
 * */
class GeminiMultiuserChat<UserKeyType : Any>(
    private val model: String,
    private val gemini: Client
) {

    private val historyMap = ConcurrentHashMap<UserKeyType, List<Content>>()

    fun getHistory(userKey: UserKeyType): List<Content> {
        return historyMap[userKey] ?: emptyList()
    }

    fun addHistory(userKey: UserKeyType, content: Content) {
        historyMap.compute(userKey) { _, v ->
            (v ?: emptyList()) + content
        }
    }

    /**
     * Given the user's input, get chat completion response.
     * The user's input will not be added to the history automatically.
     * The caller should check the response and call [addHistory] with
     * the selected content.
     * */
    fun complete(
        userKey: UserKeyType,
        config: GenerateContentConfig,
        userInput: Content,
        maxHistory: Int = 1000,
    ): GenerateContentResponse {
        val chat = gemini.chats.create(model, config)
        val contents = getHistory(userKey).takeLast(maxHistory) + userInput
        return chat.sendMessage(contents)
    }

    /**
     * Add auto function call history to the history.
     * Should be called before adding model's content to history.
     * */
    fun addHistory(
        userKey: UserKeyType,
        response: GenerateContentResponse
    ) {
        val newHistory = mutableListOf<Content>()
        if (response.automaticFunctionCallingHistory().isPresent
            && response.automaticFunctionCallingHistory().get().isNotEmpty()
        ) {
            // the afc history contains the entire curated history in addition to the new user input.
            // truncate the afc history to deduplicate the existing history.
            newHistory.addAll(
                response
                    .automaticFunctionCallingHistory()
                    .get()
                    .subList(
                        getHistory(userKey).size,
                        response.automaticFunctionCallingHistory().get().size
                    )
            )
        }
        historyMap.compute(userKey) { _, v ->
            (v ?: emptyList()) + newHistory
        }
    }

    fun removeHistory(userKey: UserKeyType) {
        historyMap.remove(userKey)
    }

    fun removeAll() {
        historyMap.clear()
    }
}
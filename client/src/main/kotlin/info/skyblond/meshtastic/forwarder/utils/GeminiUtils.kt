package info.skyblond.meshtastic.forwarder.utils

import com.google.genai.types.Candidate
import com.google.genai.types.FinishReason
import com.google.genai.types.Part
import kotlin.jvm.optionals.getOrNull

fun Candidate.checkFinishReason() {
    val finishReason = this.finishReason().getOrNull()?.knownEnum()
    require(FinishReason.Known.STOP == finishReason) {
        "Finish reason must be known stop, otherwise the output is broken and unusable. Current: $finishReason"
    }
}

fun List<Part>.thought(): String = this
    .filter { it.thought().orElse(false) }
    .mapNotNull { it.text().orElse(null) }
    .joinToString("\n")
    .trim()

fun Candidate.thought(): String? = this.content().getOrNull()?.parts()?.getOrNull()
    ?.thought()

fun Candidate.text(): String? = this.content().getOrNull()?.text()


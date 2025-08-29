package info.skyblond.meshtastic.forwarder.utils

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.victools.jsonschema.generator.OptionPreset
import com.github.victools.jsonschema.generator.SchemaGenerator
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder
import com.github.victools.jsonschema.generator.SchemaVersion
import com.github.victools.jsonschema.module.jackson.JacksonModule
import com.github.victools.jsonschema.module.jackson.JacksonOption
import com.google.genai.types.*
import kotlin.jvm.optionals.getOrNull

val jsonSchemaGenerator = SchemaGenerator(
    SchemaGeneratorConfigBuilder(
        SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON
    ).with(
        JacksonModule(
            JacksonOption.RESPECT_JSONPROPERTY_REQUIRED
        )
    ).build()
)

inline fun <reified T> GenerateContentConfig.Builder.responseSchema(): GenerateContentConfig.Builder =
    this.responseMimeType("application/json")
        .responseJsonSchema(jsonSchemaGenerator.generateSchema(T::class.java))


fun <T> Candidate.parseJsonReply(objectMapper: ObjectMapper, type: TypeReference<T>): T {
    require(FinishReason.Known.STOP == this.finishReason().getOrNull()?.knownEnum()) {
        "Finish reason must be known stop, otherwise the output is broken and unusable"
    }
    val textReply = this.content().getOrNull()?.text()
        ?: throw IllegalArgumentException("Candidate has no text parts in the content")
    val parsedResult = try {
        objectMapper.readValue(textReply, type)
    } catch (t: Throwable) {
        throw IllegalArgumentException("Invalid json reply", t)
    }
    return parsedResult ?: throw IllegalArgumentException("Parsed result is null")
}

fun <T> Candidate.parseJsonReply(objectMapper: ObjectMapper): T =
    this.parseJsonReply(objectMapper, object : TypeReference<T>() {})

fun List<Part>.thought(): String = this
    .filter { it.thought().orElse(false) }
    .mapNotNull { it.text().orElse(null) }
    .joinToString("\n")
    .trim()

// TODO: deprecate this?
fun GenerateContentResponse.thought(): String? = this.parts()?.thought()

fun Candidate.thought(): String? = this.content().getOrNull()?.parts()?.getOrNull()
    ?.filter { it.thought().orElse(false) }
    ?.mapNotNull { it.text().orElse(null) }
    ?.joinToString("\n")
    ?.trim()


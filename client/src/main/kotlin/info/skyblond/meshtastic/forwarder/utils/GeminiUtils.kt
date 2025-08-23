package info.skyblond.meshtastic.forwarder.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.victools.jsonschema.generator.OptionPreset
import com.github.victools.jsonschema.generator.SchemaGenerator
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder
import com.github.victools.jsonschema.generator.SchemaVersion
import com.github.victools.jsonschema.module.jackson.JacksonModule
import com.github.victools.jsonschema.module.jackson.JacksonOption
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.GenerateContentResponse

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

inline fun <reified T> GenerateContentResponse.parseJsonReply(objectMapper: ObjectMapper): T {
    this.checkFinishReason()
    return objectMapper.readValue(this.text(), T::class.java)
}

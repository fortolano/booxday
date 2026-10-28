package com.boox.day.api

import com.google.gson.annotations.SerializedName

/**
 * Helper to create a simple text message (for system messages)
 */
fun createTextMessage(role: String, content: String): Map<String, Any> {
    return mapOf(
        "role" to role,
        "content" to content
    )
}

/**
 * Helper to create a multimodal message with text and optional image.
 * Uses Map format for proper OpenRouter API serialization.
 */
fun createVisionMessage(role: String, textContent: String, imageBase64: String? = null): Map<String, Any> {
    val contentList = mutableListOf<Map<String, Any>>()

    // Add text content
    contentList.add(mapOf(
        "type" to "text",
        "text" to textContent
    ))

    // Add image if provided (using JPEG for smaller size)
    if (imageBase64 != null) {
        contentList.add(mapOf(
            "type" to "image_url",
            "image_url" to mapOf("url" to "data:image/jpeg;base64,$imageBase64")
        ))
    }

    return mapOf(
        "role" to role,
        "content" to contentList
    )
}

/**
 * Helper to create an audio message for models supporting audio input (e.g., Gemini 2.0 Flash).
 * Uses OpenRouter's multimodal content format for audio.
 *
 * @param role Message role ("user" typically)
 * @param textContent Text prompt to accompany the audio
 * @param audioBase64 Base64-encoded audio data
 * @param mimeType Audio MIME type (e.g., "audio/wav", "audio/mp3", "audio/webm")
 */
fun createAudioMessage(
    role: String,
    textContent: String,
    audioBase64: String,
    mimeType: String = "audio/wav"
): Map<String, Any> {
    val contentList = mutableListOf<Map<String, Any>>()

    // Add text content first (prompt/instructions)
    contentList.add(mapOf(
        "type" to "text",
        "text" to textContent
    ))

    // Add audio content
    // OpenRouter/Gemini uses input_audio format
    contentList.add(mapOf(
        "type" to "input_audio",
        "input_audio" to mapOf(
            "data" to audioBase64,
            "format" to mimeType.substringAfter("/")  // "audio/wav" -> "wav"
        )
    ))

    return mapOf(
        "role" to role,
        "content" to contentList
    )
}

/**
 * Raw request that can handle multimodal content properly
 */
data class RawVisionChatRequest(
    @SerializedName("model")
    val model: String,

    @SerializedName("messages")
    val messages: List<Map<String, Any>>,

    @SerializedName("temperature")
    val temperature: Float? = null,

    @SerializedName("max_tokens")
    val maxTokens: Int? = null,

    /**
     * Reasoning config at ROOT level (NOT inside extra_body!)
     * OpenRouter expects: "reasoning": {"enabled": true} at root
     */
    @SerializedName("reasoning")
    val reasoning: ReasoningConfig? = null,

    @SerializedName("response_format")
    val responseFormat: ResponseFormat? = null,

    /**
     * Provider routing configuration.
     * When set with "only", restricts which providers can fulfill the request.
     * Critical for BYOK users to prevent fallback charges.
     */
    @SerializedName("provider")
    val provider: ProviderConfig? = null
)

/**
 * Response format for structured JSON output.
 * Used when requesting JSON-formatted responses from compatible models.
 *
 * Two modes supported:
 * 1. Basic JSON mode: type = "json_object" (model returns valid JSON)
 * 2. JSON Schema mode: type = "json_schema" with jsonSchema defined (model follows exact schema)
 */
data class ResponseFormat(
    @SerializedName("type")
    val type: String = "json_object",

    @SerializedName("json_schema")
    val jsonSchema: JsonSchemaWrapper? = null
) {
    companion object {
        /** Create basic JSON mode response format */
        fun jsonObject() = ResponseFormat(type = "json_object")

        /** Create JSON schema response format with strict validation */
        fun jsonSchema(name: String, schema: Map<String, Any>, strict: Boolean = true) = ResponseFormat(
            type = "json_schema",
            jsonSchema = JsonSchemaWrapper(
                name = name,
                strict = strict,
                schema = schema
            )
        )
    }
}

/**
 * Wrapper for JSON schema definition.
 * OpenRouter expects: { "name": "...", "strict": true, "schema": {...} }
 */
data class JsonSchemaWrapper(
    @SerializedName("name")
    val name: String,

    @SerializedName("strict")
    val strict: Boolean = true,

    @SerializedName("schema")
    val schema: Map<String, Any>
)

/**
 * Extra body for OpenRouter-specific features like reasoning
 */
data class ExtraBody(
    @SerializedName("reasoning")
    val reasoning: ReasoningConfig? = null
)

/**
 * Reasoning configuration for models that support extended thinking.
 *
 * OpenRouter unified reasoning API supports:
 * - effort: "high" | "medium" | "low" | "minimal" (budget_tokens calculated from max_tokens)
 * - enabled: Boolean (for DeepSeek-style models)
 */
data class ReasoningConfig(
    @SerializedName("effort")
    val effort: String? = null,

    @SerializedName("enabled")
    val enabled: Boolean? = null
) {
    companion object {
        fun withEffort(effort: String) = ReasoningConfig(effort = effort, enabled = null)
        fun enabled() = ReasoningConfig(effort = null, enabled = true)
        /** No thinking, for models where it is optional (OpenRouter `reasoning.enabled = false`) */
        fun disabled() = ReasoningConfig(effort = null, enabled = false)
    }
}

/**
 * Provider configuration for routing control (OpenRouter `provider` object).
 *
 * [only] takes OpenRouter provider slugs (e.g. "google-vertex", "anthropic"), not a model's
 * author prefix.
 */
data class ProviderConfig(
    @SerializedName("only")
    val only: List<String>? = null,
    /** true: only providers with a zero data retention policy (search indexing of page images) */
    @SerializedName("zdr")
    val zdr: Boolean? = null,
    /** true: only providers that take every setting of the request, the answer format included */
    @SerializedName("require_parameters")
    val requireParameters: Boolean? = null
)

// Response models
data class ChatResponse(
    @SerializedName("id")
    val id: String?,

    @SerializedName("model")
    val model: String?,

    @SerializedName("choices")
    val choices: List<Choice>?,

    @SerializedName("usage")
    val usage: Usage?,

    @SerializedName("error")
    val error: ApiError?
)

data class ApiError(
    @SerializedName("message")
    val message: String?,

    @SerializedName("code")
    val code: Int?,

    /** Extra detail, e.g. a 402's `limit_source` and `reason` (OpenRouter docs: limits) */
    @SerializedName("metadata")
    val metadata: Map<String, Any?>? = null
)

data class Choice(
    @SerializedName("message")
    val message: ResponseMessage?,

    @SerializedName("finish_reason")
    val finishReason: String?
)

data class ResponseMessage(
    @SerializedName("role")
    val role: String?,

    @SerializedName("content")
    val content: String?,

    /**
     * Reasoning output from models like GPT-OSS that use reasoning-first architecture.
     * These models put their actual output in the reasoning field, not content.
     */
    @SerializedName("reasoning")
    val reasoning: String?
)

data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int?,

    @SerializedName("completion_tokens")
    val completionTokens: Int?,

    @SerializedName("total_tokens")
    val totalTokens: Int?
)

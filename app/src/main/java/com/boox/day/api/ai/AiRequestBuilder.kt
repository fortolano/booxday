package com.boox.day.api.ai

/**
 * Fluent builder for constructing [AiRequest] objects.
 *
 * Provides compile-time validation of model capabilities:
 * - [userMessageWithImage] requires VISION capability
 * - [jsonOutput] requires JSON_OUTPUT capability
 * - [enableReasoning] requires REASONING capability
 *
 * Usage:
 * ```kotlin
 * val request = AiRequestBuilder()
 *     .model(AiModel.NemotronNano)
 *     .systemPrompt("You are a helpful assistant.")
 *     .userMessageWithImage("Describe this image:", base64Image)
 *     .enableReasoning()
 *     .build()
 * ```
 *
 * Or using the DSL:
 * ```kotlin
 * val request = aiRequest {
 *     model(AiModel.NemotronNano)
 *     userMessageWithImage("Extract text:", base64Image)
 *     enableReasoning()
 * }
 * ```
 */
class AiRequestBuilder {
    private var model: AiModel = AiModel.GptOss20B  // Default to fast text model
    private val messages = mutableListOf<AiMessage>()
    private var config: AiConfig? = null
    private var jsonOutput = false
    private var jsonSchema: JsonSchema? = null
    private var enableReasoning = false
    private var zeroDataRetention = false

    /**
     * Set the AI model to use.
     * Defaults to [AiModel.GptOss20B] if not specified.
     */
    fun model(model: AiModel) = apply {
        this.model = model
    }

    /**
     * Add a system prompt message.
     * Usually sets the behavior/context for the AI.
     */
    fun systemPrompt(content: String) = apply {
        messages.add(AiMessage.System(content))
    }

    /**
     * Add a text-only user message.
     */
    fun userMessage(content: String) = apply {
        messages.add(AiMessage.User(content))
    }

    /**
     * Add a user message with an image.
     * @throws IllegalArgumentException if model doesn't have VISION capability
     */
    fun userMessageWithImage(text: String, imageBase64: String) = apply {
        require(model.hasCapability(AiCapability.VISION)) {
            "Model ${model.displayName} does not support vision. " +
                "Use a vision-capable model like GrokVision or NovaLite."
        }
        messages.add(AiMessage.UserWithImage(text, imageBase64))
    }

    /**
     * Add a user message with audio.
     * @param text The text prompt describing what to do with the audio
     * @param audioBase64 Base64-encoded audio data
     * @param mimeType Audio MIME type (default: "audio/wav")
     * @throws IllegalArgumentException if model doesn't have AUDIO capability
     */
    fun userMessageWithAudio(text: String, audioBase64: String, mimeType: String = "audio/wav") = apply {
        require(model.hasCapability(AiCapability.AUDIO)) {
            "Model ${model.displayName} does not support audio input. " +
                "Use an audio-capable model like Gemini2Flash."
        }
        messages.add(AiMessage.UserWithAudio(text, audioBase64, mimeType))
    }

    /**
     * Add an assistant message (for few-shot examples or conversation history).
     */
    fun assistantMessage(content: String) = apply {
        messages.add(AiMessage.Assistant(content))
    }

    /**
     * Request JSON-formatted response (basic JSON mode).
     * @throws IllegalArgumentException if model doesn't have JSON_OUTPUT capability
     */
    fun jsonOutput() = apply {
        require(model.hasCapability(AiCapability.JSON_OUTPUT)) {
            "Model ${model.displayName} does not support JSON output. " +
                "Use a model like GptOss that supports structured output."
        }
        this.jsonOutput = true
    }

    /**
     * Request structured JSON output with a specific schema.
     * The model will strictly follow the provided schema.
     * @param name Schema name (e.g., "calendar_event")
     * @param schema The JSON schema definition as a Map
     * @param strict Whether to enforce strict schema validation (default: true)
     * @throws IllegalArgumentException if model doesn't have JSON_OUTPUT capability
     */
    fun jsonSchema(name: String, schema: Map<String, Any>, strict: Boolean = true) = apply {
        require(model.hasCapability(AiCapability.JSON_OUTPUT)) {
            "Model ${model.displayName} does not support JSON schema output. " +
                "Use a model like GptOss that supports structured output."
        }
        this.jsonSchema = JsonSchema(name, schema, strict)
    }

    /**
     * Enable extended thinking/reasoning.
     * @throws IllegalArgumentException if model doesn't have REASONING capability
     */
    fun enableReasoning() = apply {
        require(model.hasCapability(AiCapability.REASONING)) {
            "Model ${model.displayName} does not support reasoning. " +
                "Use a model like GrokVision that supports extended thinking."
        }
        this.enableReasoning = true
    }

    /**
     * Route only to providers with zero data retention (OpenRouter `provider.zdr`).
     */
    fun zeroDataRetention() = apply {
        this.zeroDataRetention = true
    }

    /**
     * Override the entire configuration.
     */
    fun config(config: AiConfig) = apply {
        this.config = config
    }

    /**
     * Set the temperature parameter.
     */
    fun temperature(temp: Float) = apply {
        this.config = (config ?: model.defaultConfig).copy(temperature = temp)
    }

    /**
     * Set the maximum tokens for the response.
     */
    fun maxTokens(tokens: Int) = apply {
        this.config = (config ?: model.defaultConfig).copy(maxTokens = tokens)
    }

    /**
     * Build the final [AiRequest].
     * @throws IllegalArgumentException if no messages were added
     */
    fun build(): AiRequest {
        require(messages.isNotEmpty()) {
            "At least one message is required. Use userMessage() or systemPrompt()."
        }
        return AiRequest(
            model = model,
            messages = messages.toList(),
            config = withUserOutputLimit(config ?: model.defaultConfig),
            jsonOutput = jsonOutput,
            jsonSchema = jsonSchema,
            enableReasoning = enableReasoning,
            zeroDataRetention = zeroDataRetention
        )
    }

    /**
     * A model the user picked (Oct 2026): its output limit is the user's, unless the feature asks
     * for more (agentic planning asks for 8,192), so a low setting can't cut a feature's answer.
     */
    private fun withUserOutputLimit(config: AiConfig): AiConfig {
        val picked = (model as? AiModel.Custom)?.takeIf { it.userChoice } ?: return config
        val userLimit = picked.defaultConfig.maxTokens ?: return config
        val requested = config.maxTokens
        return if (requested == null || requested < userLimit) config.copy(maxTokens = userLimit) else config
    }
}

/**
 * DSL function for building AI requests with a cleaner syntax.
 *
 * Usage:
 * ```kotlin
 * val request = aiRequest {
 *     model(AiModel.GrokVision)
 *     userMessageWithImage("Extract text:", base64)
 *     enableReasoning()
 * }
 * ```
 */
fun aiRequest(block: AiRequestBuilder.() -> Unit): AiRequest {
    return AiRequestBuilder().apply(block).build()
}

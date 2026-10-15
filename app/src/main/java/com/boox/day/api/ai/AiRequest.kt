package com.boox.day.api.ai

import com.boox.day.api.ReasoningConfig

/**
 * Unified AI request data class.
 * Encapsulates all parameters needed for an OpenRouter API call.
 *
 * Use [AiRequestBuilder] or [aiRequest] DSL for convenient construction.
 */
data class AiRequest(
    /** The AI model to use for this request */
    val model: AiModel,

    /** List of messages forming the conversation */
    val messages: List<AiMessage>,

    /** Configuration overrides (uses model defaults if not specified) */
    val config: AiConfig = model.defaultConfig,

    /** Request JSON-formatted response (requires JSON_OUTPUT capability) */
    val jsonOutput: Boolean = false,

    /** JSON schema for structured output (alternative to jsonOutput) */
    val jsonSchema: JsonSchema? = null,

    /** Enable extended thinking/reasoning (requires REASONING capability) */
    val enableReasoning: Boolean = false,

    /**
     * Override reasoning config directly (bypasses model-based detection).
     *
     * When set, this takes precedence over [enableReasoning] and the model ID-based
     * reasoning detection in AiClient. Use this when the server provides explicit
     * reasoning configuration via ModelConfig.
     *
     * Example: Server config says model "X" has `reasoning: { enabled: true }`,
     * set this to `ReasoningConfig.enabled()` to use that directly.
     */
    val overrideReasoning: ReasoningConfig? = null,

    /** Route only to providers with zero data retention (OpenRouter `provider.zdr`) */
    val zeroDataRetention: Boolean = false
)

/**
 * JSON Schema definition for structured AI output.
 * When provided, the AI model will strictly follow the schema.
 */
data class JsonSchema(
    /** Schema name (e.g., "calendar_event") */
    val name: String,

    /** The actual JSON schema definition */
    val schema: Map<String, Any>,

    /** Whether to enforce strict schema validation */
    val strict: Boolean = true
)

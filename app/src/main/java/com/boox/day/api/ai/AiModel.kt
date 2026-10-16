package com.boox.day.api.ai

/**
 * Registry of available AI models via OpenRouter.
 *
 * Most models are FREE tier. See docs/ai-models.md for detailed benchmarks.
 *
 * ## Quick Reference (Dec 2025 Benchmark)
 *
 * | Model            | Vision | JSON | Reasoning | Speed   | Notes |
 * |------------------|:------:|:----:|:---------:|--------:|-------|
 * | Gemini 2.0 Flash |   ✓    |  ✓   |           |  1.85s  | Fastest free vision |
 * | Gemma 3 27B      |   ✓    |  ✓   |           |  3.18s  | Good vision |
 * | Nova 2 Lite      |   ✓    |  ✓   |     ✓     |  3.28s  | Recommended OCR primary |
 * | GPT OSS 20B      |        |  ✓   |     ✓     |  3.42s  | Fast text |
 * | Kimi K2          |        |  ✓   |     ✓     |  3.45s  | 32K context |
 * | GPT OSS 120B     |        |  ✓   |     ✓     |  ~5s    | Best reasoning |
 * | GPT-5 Nano       |   ✓    |  ✓   |     ✓     |  ~3s    | PAID - best quality |
 * | Gemini 2.0 Lite  |   ✓    |  ✓   |           |  ~2s    | PAID - AUDIO support |
 * | Nemotron Nano    |   ✓    |      |     ✓     | 31.42s  | SLOW - last resort OCR |
 *
 * Use sealed class to ensure type-safety and IDE autocomplete.
 */
sealed class AiModel(
    open val id: String,
    open val displayName: String,
    open val capabilities: Set<AiCapability>,
    open val defaultConfig: AiConfig
) {
    /**
     * Check if this model supports a specific capability.
     */
    fun hasCapability(capability: AiCapability): Boolean = capability in capabilities

    /**
     * Extract the provider name from the model ID.
     * Model IDs follow the pattern: "provider/model-name" (e.g., "google/gemini-2.0-flash-exp:free")
     * Used for BYOK provider restrictions to prevent fallback charges.
     */
    val provider: String
        get() = id.substringBefore("/")

    /**
     * Amazon Nova 2 Lite - Fast vision with reasoning.
     * Best for: Quick image analysis with extended thinking, OCR with layout understanding.
     * Supports reasoning via {"enabled": true} for step-by-step analysis.
     * JSON output supported (returns markdown-wrapped JSON, use JsonExtractor).
     */
    object NovaLite : AiModel(
        id = "amazon/nova-2-lite-v1:free",
        displayName = "Nova 2 Lite",
        capabilities = setOf(
            AiCapability.VISION,
            AiCapability.FAST,
            AiCapability.REASONING,
            AiCapability.JSON_OUTPUT  // Tested Dec 2025 - returns ```json wrapped
        ),
        defaultConfig = AiConfig(
            temperature = 0.3f,
            maxTokens = 2048
        )
    )

    /**
     * GPT OSS 20B - Fast text processing with reasoning.
     * Best for: Text cleanup, JSON responses, case normalization.
     * Works correctly - outputs to content field, reasoning separate.
     */
    object GptOss20B : AiModel(
        id = "openai/gpt-oss-20b:free",
        displayName = "GPT OSS 20B",
        capabilities = setOf(
            AiCapability.TEXT,
            AiCapability.JSON_OUTPUT,
            AiCapability.REASONING,
            AiCapability.FAST
        ),
        defaultConfig = AiConfig(
            temperature = 0.3f,
            maxTokens = 2048
        )
    )

    /**
     * GPT OSS 120B - Larger reasoning model with better comprehension.
     * Best for: Complex text understanding, semantic correction, entity extraction.
     * Supports JSON schema output and reasoning. Slower but more accurate than 20B.
     */
    object GptOss120B : AiModel(
        id = "openai/gpt-oss-120b:free",
        displayName = "GPT OSS 120B",
        capabilities = setOf(
            AiCapability.TEXT,
            AiCapability.JSON_OUTPUT,
            AiCapability.REASONING
        ),
        defaultConfig = AiConfig(
            temperature = 0.3f,
            maxTokens = 4096
        )
    )

    /**
     * Gemini 2.0 Flash Experimental - Free tier with strong vision capabilities.
     * Best for: Image analysis, bounding box detection, structured output.
     * NOTE: Does NOT support audio input (use Gemini2FlashLite for audio).
     */
    object Gemini2FlashExp : AiModel(
        id = "google/gemini-2.0-flash-exp:free",
        displayName = "Gemini 2.0 Flash Exp",
        capabilities = setOf(
            AiCapability.TEXT,
            AiCapability.VISION,
            AiCapability.JSON_OUTPUT
        ),
        defaultConfig = AiConfig(
            temperature = 0.2f,
            maxTokens = 4096
        )
    )

    /**
     * Gemini 2.0 Flash Lite - Multimodal with AUDIO support (paid).
     * Best for: Voice transcription + parsing in one step, fast multimodal tasks.
     * ONLY model with audio input support - required for voice features.
     * Pricing: $0.075/M input, $0.30/M output (very cheap for voice commands).
     */
    object Gemini2FlashLite : AiModel(
        id = "google/gemini-2.0-flash-lite-001",
        displayName = "Gemini 2.0 Flash Lite",
        capabilities = setOf(
            AiCapability.TEXT,
            AiCapability.VISION,
            AiCapability.AUDIO,
            AiCapability.JSON_OUTPUT,
            AiCapability.FAST
        ),
        defaultConfig = AiConfig(
            temperature = 0.3f,
            maxTokens = 4096
        )
    )

    /**
     * NVIDIA Nemotron Nano 12B VL - OCR and document intelligence optimized.
     * Best for: Handwriting recognition, document layout understanding, chart reasoning.
     * Free tier, hybrid Transformer-Mamba architecture for efficiency.
     * Supports reasoning via {"enabled": true}.
     *
     * ⚠️ SLOW (~31s) - Use as LAST RESORT fallback for difficult OCR cases.
     * Prefer NovaLite or Gemini2FlashExp for normal OCR tasks.
     */
    object NemotronNano : AiModel(
        id = "nvidia/nemotron-nano-12b-v2-vl:free",
        displayName = "Nemotron Nano 12B",
        capabilities = setOf(
            AiCapability.TEXT,
            AiCapability.VISION,
            AiCapability.REASONING
        ),
        defaultConfig = AiConfig(
            temperature = 0.2f,
            maxTokens = 4096
        )
    )

    /**
     * MoonshotAI Kimi K2 - Large-scale MoE model (1T total, 32B active).
     * Best for: Agentic tasks, tool use, reasoning, code synthesis.
     * Free tier, 32K context (up to 128K), excels at LiveCodeBench, SWE-bench, ZebraLogic.
     * Uses MuonClip optimizer for stable large-scale MoE training.
     * JSON output supported (returns clean JSON). Tested Dec 2025.
     */
    object KimiK2 : AiModel(
        id = "moonshotai/kimi-k2:free",
        displayName = "Kimi K2",
        capabilities = setOf(
            AiCapability.TEXT,
            AiCapability.REASONING,
            AiCapability.JSON_OUTPUT  // Tested Dec 2025 - returns clean JSON
        ),
        defaultConfig = AiConfig(
            temperature = 0.7f,
            maxTokens = 4096
        )
    )

    /**
     * Google Gemma 3 27B - Multimodal vision-language model.
     * Best for: Image analysis, 140+ languages, math, reasoning, structured output, function calling.
     * Free tier, 131K context, successor to Gemma 2.
     * Confirmed working: Vision accepts images and returns text descriptions.
     */
    object Gemma3_27B : AiModel(
        id = "google/gemma-3-27b-it:free",
        displayName = "Gemma 3 27B",
        capabilities = setOf(
            AiCapability.TEXT,
            AiCapability.VISION,
            AiCapability.JSON_OUTPUT
        ),
        defaultConfig = AiConfig(
            temperature = 0.5f,
            maxTokens = 4096
        )
    )

    /**
     * OpenAI GPT-5 Nano - Lightweight GPT-5 variant with multimodal capabilities.
     * Best for: Fast vision analysis, JSON output, reasoning tasks, translation.
     * Paid model (very affordable), supports image input + structured output + reasoning.
     * Confirmed working: Vision (base64), JSON output, reasoning all verified.
     */
    object Gpt5Nano : AiModel(
        id = "openai/gpt-5-nano",
        displayName = "GPT-5 Nano",
        capabilities = setOf(
            AiCapability.TEXT,
            AiCapability.VISION,
            AiCapability.JSON_OUTPUT,
            AiCapability.REASONING,
            AiCapability.FAST
        ),
        defaultConfig = AiConfig(
            temperature = 0.3f,
            maxTokens = 4096
        )
    )

    /**
     * Custom model for future extensibility.
     * Allows defining new models without modifying this sealed class.
     *
     * [userChoice]: the model the user picked for an AI job (Oct 2026). Its [reasoning] is sent
     * as-is, null = nothing (the model's own default), whatever the feature asks for, and
     * [defaultConfig]'s max tokens is the user's output limit.
     */
    data class Custom(
        override val id: String,
        override val displayName: String,
        override val capabilities: Set<AiCapability>,
        override val defaultConfig: AiConfig,
        val userChoice: Boolean = false,
        val reasoning: com.boox.day.api.ReasoningConfig? = null
    ) : AiModel(id, displayName, capabilities, defaultConfig)

    companion object {
        /**
         * All available models for selection.
         */
        fun allModels(): List<AiModel> = listOf(
            NovaLite,
            GptOss20B,
            GptOss120B,
            Gemini2FlashExp,
            Gemini2FlashLite,  // Paid - required for audio/voice features
            NemotronNano,
            KimiK2,
            Gemma3_27B,
            Gpt5Nano           // Paid - vision + JSON + reasoning
        )

        /**
         * Find a model by its OpenRouter ID.
         */
        fun fromId(id: String): AiModel? = allModels().find { it.id == id }
    }
}

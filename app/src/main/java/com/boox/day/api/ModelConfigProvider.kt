package com.boox.day.api

import android.util.Log
import com.boox.day.api.ai.AiModel

/**
 * Provides the AI model configuration: the hardcoded defaults in this file.
 *
 * This is the central place where AI services get their model configurations.
 * It bridges the ModalityConfig / ModelEntry types with the existing AiModel system.
 * There is no server config: every modality always uses its default below.
 *
 * ## Usage:
 * ```kotlin
 * // Get the primary vision model for OCR
 * val modelEntry = ModelConfigProvider.getModel(Modality.VISION, ModelRole.PRIMARY)
 *
 * // Get the full fallback chain for vision
 * val fallbackChain = ModelConfigProvider.getFallbackChain(Modality.VISION)
 *
 * // Use in AiRequest
 * val request = AiRequest(
 *     model = AiModel.Custom(
 *         id = modelEntry.id,
 *         displayName = modelEntry.id.substringAfterLast("/"),
 *         capabilities = setOf(AiCapability.VISION),
 *         defaultConfig = AiConfig()
 *     ),
 *     messages = listOf(...)
 * )
 * ```
 */
object ModelConfigProvider {

    private const val TAG = "ModelConfigProvider"

    // ═══════════════════════════════════════════════════════════════════════════
    // HARDCODED DEFAULTS - the only model configuration
    // Verified against OpenRouter model metadata in May 2026.
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Default vision modality config (OCR, handwriting recognition).
     * Uses models that support image input.
     */
    private val DEFAULT_VISION = ModalityConfig(
        primary = ModelEntry(id = "openai/gpt-5-nano", reasoning = ReasoningParam(effort = "medium")),
        fallback = ModelEntry(id = "google/gemini-3.1-flash-lite", reasoning = null),
        safety = ModelEntry(id = "google/gemma-4-26b-a4b-it:free", reasoning = null)
    )

    /**
     * Default audio modality config (voice transcription).
     * Uses current low-cost audio-capable multimodal models.
     */
    private val DEFAULT_AUDIO = ModalityConfig(
        primary = ModelEntry(id = "google/gemini-3.1-flash-lite", reasoning = null),
        fallback = ModelEntry(id = "google/gemini-2.0-flash-lite-001", reasoning = null),
        safety = ModelEntry(id = "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free", reasoning = ReasoningParam(enabled = true))
    )

    /**
     * Default reasoning modality config (semantic correction, understanding context).
     * Uses text-only models with strong reasoning capabilities.
     */
    private val DEFAULT_REASONING = ModalityConfig(
        primary = ModelEntry(id = "google/gemini-3-flash-preview", reasoning = null),
        fallback = ModelEntry(id = "nvidia/nemotron-3-super-120b-a12b:free", reasoning = ReasoningParam(enabled = true)),
        safety = ModelEntry(id = "google/gemma-4-31b-it:free", reasoning = ReasoningParam(enabled = true))
    )

    /**
     * Default text modality config (formatting, parsing, simple transformations).
     * Uses fast text-only models.
     */
    private val DEFAULT_TEXT = ModalityConfig(
        primary = ModelEntry(id = "openai/gpt-5-nano", reasoning = ReasoningParam(effort = "medium")),
        fallback = ModelEntry(id = "google/gemini-3.1-flash-lite", reasoning = null),
        safety = ModelEntry(id = "nvidia/nemotron-3-super-120b-a12b:free", reasoning = ReasoningParam(enabled = true))
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get a specific model for a modality and role.
     *
     * @param modality The use case (vision, audio, reasoning, text)
     * @param role The priority level (primary, fallback, safety)
     * @return ModelEntry with model ID and reasoning parameters
     */
    fun getModel(modality: Modality, role: ModelRole): ModelEntry {
        val modalityConfig = getModalityConfig(modality)
        return when (role) {
            ModelRole.PRIMARY -> modalityConfig.primary
            ModelRole.FALLBACK -> modalityConfig.fallback
            ModelRole.SAFETY -> modalityConfig.safety
        }
    }

    /**
     * Get the full fallback chain for a modality.
     * Returns models in order: primary -> fallback -> safety
     *
     * @param modality The use case (vision, audio, reasoning, text)
     * @return List of ModelEntry in fallback order
     */
    fun getFallbackChain(modality: Modality): List<ModelEntry> {
        return getModalityConfig(modality).getFallbackChain()
    }

    /**
     * Get the complete modality configuration.
     *
     * @param modality The use case (vision, audio, reasoning, text)
     * @return ModalityConfig with primary, fallback, and safety models
     */
    fun getModalityConfig(modality: Modality): ModalityConfig {
        Log.d(TAG, "Using hardcoded defaults for $modality")
        return when (modality) {
            Modality.VISION -> DEFAULT_VISION
            Modality.AUDIO -> DEFAULT_AUDIO
            Modality.REASONING -> DEFAULT_REASONING
            Modality.TEXT -> DEFAULT_TEXT
        }
    }

    /**
     * Convert a ModelEntry's reasoning param to ReasoningConfig for API requests.
     *
     * Supports both reasoning formats:
     * - {"effort": "medium"} for OpenAI models → ReasoningConfig.withEffort("medium")
     * - {"enabled": true} for other models → ReasoningConfig.enabled()
     *
     * @param entry The ModelEntry from the model configuration
     * @return ReasoningConfig if reasoning is enabled, null otherwise
     */
    fun toReasoningConfig(entry: ModelEntry): com.boox.day.api.ReasoningConfig? {
        val param = entry.reasoning ?: return null
        return when {
            param.effort != null -> com.boox.day.api.ReasoningConfig.withEffort(param.effort)
            param.enabled == true -> com.boox.day.api.ReasoningConfig.enabled()
            else -> null
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONVENIENCE METHODS FOR EXISTING AIMODEL INTEGRATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Convert a ModelEntry to an AiModel for use with existing AiClient.
     * Uses AiModel.Custom to wrap the model ID when it is not a built-in model.
     *
     * @param entry The ModelEntry from the model configuration
     * @param capabilities The capabilities to assign (based on modality)
     * @return AiModel instance
     */
    fun toAiModel(entry: ModelEntry, capabilities: Set<com.boox.day.api.ai.AiCapability>): AiModel {
        // Try to find a matching built-in model first
        val builtIn = AiModel.fromId(entry.id)
        if (builtIn != null) {
            return builtIn
        }

        // Otherwise create a custom model
        return AiModel.Custom(
            id = entry.id,
            displayName = entry.id.substringAfterLast("/").substringBefore(":"),
            capabilities = capabilities,
            defaultConfig = com.boox.day.api.ai.AiConfig(
                temperature = 0.3f,
                maxTokens = 4096
            )
        )
    }

    /**
     * Get an AiModel for the primary vision model.
     */
    fun getPrimaryVisionModel(): AiModel {
        val entry = getModel(Modality.VISION, ModelRole.PRIMARY)
        return toAiModel(entry, setOf(
            com.boox.day.api.ai.AiCapability.VISION,
            com.boox.day.api.ai.AiCapability.JSON_OUTPUT
        ))
    }

    /**
     * Get the fallback chain as AiModels for vision.
     */
    fun getVisionFallbackChain(): List<AiModel> {
        return getFallbackChain(Modality.VISION).map { entry ->
            toAiModel(entry, setOf(
                com.boox.day.api.ai.AiCapability.VISION,
                com.boox.day.api.ai.AiCapability.JSON_OUTPUT
            ))
        }
    }

    /**
     * Get an AiModel for the primary audio model.
     */
    fun getPrimaryAudioModel(): AiModel {
        val entry = getModel(Modality.AUDIO, ModelRole.PRIMARY)
        return toAiModel(entry, setOf(
            com.boox.day.api.ai.AiCapability.AUDIO,
            com.boox.day.api.ai.AiCapability.JSON_OUTPUT
        ))
    }

    /**
     * Get an AiModel for the primary reasoning model.
     */
    fun getPrimaryReasoningModel(): AiModel {
        val entry = getModel(Modality.REASONING, ModelRole.PRIMARY)
        return toAiModel(entry, setOf(
            com.boox.day.api.ai.AiCapability.TEXT,
            com.boox.day.api.ai.AiCapability.JSON_OUTPUT,
            com.boox.day.api.ai.AiCapability.REASONING
        ))
    }

    /**
     * Get the fallback chain as AiModels for reasoning.
     */
    fun getReasoningFallbackChain(): List<AiModel> {
        return getFallbackChain(Modality.REASONING).map { entry ->
            toAiModel(entry, setOf(
                com.boox.day.api.ai.AiCapability.TEXT,
                com.boox.day.api.ai.AiCapability.JSON_OUTPUT,
                com.boox.day.api.ai.AiCapability.REASONING
            ))
        }
    }

    /**
     * Get an AiModel for the primary text model.
     */
    fun getPrimaryTextModel(): AiModel {
        val entry = getModel(Modality.TEXT, ModelRole.PRIMARY)
        return toAiModel(entry, setOf(
            com.boox.day.api.ai.AiCapability.TEXT,
            com.boox.day.api.ai.AiCapability.JSON_OUTPUT
        ))
    }

    /**
     * Get the fallback chain as AiModels for text.
     */
    fun getTextFallbackChain(): List<AiModel> {
        return getFallbackChain(Modality.TEXT).map { entry ->
            toAiModel(entry, setOf(
                com.boox.day.api.ai.AiCapability.TEXT,
                com.boox.day.api.ai.AiCapability.JSON_OUTPUT
            ))
        }
    }
}

package com.boox.day.api

import kotlinx.serialization.Serializable

/**
 * AI models for one use case (vision, audio, reasoning, text): a primary model and two fallbacks.
 * The app's defaults are in ModelConfigProvider.
 */
@Serializable
data class ModalityConfig(
    val primary: ModelEntry,
    val fallback: ModelEntry,
    val safety: ModelEntry
) {
    fun getFallbackChain(): List<ModelEntry> = listOf(primary, fallback, safety)
}

@Serializable
data class ModelEntry(
    val id: String,
    val reasoning: ReasoningParam? = null
) {
    val provider: String get() = id.substringBefore("/")
    val hasReasoning: Boolean get() = reasoning?.enabled == true || reasoning?.effort != null
}

@Serializable
data class ReasoningParam(
    val enabled: Boolean? = null,
    val effort: String? = null
)

enum class Modality { VISION, AUDIO, REASONING, TEXT }
enum class ModelRole { PRIMARY, FALLBACK, SAFETY }

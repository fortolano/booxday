package com.boox.day.api

import kotlinx.serialization.Serializable

/**
 * One of BooxDay's own AI models. The lists per AI job (AiJob) are in ModelConfigProvider.
 */
@Serializable
data class ModelEntry(
    val id: String,
    val reasoning: ReasoningParam? = null,
    /** Name shown to users, e.g. "Gemini 3.1 Flash Lite" (AI Models card) */
    val name: String = ""
) {
    val provider: String get() = id.substringBefore("/")
    val hasReasoning: Boolean get() = reasoning?.enabled == true || reasoning?.effort != null
}

@Serializable
data class ReasoningParam(
    val enabled: Boolean? = null,
    val effort: String? = null
)

/** The old model groups; AI jobs (AiJob) replaced them. Kept because older files still import it. */
enum class Modality { VISION, AUDIO, REASONING, TEXT }

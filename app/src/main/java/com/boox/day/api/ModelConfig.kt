package com.boox.day.api

import kotlinx.serialization.Serializable

/**
 * One of Boox Day's own AI models. The lists per AI job (AiJob) are in ModelConfigProvider.
 */
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

/** The old model groups; AI jobs (AiJob) replaced them. Kept because older files still import it. */
enum class Modality { VISION, AUDIO, REASONING, TEXT }

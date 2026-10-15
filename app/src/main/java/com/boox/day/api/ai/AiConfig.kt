package com.boox.day.api.ai

/**
 * Configuration parameters for AI model requests.
 * All parameters are optional and will use model defaults if not specified.
 */
data class AiConfig(
    /** Controls randomness in output (0.0 = deterministic, 1.0 = creative) */
    val temperature: Float = 0.7f,

    /** Maximum number of tokens to generate in the response */
    val maxTokens: Int? = null,

    /** Nucleus sampling: only consider tokens with top_p cumulative probability */
    val topP: Float? = null,

    /** Penalize tokens that have already appeared (-2.0 to 2.0) */
    val presencePenalty: Float? = null,

    /** Penalize tokens based on frequency in the text so far (-2.0 to 2.0) */
    val frequencyPenalty: Float? = null
)

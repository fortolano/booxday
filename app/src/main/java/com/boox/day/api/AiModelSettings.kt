package com.boox.day.api

import com.boox.day.api.ai.AiCapability
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlin.math.abs

/**
 * The AI jobs a user can choose an OpenRouter model for (Oct 2026, plan:
 * docs/AI_MODEL_PICKER_PLAN.md). Each job's requests need the same model abilities.
 */
enum class AiJob(
    /** What a model is given for this job, so the features' requests can be built */
    val capabilities: Set<AiCapability>,
    /** Input a model must accept for this job (OpenRouter `input_modalities`) */
    val requiredInput: String,
    /** Reasoning level pre-selected for a picked model (an OpenRouter effort name) */
    val recommendedReasoning: String,
    /** Room for the answer itself, before reasoning is added (today's values) */
    val answerTokens: Int,
    /** Temperature when the feature sets none (Boox Day's models and picked ones) */
    val temperature: Float,
    /** Output tokens for Boox Day's own models when the feature sets none (today's values) */
    val defaultMaxTokens: Int
) {
    HANDWRITING(setOf(AiCapability.VISION, AiCapability.JSON_OUTPUT), "image", "low", 4_000, 0.3f, 4096),
    VOICE(setOf(AiCapability.AUDIO, AiCapability.JSON_OUTPUT), "audio", "none", 4_000, 0.3f, 4096),
    WRITING(setOf(AiCapability.TEXT, AiCapability.JSON_OUTPUT), "text", "low", 4_000, 0.3f, 4096),
    PLANNING(setOf(AiCapability.TEXT, AiCapability.JSON_OUTPUT, AiCapability.REASONING), "text", "medium", 8_000, 0.3f, 4096),
    SEARCH_INDEXING(setOf(AiCapability.VISION, AiCapability.JSON_OUTPUT), "image", "none", 3_000, 0f, 3000)
}

/**
 * What a model offers for reasoning, from OpenRouter's models list: the `reasoning` parameter in
 * `supported_parameters` and the model's `reasoning` object.
 */
@Serializable
data class ReasoningOptions(
    /** The model accepts OpenRouter's `reasoning` parameter */
    val supported: Boolean = false,
    /** Reasoning can't be turned off; OpenRouter says such models reject `effort: "none"` */
    val mandatory: Boolean = false,
    /** Effort names the model accepts, as OpenRouter lists them (highest first); null = no levels */
    val efforts: List<String>? = null
) {
    companion object {
        /**
         * Reads OpenRouter's model data. `supported_efforts` missing = no effort levels (on/off only),
         * `null` = every level OpenRouter accepts.
         */
        fun fromOpenRouter(supportedParameters: List<String>, reasoning: JsonObject?): ReasoningOptions {
            if ("reasoning" !in supportedParameters) return ReasoningOptions()
            val mandatory = (reasoning?.get("mandatory") as? JsonPrimitive)?.booleanOrNull == true
            val efforts = when (val value = reasoning?.get("supported_efforts")) {
                null -> null
                is JsonNull -> AiModelSettings.LEVELS.reversed()
                is JsonArray -> value
                    .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.lowercase() }
                    .filter { it.isNotEmpty() }
                    .ifEmpty { null }
                else -> null
            }
            return ReasoningOptions(supported = true, mandatory = mandatory, efforts = efforts)
        }
    }
}

/**
 * Rules for a picked model's reasoning level and output tokens. Built from each model's own data,
 * so models with other levels, or levels OpenRouter adds later, work without code changes.
 */
object AiModelSettings {
    /** OpenRouter's effort levels, low to high */
    val LEVELS = listOf("none", "minimal", "low", "medium", "high", "xhigh", "max")

    /** Choices for a model that can turn reasoning off and on but has no effort levels */
    const val OFF = "off"
    const val ON = "on"

    /** Highest recommended output tokens: a larger limit holds more credit while a request runs */
    const val OUTPUT_TOKENS_CAP = 64_000

    /**
     * Reasoning choices to show for a model, low to high; empty = no control. Off is offered unless
     * the model's reasoning is mandatory: as the model's "none" level when it lists one, otherwise
     * as [OFF] (sent as `enabled: false`).
     */
    fun reasoningChoices(options: ReasoningOptions): List<String> {
        if (!options.supported) return emptyList()
        val efforts = options.efforts?.distinct()
            ?: return if (options.mandatory) emptyList() else listOf(OFF, ON)
        // Known names are sorted by level; with unknown (newer) names, trust OpenRouter's order
        val ascending = if (efforts.all { it in LEVELS }) {
            efforts.sortedBy { LEVELS.indexOf(it) }
        } else {
            efforts.reversed()
        }
        val levels = if (options.mandatory) ascending.filter { it != "none" } else ascending
        return if (!options.mandatory && "none" !in levels) listOf(OFF) + levels else levels
    }

    /** The job's recommended level, or the model's nearest one; null when the model has no control. */
    fun recommendedReasoning(job: AiJob, options: ReasoningOptions): String? {
        val choices = reasoningChoices(options)
        if (choices.isEmpty()) return null
        if (choices == listOf(OFF, ON)) return if (job.recommendedReasoning == "none") OFF else ON
        return nearest(job.recommendedReasoning, choices)
    }

    /** The choice closest to [target]; the lower one on a tie. [OFF] counts as "none". */
    internal fun nearest(target: String, choices: List<String>): String {
        val targetRank = rank(target) ?: return choices.first()
        return choices
            .filter { rank(it) != null }
            .minWithOrNull(compareBy<String>({ abs(rank(it)!! - targetRank) }, { rank(it)!! }))
            ?: choices.first()
    }

    private fun rank(level: String): Int? =
        if (level == OFF) 0 else LEVELS.indexOf(level).takeIf { it >= 0 }

    /**
     * What to send for a saved level. Null = send nothing, so the model uses its own default: no
     * level saved, or the model no longer offers the saved one.
     */
    fun reasoningConfig(level: String?, options: ReasoningOptions): ReasoningConfig? {
        if (level == null || level !in reasoningChoices(options)) return null
        return when (level) {
            OFF -> ReasoningConfig.disabled()
            ON -> ReasoningConfig.enabled()
            else -> ReasoningConfig.withEffort(level)
        }
    }

    /**
     * Share of `max_tokens` OpenRouter gives reasoning at [level], in percent (OpenRouter docs,
     * reasoning tokens: minimal 10, low 20, medium 50, high 80, xhigh and max 95; "on" = medium).
     */
    internal fun reasoningSharePercent(level: String?, options: ReasoningOptions): Int {
        if (!options.supported) return 0
        return when (level) {
            null -> if (options.mandatory) 50 else 0
            OFF, "none" -> 0
            "minimal" -> 10
            "low" -> 20
            ON, "medium" -> 50
            "high" -> 80
            "xhigh", "max" -> 95
            else -> 50
        }
    }

    /**
     * Recommended output tokens: the job's answer room plus room for reasoning, rounded up to the
     * next 1,000, at most [OUTPUT_TOKENS_CAP] and the model's own limit.
     */
    fun recommendedMaxOutputTokens(
        job: AiJob,
        level: String?,
        options: ReasoningOptions,
        modelMaxOutput: Int?
    ): Int {
        val reasoningShare = reasoningSharePercent(level, options)
        val answerShare = 100 - reasoningShare
        val withReasoning = (job.answerTokens * 100 + answerShare - 1) / answerShare
        val rounded = (withReasoning + 999) / 1000 * 1000
        val modelLimit = modelMaxOutput?.takeIf { it > 0 } ?: Int.MAX_VALUE
        return minOf(rounded, OUTPUT_TOKENS_CAP, modelLimit)
    }
}

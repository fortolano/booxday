package com.boox.day.data.datastore

import com.boox.day.api.AiJob
import com.boox.day.api.ReasoningOptions
import kotlinx.serialization.Serializable

/**
 * The OpenRouter model the user picked for each AI job (Oct 2026). Null = Boox Day's choice,
 * which follows app updates (ModelConfigProvider). Saved with the app settings and exported in
 * backups: nothing here is secret.
 */
@Serializable
data class AiModelChoices(
    val handwriting: AiModelChoice? = null,
    val voice: AiModelChoice? = null,
    val writing: AiModelChoice? = null,
    val planning: AiModelChoice? = null,
    val searchIndexing: AiModelChoice? = null
) {
    fun forJob(job: AiJob): AiModelChoice? = when (job) {
        AiJob.HANDWRITING -> handwriting
        AiJob.VOICE -> voice
        AiJob.WRITING -> writing
        AiJob.PLANNING -> planning
        AiJob.SEARCH_INDEXING -> searchIndexing
    }

    fun withJob(job: AiJob, choice: AiModelChoice?): AiModelChoices = when (job) {
        AiJob.HANDWRITING -> copy(handwriting = choice)
        AiJob.VOICE -> copy(voice = choice)
        AiJob.WRITING -> copy(writing = choice)
        AiJob.PLANNING -> copy(planning = choice)
        AiJob.SEARCH_INDEXING -> copy(searchIndexing = choice)
    }
}

/**
 * One picked model and its settings, with what the model offered when it was picked (from
 * OpenRouter's models list), so requests can be checked without the list.
 */
@Serializable
data class AiModelChoice(
    /** OpenRouter model id, e.g. "google/gemini-3.1-flash-lite" */
    val id: String,
    /** Model name without the lab, e.g. "Gemini 3.1 Flash Lite" */
    val name: String = "",
    /** The lab that made the model, e.g. "Google" */
    val lab: String = "",
    /** Reasoning level: an OpenRouter effort name, "off" or "on"; null = the model's own default */
    val reasoning: String? = null,
    /** Max output tokens; null = the recommended value for the reasoning level */
    val maxOutputTokens: Int? = null,
    val reasoningOptions: ReasoningOptions = ReasoningOptions(),
    /** The model's own output limit (OpenRouter `top_provider.max_completion_tokens`) */
    val modelMaxOutputTokens: Int? = null,
    /** How much the model can read (OpenRouter `context_length`), shown only */
    val contextLength: Int? = null,
    /** What the model accepts (OpenRouter `input_modalities`); empty = not known */
    val inputs: List<String> = emptyList()
)

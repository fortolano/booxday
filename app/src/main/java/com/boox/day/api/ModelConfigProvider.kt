package com.boox.day.api

import android.util.Log
import com.boox.day.api.ai.AiConfig
import com.boox.day.api.ai.AiModel
import com.boox.day.data.datastore.AiModelChoice
import com.boox.day.data.datastore.GlobalAppSettings

/**
 * The AI models each job tries, in order (Oct 2026, plan: docs/AI_MODEL_PICKER_PLAN.md):
 *
 * 1. The model the user picked for the job in AI Settings, with its reasoning level and output
 *    tokens ([userModel]).
 * 2. Boox Day's choice: tested models in this file, which follow app updates instead of being
 *    copied into the user's settings. While the account has no credit ([OpenRouterAccount]) it is
 *    the free set, so AI keeps working on free models.
 *
 * A failing model never ends a feature on its own: the next model in the list answers.
 */
object ModelConfigProvider {

    private const val TAG = "ModelConfigProvider"

    // ═══════════════════════════════════════════════════════════════════════════
    // BOOX DAY'S CHOICE
    // Checked against OpenRouter's models list on 15 Oct 2026 (all listed, none retiring).
    // The reasoning params are kept with the entries but not sent (see toReasoningConfig).
    // ═══════════════════════════════════════════════════════════════════════════

    private val GPT_5_NANO = ModelEntry(id = "openai/gpt-5-nano", reasoning = ReasoningParam(effort = "medium"))
    private val GEMINI_31_FLASH_LITE = ModelEntry(id = "google/gemini-3.1-flash-lite")
    private val GEMINI_3_FLASH_PREVIEW = ModelEntry(id = "google/gemini-3-flash-preview")
    private val GEMMA_4_26B_FREE = ModelEntry(id = "google/gemma-4-26b-a4b-it:free")
    private val GEMMA_4_31B_FREE = ModelEntry(id = "google/gemma-4-31b-it:free", reasoning = ReasoningParam(enabled = true))
    private val NEMOTRON_3_SUPER_FREE = ModelEntry(id = "nvidia/nemotron-3-super-120b-a12b:free", reasoning = ReasoningParam(enabled = true))
    private val NEMOTRON_3_NANO_OMNI_FREE = ModelEntry(id = "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free", reasoning = ReasoningParam(enabled = true))

    /** With credit: the tested paid models first, free models as the last backups. */
    private val WITH_CREDIT: Map<AiJob, List<ModelEntry>> = mapOf(
        AiJob.HANDWRITING to listOf(GPT_5_NANO, GEMINI_31_FLASH_LITE, GEMMA_4_26B_FREE),
        // Oct 2026: google/gemini-2.0-flash-lite-001 was the middle model; OpenRouter retired it
        AiJob.VOICE to listOf(GEMINI_31_FLASH_LITE, NEMOTRON_3_NANO_OMNI_FREE),
        AiJob.WRITING to listOf(GPT_5_NANO, GEMINI_31_FLASH_LITE, NEMOTRON_3_SUPER_FREE),
        AiJob.PLANNING to listOf(GEMINI_3_FLASH_PREVIEW, NEMOTRON_3_SUPER_FREE, GEMMA_4_31B_FREE),
        // Chosen by the 14 Oct 2026 test on real pages (OpenRouterOcrService)
        AiJob.SEARCH_INDEXING to listOf(GEMINI_31_FLASH_LITE)
    )

    /**
     * No credit: free models only (50 requests a day, 1,000 after buying $10 of credit). Search
     * indexing keeps its paid model: no free model keeps pages private, and a refused request
     * pauses indexing until there is credit.
     */
    private val NO_CREDIT: Map<AiJob, List<ModelEntry>> = mapOf(
        AiJob.HANDWRITING to listOf(GEMMA_4_26B_FREE, GEMMA_4_31B_FREE),
        AiJob.VOICE to listOf(NEMOTRON_3_NANO_OMNI_FREE),
        AiJob.WRITING to listOf(NEMOTRON_3_SUPER_FREE, GEMMA_4_31B_FREE),
        AiJob.PLANNING to listOf(NEMOTRON_3_SUPER_FREE, GEMMA_4_31B_FREE),
        AiJob.SEARCH_INDEXING to listOf(GEMINI_31_FLASH_LITE)
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════

    /** Models for [job] in the order to try: the user's pick, then Boox Day's choice. */
    fun getChain(job: AiJob): List<AiModel> {
        val defaults = defaultChain(job, OpenRouterAccount.isFreeOnly()).map { toAiModel(it, job) }
        return (listOfNotNull(userModel(job)) + defaults).distinctBy { it.id }
    }

    /** Boox Day's choice for [job]; [freeOnly] = the free set. */
    fun defaultChain(job: AiJob, freeOnly: Boolean): List<ModelEntry> =
        (if (freeOnly) NO_CREDIT else WITH_CREDIT).getValue(job)

    /**
     * The user's pick for [job] as a model, or null: no pick, or one this job can't use. Always a
     * [AiModel.Custom] with the job's capabilities, never one of the old built-in models, whose
     * capability lists can make a feature's request builder throw (AiRequestBuilder.require).
     */
    fun userModel(
        job: AiJob,
        choice: AiModelChoice? = GlobalAppSettings.current.aiModels.forJob(job)
    ): AiModel.Custom? {
        if (choice == null) return null
        val id = choice.id.trim()
        if (id.isEmpty()) return null
        if (choice.inputs.isNotEmpty() && job.requiredInput !in choice.inputs) {
            Log.w(TAG, "Not using $id for $job: it does not read ${job.requiredInput}")
            return null
        }
        val maxTokens = choice.maxOutputTokens?.takeIf { it > 0 }
            ?: AiModelSettings.recommendedMaxOutputTokens(
                job, choice.reasoning, choice.reasoningOptions, choice.modelMaxOutputTokens
            )
        return AiModel.Custom(
            id = id,
            displayName = choice.name.ifBlank { id.substringAfterLast("/").substringBefore(":") },
            capabilities = job.capabilities,
            defaultConfig = AiConfig(temperature = job.temperature, maxTokens = maxTokens),
            userChoice = true,
            reasoning = AiModelSettings.reasoningConfig(choice.reasoning, choice.reasoningOptions)
        )
    }

    fun getVisionFallbackChain(): List<AiModel> = getChain(AiJob.HANDWRITING)

    fun getAudioFallbackChain(): List<AiModel> = getChain(AiJob.VOICE)

    fun getTextFallbackChain(): List<AiModel> = getChain(AiJob.WRITING)

    fun getReasoningFallbackChain(): List<AiModel> = getChain(AiJob.PLANNING)

    /**
     * Convert a ModelEntry's reasoning param to ReasoningConfig for API requests.
     *
     * Supports both reasoning formats:
     * - {"effort": "medium"} for OpenAI models → ReasoningConfig.withEffort("medium")
     * - {"enabled": true} for other models → ReasoningConfig.enabled()
     *
     * Not called (Oct 2026): Boox Day's choice still sends reasoning as the features ask for it.
     */
    fun toReasoningConfig(entry: ModelEntry): ReasoningConfig? {
        val param = entry.reasoning ?: return null
        return when {
            param.effort != null -> ReasoningConfig.withEffort(param.effort)
            param.enabled == true -> ReasoningConfig.enabled()
            else -> null
        }
    }

    /**
     * One of Boox Day's models as an AiModel: a matching built-in model (GPT-5 Nano), otherwise a
     * custom model with the job's capabilities.
     */
    private fun toAiModel(entry: ModelEntry, job: AiJob): AiModel {
        AiModel.fromId(entry.id)?.let { return it }
        return AiModel.Custom(
            id = entry.id,
            displayName = entry.id.substringAfterLast("/").substringBefore(":"),
            capabilities = job.capabilities,
            defaultConfig = AiConfig(temperature = job.temperature, maxTokens = job.defaultMaxTokens)
        )
    }
}

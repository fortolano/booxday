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
 * 2. BooxDay's choice: tested models in this file, which follow app updates instead of being
 *    copied into the user's settings. While the account has no credit ([OpenRouterAccount]) it is
 *    the free set, so AI keeps working on free models.
 *
 * A failing model never ends a feature on its own: the next model in the list answers.
 */
object ModelConfigProvider {

    private const val TAG = "ModelConfigProvider"

    // ═══════════════════════════════════════════════════════════════════════════
    // BOOXDAY'S CHOICE
    // Paid models: stable releases (no previews) with zero-data-retention providers, from two
    // labs, each with the reasoning level it was tested at (sent with every request, see
    // toReasoningConfig). Chosen by the 29 Oct 2026 live test of every Memos and Tasks AI action:
    // the models before (GPT-5 Nano, Gemini 3 Flash Preview) were sent "reasoning on", thought
    // through most of the 4,096 output tokens and were cut off (Smart Suggestions 7 times in 12).
    // ═══════════════════════════════════════════════════════════════════════════

    private val GPT_6_LUNA = ModelEntry(id = "openai/gpt-6-luna", reasoning = ReasoningParam(effort = "low"), name = "GPT-6 Luna")
    private val GEMINI_38_FLASH = ModelEntry(id = "google/gemini-3.8-flash", reasoning = ReasoningParam(effort = "low"), name = "Gemini 3.8 Flash")
    private val GEMINI_31_FLASH_LITE = ModelEntry(id = "google/gemini-3.1-flash-lite", reasoning = ReasoningParam(enabled = false), name = "Gemini 3.1 Flash Lite")
    private val GEMINI_35_FLASH_LITE = ModelEntry(id = "google/gemini-3.5-flash-lite", reasoning = ReasoningParam(effort = "minimal"), name = "Gemini 3.5 Flash Lite")

    // Free models, for accounts without credit only. No level: they reason as the feature asks.
    // Oct 2026: nvidia/nemotron-3-super-120b-a12b:free was in the writing and planning lists;
    // OpenRouter no longer lists it.
    private val GEMMA_4_26B_FREE = ModelEntry(id = "google/gemma-4-26b-a4b-it:free", name = "Gemma 4 26B (free)")
    private val GEMMA_4_31B_FREE = ModelEntry(id = "google/gemma-4-31b-it:free", name = "Gemma 4 31B (free)")
    private val NEMOTRON_3_NANO_OMNI_FREE = ModelEntry(id = "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free", name = "Nemotron 3 Nano Omni (free)")

    /**
     * With credit: paid models only. Free models were the last backups here until Oct 2026, but
     * their shared capacity was "rate-limited upstream" on every try of the 29 Oct test.
     */
    private val WITH_CREDIT: Map<AiJob, List<ModelEntry>> = mapOf(
        AiJob.HANDWRITING to listOf(GPT_6_LUNA, GEMINI_31_FLASH_LITE, GEMINI_38_FLASH),
        // GPT-6 Luna doesn't take audio
        AiJob.VOICE to listOf(GEMINI_31_FLASH_LITE, GEMINI_35_FLASH_LITE),
        AiJob.WRITING to listOf(GPT_6_LUNA, GEMINI_31_FLASH_LITE, GEMINI_38_FLASH),
        AiJob.PLANNING to listOf(GEMINI_38_FLASH, GPT_6_LUNA, GEMINI_31_FLASH_LITE),
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
        AiJob.WRITING to listOf(GEMMA_4_31B_FREE, GEMMA_4_26B_FREE),
        AiJob.PLANNING to listOf(GEMMA_4_31B_FREE, GEMMA_4_26B_FREE),
        AiJob.SEARCH_INDEXING to listOf(GEMINI_31_FLASH_LITE)
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Models for [job] in the order to try: the user's pick, then BooxDay's choice. Models that
     * OpenRouter's recent list no longer has, or that the account's settings block, are skipped
     * (OpenRouterModelCatalog), and a pick that can't do the job ([pickProblem]); BooxDay's choice
     * is never skipped down to nothing.
     */
    fun getChain(job: AiJob): List<AiModel> {
        val pick = userModel(job)?.takeIf { pickProblem(job, it.id) == null }
        return (listOfNotNull(pick) + usableDefaults(job)).distinctBy { it.id }
    }

    /** Why a saved pick is not used (the AI Models card says which). */
    enum class PickProblem {
        /** OpenRouter no longer lists it */
        RETIRED,
        /** The account's settings (privacy, allowed providers) block it */
        BLOCKED,
        /** It can't do the job's requests (OpenRouterModelCatalog.canDo: picture or audio, exact JSON, room) */
        CANT_DO_JOB
    }

    /** Why the pick [modelId] can't do [job] now, or null when it can, as far as the saved list knows. */
    fun pickProblem(job: AiJob, modelId: String): PickProblem? {
        val listed = OpenRouterModelCatalog.listedIds()
        val allowed = OpenRouterModelCatalog.allowedIds()
        return when {
            listed != null && modelId !in listed -> PickProblem.RETIRED
            allowed != null && modelId !in allowed -> PickProblem.BLOCKED
            OpenRouterModelCatalog.model(modelId)?.let { !OpenRouterModelCatalog.canDo(job, it) } == true -> PickProblem.CANT_DO_JOB
            else -> null
        }
    }

    private fun usableDefaults(job: AiJob): List<AiModel> {
        val all = defaultChain(job, OpenRouterAccount.isFreeOnly())
        return all.filter { isUsable(it.id) }.ifEmpty { all }.map { toAiModel(it, job) }
    }

    /** Listed by OpenRouter and allowed for the account, as far as the saved list knows. */
    private fun isUsable(modelId: String): Boolean {
        val listed = OpenRouterModelCatalog.listedIds()
        val allowed = OpenRouterModelCatalog.allowedIds()
        return (listed == null || modelId in listed) && (allowed == null || modelId in allowed)
    }

    /** BooxDay's choice for [job]; [freeOnly] = the free set. */
    fun defaultChain(job: AiJob, freeOnly: Boolean): List<ModelEntry> =
        (if (freeOnly) NO_CREDIT else WITH_CREDIT).getValue(job)

    /** The first of BooxDay's models for [job] right now (the model list's pinned row). */
    fun defaultModel(job: AiJob): AiModel = usableDefaults(job).first()

    /**
     * The model a job tries first right now (AI Models card). [waitsForCredit]: the account has no
     * credit and the job has no free model (search indexing), so it waits for credit.
     * [unavailablePick]: the user's pick when it is not used, and [unavailableReason] why.
     */
    data class InUse(
        val name: String,
        val isUserChoice: Boolean,
        val waitsForCredit: Boolean,
        val unavailablePick: String? = null,
        val unavailableReason: PickProblem? = null
    )

    fun modelInUse(job: AiJob): InUse {
        val first = getChain(job).first()
        val picked = (first as? AiModel.Custom)?.userChoice == true
        val waits = !picked && OpenRouterAccount.isFreeOnly() && !OpenRouterAccount.isFreeModel(first.id)
        val unavailable = if (picked) null else userModel(job)
        return InUse(first.displayName, picked, waits, unavailable?.displayName, unavailable?.let { pickProblem(job, it.id) })
    }

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
            reasoning = AiModelSettings.reasoningConfig(choice.reasoning, choice.reasoningOptions),
            job = job
        )
    }

    fun getVisionFallbackChain(): List<AiModel> = getChain(AiJob.HANDWRITING)

    fun getAudioFallbackChain(): List<AiModel> = getChain(AiJob.VOICE)

    fun getTextFallbackChain(): List<AiModel> = getChain(AiJob.WRITING)

    fun getReasoningFallbackChain(): List<AiModel> = getChain(AiJob.PLANNING)

    /**
     * Convert a ModelEntry's reasoning param to ReasoningConfig for API requests (sent whatever
     * the feature asks for since 29 Oct 2026; AiClient.buildApiRequest).
     *
     * - {"effort": "low"} → ReasoningConfig.withEffort("low")
     * - {"enabled": true} → ReasoningConfig.enabled()
     * - {"enabled": false} → ReasoningConfig.disabled()
     */
    fun toReasoningConfig(entry: ModelEntry): ReasoningConfig? {
        val param = entry.reasoning ?: return null
        return when {
            param.effort != null -> ReasoningConfig.withEffort(param.effort)
            param.enabled == true -> ReasoningConfig.enabled()
            param.enabled == false -> ReasoningConfig.disabled()
            else -> null
        }
    }

    /**
     * One of BooxDay's models as an AiModel: a custom model with the job's capabilities and its
     * tested reasoning level. Never one of the old built-in models (GPT-5 Nano was, until Oct
     * 2026), which carry no level.
     */
    private fun toAiModel(entry: ModelEntry, job: AiJob): AiModel {
        return AiModel.Custom(
            id = entry.id,
            displayName = entry.name.ifBlank { entry.id.substringAfterLast("/").substringBefore(":") },
            capabilities = job.capabilities,
            defaultConfig = AiConfig(temperature = job.temperature, maxTokens = job.defaultMaxTokens),
            reasoning = toReasoningConfig(entry)
        )
    }
}

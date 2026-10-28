package com.boox.day.api

import com.boox.day.api.ai.AiCapability
import com.boox.day.api.ai.AiModel
import com.boox.day.data.datastore.AiModelChoice
import com.boox.day.data.datastore.AiModelChoices
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.datastore.GlobalAppSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The models each AI job tries: the user's pick first, then BooxDay's choice. */
class ModelConfigProviderTest {

    private lateinit var savedSettings: AppSettings

    @Before
    fun setUp() {
        savedSettings = GlobalAppSettings.current
        OpenRouterAccount.onPaidRequestSucceeded()
    }

    @After
    fun tearDown() {
        GlobalAppSettings.update(savedSettings)
        OpenRouterAccount.onPaidRequestSucceeded()
        OpenRouterModelCatalog.setSnapshotForTest(null)
    }

    /** A saved model list with just these ids, downloaded [ageMs] ago. */
    private fun listed(vararg ids: String, allowed: List<String>? = null, ageMs: Long = 0L) {
        OpenRouterModelCatalog.setSnapshotForTest(
            CatalogSnapshot(
                fetchedAtMs = System.currentTimeMillis() - ageMs,
                models = ids.mapIndexed { i, id -> CatalogModel(id = id, name = id, lab = "", rank = i) },
                allowedIds = allowed
            )
        )
    }

    @Test
    fun `models OpenRouter no longer lists are skipped`() {
        // Gemini 3 Flash Preview retired: planning starts with the next model
        listed("nvidia/nemotron-3-super-120b-a12b:free", "google/gemma-4-31b-it:free", "openai/gpt-5-nano")
        assertEquals(
            listOf("nvidia/nemotron-3-super-120b-a12b:free", "google/gemma-4-31b-it:free"),
            ModelConfigProvider.getReasoningFallbackChain().map { it.id }
        )
        // A list older than two weeks is not trusted to say a model is gone
        listed("openai/gpt-5-nano", ageMs = 15L * 24 * 60 * 60 * 1000)
        assertEquals("google/gemini-3-flash-preview", ModelConfigProvider.getReasoningFallbackChain().first().id)
    }

    @Test
    fun `models the account blocks are skipped, but never down to nothing`() {
        listed(
            "nvidia/nemotron-3-super-120b-a12b:free", "google/gemma-4-31b-it:free", "google/gemini-3-flash-preview",
            allowed = listOf("google/gemma-4-31b-it:free")
        )
        OpenRouterAccount.onPaidRequestRefused()
        assertEquals(listOf("google/gemma-4-31b-it:free"), ModelConfigProvider.getReasoningFallbackChain().map { it.id })
        // Nothing of BooxDay's allowed: keep the list rather than send no request at all
        listed("x/other", allowed = listOf("x/other"))
        assertEquals(2, ModelConfigProvider.getReasoningFallbackChain().size)
    }

    @Test
    fun `a pick OpenRouter dropped is skipped and the card says so`() {
        pick(AiJob.WRITING, AiModelChoice(id = "old/retired-model", name = "Retired Model"))
        listed("openai/gpt-5-nano", "google/gemini-3.1-flash-lite", "nvidia/nemotron-3-super-120b-a12b:free")
        assertEquals("openai/gpt-5-nano", ModelConfigProvider.getTextFallbackChain().first().id)
        val inUse = ModelConfigProvider.modelInUse(AiJob.WRITING)
        assertFalse(inUse.isUserChoice)
        assertEquals("Retired Model", inUse.unavailablePick)
        assertEquals(ModelConfigProvider.PickProblem.RETIRED, inUse.unavailableReason)
        assertEquals("GPT-5 Nano", ModelConfigProvider.defaultModel(AiJob.WRITING).displayName)
    }

    /** A model OpenRouter lists that can do every job. */
    private fun able(id: String, rank: Int) = CatalogModel(
        id = id, name = id, lab = "", rank = rank, inputs = listOf("text", "image", "audio"),
        supportsJson = true, supportsJsonSchema = true, supportsTemperature = true, supportsMaxTokens = true,
        contextLength = 1_000_000, zdr = true
    )

    @Test
    fun `a pick the account's privacy settings block is skipped, and the card says why`() {
        // Go 10.3, 27 Oct 2026: "Free model training violation (account settings)"
        pick(AiJob.HANDWRITING, AiModelChoice(id = "nvidia/nemotron-3.5-super-vl:free", name = "Nemotron 3.5 Super VL (free)"))
        OpenRouterModelCatalog.setSnapshotForTest(CatalogSnapshot(
            System.currentTimeMillis(), listOf(able("nvidia/nemotron-3.5-super-vl:free", 0), able("openai/gpt-5-nano", 1)),
            allowedIds = listOf("openai/gpt-5-nano")
        ))
        assertEquals("openai/gpt-5-nano", ModelConfigProvider.getVisionFallbackChain().first().id)
        assertEquals(ModelConfigProvider.PickProblem.BLOCKED, ModelConfigProvider.modelInUse(AiJob.HANDWRITING).unavailableReason)
    }

    @Test
    fun `a pick that can't do the job is skipped, one that can comes first`() {
        pick(AiJob.WRITING, AiModelChoice(id = "x/json-mode-only", name = "JSON Mode Only"))
        OpenRouterModelCatalog.setSnapshotForTest(CatalogSnapshot(
            System.currentTimeMillis(), listOf(able("x/json-mode-only", 0).copy(supportsJsonSchema = false), able("x/able", 1))
        ))
        assertFalse("x/json-mode-only" in ModelConfigProvider.getTextFallbackChain().map { it.id })
        assertEquals(ModelConfigProvider.PickProblem.CANT_DO_JOB, ModelConfigProvider.modelInUse(AiJob.WRITING).unavailableReason)

        pick(AiJob.WRITING, AiModelChoice(id = "x/able", name = "Able"))
        assertEquals("x/able", ModelConfigProvider.getTextFallbackChain().first().id)
        assertNull(ModelConfigProvider.modelInUse(AiJob.WRITING).unavailablePick)
    }

    private fun pick(job: AiJob, choice: AiModelChoice?) {
        GlobalAppSettings.update(
            GlobalAppSettings.current.copy(aiModels = AiModelChoices().withJob(job, choice))
        )
    }

    @Test
    fun `without a pick every job uses BooxDay's choice`() {
        pick(AiJob.WRITING, null)
        assertEquals(
            listOf("openai/gpt-5-nano", "google/gemini-3.1-flash-lite", "nvidia/nemotron-3-super-120b-a12b:free"),
            ModelConfigProvider.getTextFallbackChain().map { it.id }
        )
        for (job in AiJob.entries) {
            assertTrue("$job has models", ModelConfigProvider.getChain(job).isNotEmpty())
        }
    }

    @Test
    fun `the retired voice model is gone and voice has a backup`() {
        val voice = ModelConfigProvider.getAudioFallbackChain()
        assertFalse("google/gemini-2.0-flash-lite-001" in voice.map { it.id })
        assertEquals(2, voice.size)
        assertTrue(voice.all { AiCapability.AUDIO in it.capabilities })
    }

    @Test
    fun `no credit uses free models, except search indexing which keeps its private model`() {
        OpenRouterAccount.onPaidRequestRefused()
        for (job in listOf(AiJob.HANDWRITING, AiJob.VOICE, AiJob.WRITING, AiJob.PLANNING)) {
            val ids = ModelConfigProvider.getChain(job).map { it.id }
            assertTrue("$job: $ids", ids.isNotEmpty() && ids.all { OpenRouterAccount.isFreeModel(it) })
        }
        assertEquals(listOf("google/gemini-3.1-flash-lite"), ModelConfigProvider.getChain(AiJob.SEARCH_INDEXING).map { it.id })
    }

    @Test
    fun `a pick comes first, with the user's settings, and is not repeated`() {
        pick(
            AiJob.WRITING,
            AiModelChoice(
                id = "google/gemini-3.1-flash-lite",
                name = "Gemini 3.1 Flash Lite",
                lab = "Google",
                reasoning = "low",
                reasoningOptions = ReasoningOptions(true, false, listOf("high", "medium", "low", "minimal", "none")),
                modelMaxOutputTokens = 65_536
            )
        )
        val chain = ModelConfigProvider.getTextFallbackChain()
        assertEquals(
            listOf("google/gemini-3.1-flash-lite", "openai/gpt-5-nano", "nvidia/nemotron-3-super-120b-a12b:free"),
            chain.map { it.id }
        )
        val picked = chain.first() as AiModel.Custom
        assertTrue(picked.userChoice)
        assertEquals(ReasoningConfig.withEffort("low"), picked.reasoning)
        assertEquals(5_000, picked.defaultConfig.maxTokens)
        assertEquals("Gemini 3.1 Flash Lite", picked.displayName)
    }

    @Test
    fun `a pick that matches an old built-in model gets the job's capabilities`() {
        // GPT-5 Nano is a built-in AiModel; as a planning pick it must still be able to reason
        // (BooxNoteIndexer calls enableReasoning(), which requires REASONING)
        val picked = ModelConfigProvider.userModel(AiJob.PLANNING, AiModelChoice(id = "openai/gpt-5-nano"))!!
        assertEquals(AiJob.PLANNING.capabilities, picked.capabilities)
        assertTrue(picked.userChoice)
    }

    @Test
    fun `a saved output limit wins over the recommendation`() {
        val picked = ModelConfigProvider.userModel(
            AiJob.WRITING,
            AiModelChoice(id = "anthropic/claude-haiku-5.5", reasoning = "high", maxOutputTokens = 12_000,
                reasoningOptions = ReasoningOptions(true, false, listOf("max", "high", "low", "none")))
        )!!
        assertEquals(12_000, picked.defaultConfig.maxTokens)
    }

    @Test
    fun `a pick this job can't use is ignored`() {
        assertNull(ModelConfigProvider.userModel(AiJob.VOICE, AiModelChoice(id = "openai/gpt-5-nano", inputs = listOf("text", "image"))))
        assertNull(ModelConfigProvider.userModel(AiJob.WRITING, AiModelChoice(id = "  ")))
        pick(AiJob.VOICE, AiModelChoice(id = "openai/gpt-5-nano", inputs = listOf("text", "image")))
        assertFalse("openai/gpt-5-nano" in ModelConfigProvider.getAudioFallbackChain().map { it.id })
    }

    @Test
    fun `the AI Models card shows the model each job uses first`() {
        pick(AiJob.WRITING, AiModelChoice(id = "anthropic/claude-haiku-5.5", name = "Claude Haiku 5.5", lab = "Anthropic"))
        assertEquals(ModelConfigProvider.InUse("GPT-5 Nano", false, false), ModelConfigProvider.modelInUse(AiJob.HANDWRITING))
        assertEquals(ModelConfigProvider.InUse("Claude Haiku 5.5", true, false), ModelConfigProvider.modelInUse(AiJob.WRITING))
        assertEquals("Gemini 3 Flash Preview", ModelConfigProvider.modelInUse(AiJob.PLANNING).name)

        // No credit: free models by name; search indexing has none and waits
        OpenRouterAccount.onPaidRequestRefused()
        assertEquals(ModelConfigProvider.InUse("Nemotron 3 Nano Omni (free)", false, false), ModelConfigProvider.modelInUse(AiJob.VOICE))
        assertEquals(ModelConfigProvider.InUse("Gemini 3.1 Flash Lite", false, true), ModelConfigProvider.modelInUse(AiJob.SEARCH_INDEXING))
        // A pick still comes first
        assertTrue(ModelConfigProvider.modelInUse(AiJob.WRITING).isUserChoice)
    }

    @Test
    fun `a level the model no longer offers sends nothing`() {
        val picked = ModelConfigProvider.userModel(
            AiJob.PLANNING,
            AiModelChoice(id = "z-ai/glm-5.3-flash", reasoning = "medium",
                reasoningOptions = ReasoningOptions(true, true, listOf("max", "high", "low")))
        )!!
        assertTrue(picked.userChoice)
        assertNull(picked.reasoning)
    }
}

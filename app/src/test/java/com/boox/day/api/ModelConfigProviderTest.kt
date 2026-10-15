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

/** The models each AI job tries: the user's pick first, then Boox Day's choice. */
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
    }

    private fun pick(job: AiJob, choice: AiModelChoice?) {
        GlobalAppSettings.update(
            GlobalAppSettings.current.copy(aiModels = AiModelChoices().withJob(job, choice))
        )
    }

    @Test
    fun `without a pick every job uses Boox Day's choice`() {
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

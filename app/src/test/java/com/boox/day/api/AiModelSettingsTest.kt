package com.boox.day.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reasoning choices and output tokens for a picked model, from real OpenRouter model data
 * (models list, 15 Oct 2026).
 */
class AiModelSettingsTest {

    private fun reasoningOf(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    private val withReasoning = listOf("max_tokens", "reasoning", "response_format")

    // GPT-5 Nano: mandatory, minimal to high
    private val gpt5Nano = ReasoningOptions.fromOpenRouter(
        withReasoning,
        reasoningOf("""{"mandatory": true, "supported_efforts": ["high", "medium", "low", "minimal"], "default_effort": "medium"}""")
    )

    // GLM 5.3 Flash: mandatory, only max, high and low
    private val glm53Flash = ReasoningOptions.fromOpenRouter(
        withReasoning,
        reasoningOf("""{"mandatory": true, "default_enabled": true, "supported_efforts": ["max", "high", "low"], "default_effort": "max"}""")
    )

    // Gemini 3.1 Flash Lite: can turn off, none to high
    private val geminiFlashLite = ReasoningOptions.fromOpenRouter(
        withReasoning,
        reasoningOf("""{"mandatory": false, "default_enabled": true, "supported_efforts": ["high", "medium", "low", "minimal", "none"], "default_effort": "minimal"}""")
    )

    // Claude Haiku 5.5: can turn off, none to max
    private val claudeHaiku = ReasoningOptions.fromOpenRouter(
        withReasoning,
        reasoningOf("""{"mandatory": false, "supported_efforts": ["max", "xhigh", "high", "medium", "low", "none"], "default_effort": "medium"}""")
    )

    // MiMo V2.6 Flash: reasoning without effort levels
    private val onOffOnly = ReasoningOptions.fromOpenRouter(withReasoning, reasoningOf("""{"mandatory": false}"""))

    @Test
    fun `reads OpenRouter's reasoning object`() {
        assertEquals(ReasoningOptions(true, true, listOf("high", "medium", "low", "minimal")), gpt5Nano)
        assertEquals(ReasoningOptions(true, false, null), onOffOnly)
        // supported_efforts: null means every level OpenRouter accepts
        val nullLevels = ReasoningOptions.fromOpenRouter(withReasoning, reasoningOf("""{"supported_efforts": null}"""))
        assertEquals(AiModelSettings.LEVELS, AiModelSettings.reasoningChoices(nullLevels))
        // No reasoning parameter: no control, even when OpenRouter lists an object
        val noParam = ReasoningOptions.fromOpenRouter(listOf("max_tokens"), reasoningOf("""{"mandatory": false}"""))
        assertFalse(noParam.supported)
        assertEquals(emptyList<String>(), AiModelSettings.reasoningChoices(noParam))
    }

    @Test
    fun `choices are the model's own levels, low to high`() {
        assertEquals(listOf("minimal", "low", "medium", "high"), AiModelSettings.reasoningChoices(gpt5Nano))
        assertEquals(listOf("low", "high", "max"), AiModelSettings.reasoningChoices(glm53Flash))
        assertEquals(listOf("none", "minimal", "low", "medium", "high"), AiModelSettings.reasoningChoices(geminiFlashLite))
        assertEquals(listOf("none", "low", "medium", "high", "xhigh", "max"), AiModelSettings.reasoningChoices(claudeHaiku))
        assertEquals(listOf(AiModelSettings.OFF, AiModelSettings.ON), AiModelSettings.reasoningChoices(onOffOnly))
    }

    @Test
    fun `mandatory models never get Off or none`() {
        val mandatoryWithNone = ReasoningOptions(true, true, listOf("high", "low", "none"))
        val choices = AiModelSettings.reasoningChoices(mandatoryWithNone)
        assertFalse("none" in choices)
        assertFalse(AiModelSettings.OFF in choices)
        // Mandatory without levels: always reasons, nothing to choose
        assertEquals(emptyList<String>(), AiModelSettings.reasoningChoices(ReasoningOptions(true, true, null)))
        assertNull(AiModelSettings.reasoningConfig("none", mandatoryWithNone))
    }

    @Test
    fun `models that can turn off without a none level get Off`() {
        val canTurnOff = ReasoningOptions(true, false, listOf("high", "medium", "low"))
        assertEquals(listOf(AiModelSettings.OFF, "low", "medium", "high"), AiModelSettings.reasoningChoices(canTurnOff))
        assertEquals(ReasoningConfig.disabled(), AiModelSettings.reasoningConfig(AiModelSettings.OFF, canTurnOff))
    }

    @Test
    fun `newer level names are kept in OpenRouter's order`() {
        val withNewLevel = ReasoningOptions(true, false, listOf("ultra", "high", "low", "none"))
        assertEquals(listOf("none", "low", "high", "ultra"), AiModelSettings.reasoningChoices(withNewLevel))
    }

    @Test
    fun `recommended level is the job's, or the nearest the model has`() {
        assertEquals("low", AiModelSettings.recommendedReasoning(AiJob.HANDWRITING, geminiFlashLite))
        assertEquals("medium", AiModelSettings.recommendedReasoning(AiJob.PLANNING, claudeHaiku))
        assertEquals("none", AiModelSettings.recommendedReasoning(AiJob.VOICE, geminiFlashLite))
        // Off is not allowed: the lowest level
        assertEquals("minimal", AiModelSettings.recommendedReasoning(AiJob.SEARCH_INDEXING, gpt5Nano))
        // Medium missing: low and high are equally near, the lower one wins
        assertEquals("low", AiModelSettings.recommendedReasoning(AiJob.PLANNING, glm53Flash))
        assertEquals(AiModelSettings.OFF, AiModelSettings.recommendedReasoning(AiJob.VOICE, onOffOnly))
        assertEquals(AiModelSettings.ON, AiModelSettings.recommendedReasoning(AiJob.PLANNING, onOffOnly))
        assertNull(AiModelSettings.recommendedReasoning(AiJob.PLANNING, ReasoningOptions()))
    }

    @Test
    fun `reasoning sent for a saved level`() {
        assertEquals(ReasoningConfig.withEffort("low"), AiModelSettings.reasoningConfig("low", gpt5Nano))
        assertEquals(ReasoningConfig.withEffort("none"), AiModelSettings.reasoningConfig("none", geminiFlashLite))
        assertEquals(ReasoningConfig.enabled(), AiModelSettings.reasoningConfig(AiModelSettings.ON, onOffOnly))
        // A level the model no longer offers: nothing is sent, the model uses its own default
        assertNull(AiModelSettings.reasoningConfig("xhigh", gpt5Nano))
        assertNull(AiModelSettings.reasoningConfig(null, gpt5Nano))
    }

    @Test
    fun `recommended output tokens leave room for reasoning`() {
        fun tokens(job: AiJob, level: String?, options: ReasoningOptions = claudeHaiku, max: Int? = 128_000) =
            AiModelSettings.recommendedMaxOutputTokens(job, level, options, max)

        // A 4,000-token job (the plan's example)
        assertEquals(4_000, tokens(AiJob.WRITING, "none"))
        assertEquals(5_000, tokens(AiJob.WRITING, "minimal", gpt5Nano))
        assertEquals(5_000, tokens(AiJob.WRITING, "low"))
        assertEquals(8_000, tokens(AiJob.WRITING, "medium"))
        assertEquals(20_000, tokens(AiJob.WRITING, "high"))
        assertEquals(64_000, tokens(AiJob.WRITING, "max"))
        // Planning answers are longer; search indexing shorter
        assertEquals(16_000, tokens(AiJob.PLANNING, "medium"))
        assertEquals(3_000, tokens(AiJob.SEARCH_INDEXING, "none"))
        // Never above the model's own limit
        assertEquals(8_192, tokens(AiJob.WRITING, "high", max = 8_192))
        // No reasoning: just the answer room; "always reasons" without levels counts as medium
        assertEquals(4_000, tokens(AiJob.WRITING, null, ReasoningOptions()))
        assertEquals(8_000, tokens(AiJob.WRITING, null, ReasoningOptions(true, true, null)))
        assertEquals(8_000, tokens(AiJob.WRITING, AiModelSettings.ON, onOffOnly))
    }

    @Test
    fun `output token steps reach the model's limit and always include the recommendation`() {
        assertEquals(
            listOf(1_000, 2_000, 3_000, 4_000, 5_000, 6_000, 8_000, 12_000, 16_000, 24_000, 32_000, 48_000, 64_000, 65_536),
            AiModelSettings.outputTokenSteps(modelMaxOutput = 65_536, recommended = 5_000)
        )
        val withTwenty = AiModelSettings.outputTokenSteps(128_000, 20_000)
        assertTrue(20_000 in withTwenty)
        assertEquals(withTwenty.sorted(), withTwenty)
        assertEquals(listOf(1_000, 2_000, 3_000, 4_000, 5_000, 6_000, 8_000, 8_192), AiModelSettings.outputTokenSteps(8_192, 8_192))
        // Limit not known: up to 128,000
        assertEquals(128_000, AiModelSettings.outputTokenSteps(null, 4_000).last())
        // A model that can only write 800 tokens
        assertEquals(listOf(800), AiModelSettings.outputTokenSteps(800, 800))
    }

    @Test
    fun `minus and plus move one step and stop at the ends`() {
        val steps = AiModelSettings.outputTokenSteps(65_536, 5_000)
        assertEquals(6_000, AiModelSettings.stepOutputTokens(steps, 5_000, up = true))
        assertEquals(4_000, AiModelSettings.stepOutputTokens(steps, 5_000, up = false))
        assertEquals(65_536, AiModelSettings.stepOutputTokens(steps, 65_536, up = true))
        assertEquals(1_000, AiModelSettings.stepOutputTokens(steps, 1_000, up = false))
        // A saved value between steps moves to the neighbouring steps
        assertEquals(8_000, AiModelSettings.stepOutputTokens(steps, 7_000, up = true))
        assertEquals(6_000, AiModelSettings.stepOutputTokens(steps, 7_000, up = false))
    }

    @Test
    fun `no level is offered twice`() {
        val repeated = ReasoningOptions(true, false, listOf("high", "high", "low"))
        val choices = AiModelSettings.reasoningChoices(repeated)
        assertEquals(choices.distinct(), choices)
        assertTrue("low" in choices && "high" in choices)
    }
}

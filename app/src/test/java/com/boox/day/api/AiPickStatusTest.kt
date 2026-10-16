package com.boox.day.api

import com.boox.day.api.ai.AiConfig
import com.boox.day.api.ai.AiModel
import com.boox.day.api.ai.AiResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The failure note (plan step f): what counts, and when it clears. */
class AiPickStatusTest {

    private fun pick(id: String, job: AiJob) = AiModel.Custom(
        id = id, displayName = id, capabilities = job.capabilities, defaultConfig = AiConfig(),
        userChoice = true, job = job
    )

    @After
    fun tearDown() {
        AiJob.entries.forEach { AiPickStatus.clear(it) }
    }

    @Test
    fun `the model's own failures count, with a short reason`() {
        assertEquals("model not found", AiPickStatus.reasonFor(AiResponse.Error.ApiError(404, "No such model")))
        assertEquals(
            "blocked by your OpenRouter settings",
            AiPickStatus.reasonFor(AiResponse.Error.ApiError(404, "No endpoints found matching your data policy"))
        )
        assertEquals("request not accepted", AiPickStatus.reasonFor(AiResponse.Error.InvalidRequest("bad")))
        assertEquals(
            "answer cut off",
            AiPickStatus.reasonFor(AiResponse.Error.NoContent("The answer was cut off: the model used all its output tokens before answering."))
        )
        assertEquals("empty answer", AiPickStatus.reasonFor(AiResponse.Error.NoContent()))
        assertEquals("needs credit", AiPickStatus.reasonFor(AiResponse.Error.OutOfCredit()))
    }

    @Test
    fun `no internet, key problems and busy providers don't count`() {
        assertNull(AiPickStatus.reasonFor(AiResponse.Error.NetworkError("offline", null)))
        assertNull(AiPickStatus.reasonFor(AiResponse.Error.NoApiKey()))
        assertNull(AiPickStatus.reasonFor(AiResponse.Error.KeyRejected()))
        assertNull(AiPickStatus.reasonFor(AiResponse.Error.RateLimitError("busy", null)))
        assertNull(AiPickStatus.reasonFor(AiResponse.Error.ApiError(502, "provider down")))
        assertNull(AiPickStatus.reasonFor(AiResponse.Error.OutOfCredit.from("openrouter_in_flight_budget", null)))
    }

    @Test
    fun `a failure shows until the pick answers`() {
        val model = pick("anthropic/claude-haiku-5.5", AiJob.WRITING)
        AiPickStatus.noteResult(model, AiResponse.Error.ApiError(404, "No such model"))
        assertEquals("model not found", AiPickStatus.failureFor(AiJob.WRITING, model.id)?.reason)
        // Not shown for another job, or once the job has another pick
        assertNull(AiPickStatus.failureFor(AiJob.PLANNING, model.id))
        assertNull(AiPickStatus.failureFor(AiJob.WRITING, "openai/gpt-5-nano"))
        // A network error doesn't clear or change it
        AiPickStatus.noteResult(model, AiResponse.Error.NetworkError("offline", null))
        assertNotNull(AiPickStatus.failureFor(AiJob.WRITING, model.id))
        // An answer clears it
        AiPickStatus.noteResult(model, AiResponse.Success("ok", model.id, null))
        assertNull(AiPickStatus.failureFor(AiJob.WRITING, model.id))
    }

    @Test
    fun `an answer the feature can't use counts`() {
        val model = pick("x/model", AiJob.HANDWRITING)
        AiPickStatus.noteUnusableAnswer(model)
        assertEquals("unusable answer", AiPickStatus.failureFor(AiJob.HANDWRITING, "x/model")?.reason)
    }

    @Test
    fun `Boox Day's own models never set it`() {
        val booxDay = AiModel.Custom("google/gemini-3.1-flash-lite", "Gemini", AiJob.VOICE.capabilities, AiConfig())
        AiPickStatus.noteResult(booxDay, AiResponse.Error.ApiError(404, "gone"))
        assertEquals(emptyMap<AiJob, AiPickStatus.Failure>(), AiPickStatus.failures)
    }
}

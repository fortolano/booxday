package com.boox.day.api

import android.app.Application
import android.util.Base64
import com.boox.day.api.ai.AiMessage
import com.boox.day.api.ai.AiProblem
import com.boox.day.api.ai.AiResponse
import com.boox.day.data.datastore.AiModelChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The check on Use Model (Oct 2026): one small request built like the job's features build theirs,
 * read like they read the answer, and a plain reason when it fails. Robolectric for the picture,
 * Base64 and org.json.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AiModelCheckTest {

    private fun model(job: AiJob) = ModelConfigProvider.userModel(job, AiModelChoice(id = "x/model"))!!

    @Test
    fun `each job's check sends what its features send`() {
        val handwriting = AiModelCheck.requestFor(AiJob.HANDWRITING, model(AiJob.HANDWRITING))
        assertTrue(handwriting.messages.any { it is AiMessage.UserWithImage })
        assertEquals("ocr_text", handwriting.jsonSchema?.name)

        val indexing = AiModelCheck.requestFor(AiJob.SEARCH_INDEXING, model(AiJob.SEARCH_INDEXING))
        assertTrue(indexing.zeroDataRetention)
        assertEquals(ReasoningConfig.disabled(), indexing.overrideReasoning)
        assertEquals(0f, indexing.config.temperature)

        val voice = AiModelCheck.requestFor(AiJob.VOICE, model(AiJob.VOICE))
        val audio = voice.messages.filterIsInstance<AiMessage.UserWithAudio>().single()
        assertEquals("RIFF", String(Base64.decode(audio.audioBase64, Base64.DEFAULT), 0, 4))

        assertEquals("memo_to_tasks", AiModelCheck.requestFor(AiJob.WRITING, model(AiJob.WRITING)).jsonSchema?.name)
        assertEquals("task_breakdown_v3", AiModelCheck.requestFor(AiJob.PLANNING, model(AiJob.PLANNING)).jsonSchema?.name)
    }

    @Test
    fun `an answer counts only when the job's features could use it`() {
        assertTrue(AiModelCheck.answerFits(AiJob.HANDWRITING, """{"hasText": true, "text": "Buy milk"}"""))
        assertFalse(AiModelCheck.answerFits(AiJob.HANDWRITING, """{"hasText": false, "text": ""}"""))
        assertFalse(AiModelCheck.answerFits(AiJob.SEARCH_INDEXING, "Buy milk")) // not the format
        assertTrue(AiModelCheck.answerFits(AiJob.WRITING, """{"tasks": [{"text": "Call the dentist", "suggestedPriority": ""}]}"""))
        assertFalse(AiModelCheck.answerFits(AiJob.WRITING, """{"tasks": []}"""))
        assertTrue(AiModelCheck.answerFits(AiJob.PLANNING, """{"subtasks": [{"text": "a"}, {"text": "b"}, {"text": "c"}], "keepOriginal": false}"""))
        assertFalse(AiModelCheck.answerFits(AiJob.PLANNING, """{"subtasks": [{"text": "a"}]}"""))
        assertTrue(AiModelCheck.answerFits(AiJob.VOICE, "OK"))
        assertFalse(AiModelCheck.answerFits(AiJob.VOICE, " "))
    }

    @Test
    fun `failures in plain words`() {
        // OpenRouter's answer for the Go 10.3's Handwriting pick, 27 Oct 2026
        val blocked = AiResponse.Error.ApiError(404, """{"error":{"message":"0 endpoints out of 1 requested are available matching your guardrail restrictions and data policy."}}""")
        assertTrue(AiModelCheck.reasonFor(blocked).startsWith("Your OpenRouter privacy settings block this model."))
        val noProvider = AiResponse.Error.ApiError(404, """{"error":{"message":"No endpoints found that can handle the requested parameters."}}""")
        assertEquals("None of this model's providers can take this job's requests.", AiModelCheck.reasonFor(noProvider))
        assertEquals("OpenRouter no longer has this model.", AiModelCheck.reasonFor(AiResponse.Error.ApiError(404, "Not found")))
        assertTrue(AiModelCheck.reasonFor(AiResponse.Error.NoContent("The answer was cut off: ...")).contains("output tokens"))
        assertEquals(AiProblem.OPENROUTER_BUSY.message, AiModelCheck.reasonFor(AiResponse.Error.RateLimitError("busy", null)))
    }
}

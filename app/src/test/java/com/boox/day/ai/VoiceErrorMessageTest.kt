package com.boox.day.ai

import android.app.Application
import com.boox.day.api.OpenRouterAccount
import com.boox.day.api.ai.AiProblem
import com.boox.day.api.ai.AiResponse
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.SocketTimeoutException

/**
 * Voice says what went wrong in plain words (Oct 2026): an OpenRouter timeout said "No internet
 * connection", and other errors showed OpenRouter's raw HTTP answer. TranscriptionService (voice
 * to task, event, text) builds its message the same way.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class VoiceErrorMessageTest {

    private val originalSend = AiRetryExecutor.send

    @Before
    fun setUp() {
        OpenRouterAccount.onPaidRequestSucceeded()
    }

    @After
    fun tearDown() {
        AiRetryExecutor.send = originalSend
        OpenRouterAccount.onPaidRequestSucceeded()
    }

    private fun parseWith(answer: AiResponse<String>): String? = runBlocking {
        AiRetryExecutor.send = { _, _ -> answer }
        VoicePreferencesParser.parseAudioInput("UklGRg==").error
    }

    @Test
    fun `a timeout is OpenRouter not answering, not no internet`() {
        assertEquals(
            AiProblem.OPENROUTER_DOWN.message,
            parseWith(AiResponse.Error.NetworkError("Network error: timeout", SocketTimeoutException()))
        )
    }

    @Test
    fun `OpenRouter's raw error is not shown`() {
        assertEquals(
            AiProblem.OPENROUTER_DOWN.message,
            parseWith(AiResponse.Error.ApiError(502, "{\"error\":{\"message\":\"Provider returned error\"}}"))
        )
        assertEquals(AiProblem.MODEL_FAILED.message, parseWith(AiResponse.Error.InvalidRequest("{\"error\":\"bad audio\"}")))
    }

    @Test
    fun `busy and credit use the shared words`() {
        assertEquals(AiProblem.OPENROUTER_BUSY.message, parseWith(AiResponse.Error.RateLimitError("429", null)))
        assertEquals(AiResponse.Error.OutOfCredit.NO_CREDIT_MESSAGE, parseWith(AiResponse.Error.OutOfCredit()))
    }
}

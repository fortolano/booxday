package com.boox.day.ai

import android.app.Application
import com.boox.day.api.OpenRouterAccount
import com.boox.day.api.ai.AiProblem
import com.boox.day.api.ai.AiResponse
import com.boox.day.data.db.Stroke
import com.boox.day.data.db.StrokePoint
import com.boox.day.data.db.TextAnnotation
import com.boox.day.editor.utils.Pen
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.UnknownHostException
import java.util.Date

/**
 * Translate and Summarize say why handwriting was not read (Oct 2026). When reading failed they
 * said "No readable text found", even for a page full of writing with Wi-Fi off.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HandwritingReadFailureMessageTest {

    private val originalSend = AiRetryExecutor.send

    private val stroke = Stroke(
        size = 10f, pen = Pen.FOUNTAIN, maxPressure = 1024, top = 10f, bottom = 90f, left = 10f, right = 90f,
        points = listOf(StrokePoint(10f, 10f, 512f), StrokePoint(50f, 60f, 512f), StrokePoint(90f, 90f, 512f)),
        pageId = "page", createdAt = Date(1000), updatedAt = Date(1000)
    )

    @Before
    fun setUp() {
        OpenRouterAccount.onPaidRequestSucceeded()
        AiRetryExecutor.send = { _, _ -> AiResponse.Error.NetworkError("Network error", UnknownHostException()) }
    }

    @After
    fun tearDown() {
        AiRetryExecutor.send = originalSend
        OpenRouterAccount.onPaidRequestSucceeded()
    }

    // Unit tests have no network state, so a connection failure means OpenRouter can't be reached
    private val expected = AiProblem.OPENROUTER_UNREACHABLE.message

    @Test
    fun `translate says why the handwriting was not read`() = runBlocking {
        var shown: AiTranslationService.TranslationState? = null
        val result = AiTranslationService.translateContent(
            AiTranslationService.TranslationContent(listOf(stroke), emptyList())
        ) { shown = it }
        assertEquals(expected, result.errorMessage)
        assertEquals(AiTranslationService.TranslationState.Error(expected), shown)
    }

    @Test
    fun `summarize says why the handwriting was not read`() = runBlocking {
        var shown: AiSummarizationService.SummarizationState? = null
        // Handwriting plus an empty text box: nothing else to summarize
        val emptyBox = TextAnnotation(content = " ", x = 0f, y = 0f, width = 100f, height = 40f, pageId = "page")
        val result = AiSummarizationService.summarizeContent(
            AiSummarizationService.SummarizationContent(listOf(stroke), listOf(emptyBox))
        ) { shown = it }
        assertEquals(expected, result.errorMessage)
        assertEquals(AiSummarizationService.SummarizationState.Error(expected), shown)
    }
}

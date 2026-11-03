package com.boox.day.ai

import android.app.Application
import com.boox.day.api.OpenRouterAccount
import com.boox.day.api.ai.AiResponse
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The text clean-up never changes a number (2 Nov 2026): a memo's "top 3 goals for 24" came back
 * as "Top 3 goals for 2024", and Convert Memo to Tasks offered the made-up year.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TextInferenceNumbersTest {

    private val originalSend = AiRetryExecutor.send

    @Before
    fun setUp() {
        OpenRouterAccount.onPaidRequestSucceeded()
    }

    @After
    fun tearDown() {
        AiRetryExecutor.send = originalSend
    }

    @Test
    fun `a clean-up that adds a number keeps the user's own words`() {
        AiRetryExecutor.send = { request, _ ->
            AiResponse.Success("""{"refinedText":"Quarterly planning\n- Top 3 goals for 2024","confidence":0.9}""", request.model.id, null)
        }
        val raw = "Quarterly planning\n- top 3 goals for 24"
        val result = runBlocking { TextInferenceService.refineText(raw, InferenceContext.TASK) }
        assertEquals(raw, result.refinedText)

        // Time formats and numbers already written are still fine
        assertFalse(TextInferenceService.inventsNumber("Dentst apt 3pm tues", "Dentist appointment 3:00 PM Tuesday"))
        assertFalse(TextInferenceService.inventsNumber("budget 1500 for Q4", "Budget 1500 for Q4"))
        assertTrue(TextInferenceService.inventsNumber("goals for 24", "Goals for 2024"))
    }
}

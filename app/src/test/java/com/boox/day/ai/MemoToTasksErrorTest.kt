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
import java.net.UnknownHostException

/**
 * Memo to tasks says what went wrong (Oct 2026). It showed "All models failed to convert memo to
 * tasks" (or "All vision models failed to extract text") for every cause.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MemoToTasksErrorTest {

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

    private fun convert(): String? = runBlocking {
        MemoToTasksService.convertRecognizedTextToTasks("call the dentist tomorrow", onStageChange = {}).error
    }

    @Test
    fun `no connection says so`() {
        AiRetryExecutor.send = { _, _ -> AiResponse.Error.NetworkError("Network error", UnknownHostException()) }
        // Unit tests have no network state, so a connection failure means OpenRouter can't be reached
        assertEquals(AiProblem.OPENROUTER_UNREACHABLE.message, convert())
    }

    @Test
    fun `no credit says to add credit`() {
        AiRetryExecutor.send = { _, _ -> AiResponse.Error.OutOfCredit() }
        assertEquals(AiResponse.Error.OutOfCredit.NO_CREDIT_MESSAGE, convert())
    }

    @Test
    fun `a rejected key stops at once`() {
        var conversionRequests = 0
        AiRetryExecutor.send = { request, _ ->
            if (request.jsonSchema?.name == "memo_to_tasks") conversionRequests++
            AiResponse.Error.KeyRejected()
        }
        assertEquals(AiResponse.Error.KeyRejected().message, convert())
        assertEquals(1, conversionRequests)
    }

    @Test
    fun `answers that can't be used say the model failed`() {
        AiRetryExecutor.send = { request, _ -> AiResponse.Success("not json", request.model.id, null) }
        assertEquals(AiProblem.MODEL_FAILED.message, convert())
    }
}

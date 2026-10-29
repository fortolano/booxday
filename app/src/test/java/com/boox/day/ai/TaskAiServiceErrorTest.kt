package com.boox.day.ai

import android.app.Application
import com.boox.day.ai.intelligence.IntelligenceTestUtils
import com.boox.day.api.OpenRouterAccount
import com.boox.day.api.ai.AiProblem
import com.boox.day.api.ai.AiResponse
import com.boox.day.data.ai.AggregateMetrics
import com.boox.day.data.ai.IntelligenceFeature
import com.boox.day.data.ai.ScoreDistribution
import com.boox.day.data.ai.ScoredContext
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
 * Break down task says what went wrong (Oct 2026). It used to show "AI service temporarily
 * unavailable" for every failure, so no Wi-Fi or an empty OpenRouter account looked the same.
 * Smart suggestions builds its error the same way (TaskAiService.generateSmartSuggestionsV3).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TaskAiServiceErrorTest {

    private val originalSend = AiRetryExecutor.send

    private val scoredContext = ScoredContext(
        feature = IntelligenceFeature.BREAK_DOWN_TASK,
        scoredItems = emptyList(),
        aggregateMetrics = AggregateMetrics(0, 0f, ScoreDistribution(0, 0, 0), emptyList(), emptyMap()),
        contextSummary = ""
    )

    @Before
    fun setUp() {
        OpenRouterAccount.onPaidRequestSucceeded()
    }

    @After
    fun tearDown() {
        AiRetryExecutor.send = originalSend
        OpenRouterAccount.onPaidRequestSucceeded()
    }

    private fun breakDown(): String? = breakDownResult().error

    private fun breakDownResult(): TaskAiService.BreakdownResult = runBlocking {
        val task = IntelligenceTestUtils.createTask(id = "t1", text = "Plan the garden")
        TaskAiService.breakDownTaskV3(
            taskText = "Plan the garden",
            task = task,
            existingTasks = listOf(task),
            currentDate = IntelligenceTestUtils.TODAY,
            scoredContext = scoredContext
        )
    }

    @Test
    fun `an answer that can't be read goes to the next model`() {
        // 29 Oct 2026: it ended the feature with "Could not parse breakdown", no other model tried
        val models = mutableListOf<String>()
        AiRetryExecutor.send = { request, _ ->
            models += request.model.id
            if (models.size == 1) AiResponse.Success("""{"subtasks":[{"text":"Pick pla""", "m", null)
            else AiResponse.Success(
                """{"subtasks":[{"text":"Pick plants","order":1},{"text":"Buy soil","order":2},{"text":"Mark the beds","order":3}],"keepOriginal":false}""",
                "m", null
            )
        }
        val result = breakDownResult()
        assertEquals(null, result.error)
        assertEquals(listOf("Pick plants", "Buy soil", "Mark the beds"), result.subtasks.map { it.text })
        assertEquals(2, models.size)
        assertEquals(false, models[0] == models[1])
    }

    @Test
    fun `no connection says so`() {
        AiRetryExecutor.send = { _, _ -> AiResponse.Error.NetworkError("Network error", UnknownHostException()) }
        // Unit tests have no network state, so a connection failure means OpenRouter can't be reached
        assertEquals(AiProblem.OPENROUTER_UNREACHABLE.message, breakDown())
    }

    @Test
    fun `no credit says to add credit`() {
        AiRetryExecutor.send = { _, _ -> AiResponse.Error.OutOfCredit() }
        assertEquals(AiResponse.Error.OutOfCredit.NO_CREDIT_MESSAGE, breakDown())
    }
}

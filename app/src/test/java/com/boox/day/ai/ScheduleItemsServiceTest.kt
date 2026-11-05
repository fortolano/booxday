package com.boox.day.ai

import android.app.Application
import com.boox.day.api.OpenRouterAccount
import com.boox.day.api.ai.AiRequest
import com.boox.day.api.ai.AiResponse
import com.boox.day.proposals.Proposal
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Never a dead end, and the AI's words are checked, not trusted (Nov 2026). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ScheduleItemsServiceTest {
    private val originalSend = AiRetryExecutor.send
    private val requests = mutableListOf<AiRequest>()
    private val memoDay = LocalDate.of(2026, 11, 2)
    private val today = LocalDate.of(2026, 11, 2)

    private val oneEvent = """{"items":[{"kind":"event","title":"Lunch with Sam","date":"2026-11-03","start_time":"13:00",
        "end_time":"","all_day":false,"location":"","is_deadline":false,"source_text":"Lunch with Sam Tue 1pm","confidence":"high"}]}"""

    @Before
    fun setUp() {
        OpenRouterAccount.onPaidRequestSucceeded()
    }

    @After
    fun tearDown() {
        AiRetryExecutor.send = originalSend
        OpenRouterAccount.onPaidRequestSucceeded()
    }

    private fun answers(vararg replies: String) {
        var next = 0
        AiRetryExecutor.send = { request, _ ->
            requests += request
            AiResponse.Success(replies[minOf(next++, replies.size - 1)], request.model.id, null)
        }
    }

    private fun find(text: String = "Lunch with Sam Tue 1pm") = runBlocking { ScheduleItemsService.findItems(text, memoDay, today) }

    @Test
    fun anEventComesBackReadyToCheck() {
        answers(oneEvent)
        val found = find() as ScheduleResult.Found
        val event = found.proposals.single() as Proposal.Event
        assertEquals("Lunch with Sam", event.title)
        assertEquals(840, event.endMinute)
        assertEquals("Lunch with Sam Tue 1pm", found.readAs)
    }

    @Test
    fun anEmptyAnswerIsNoDateNotAnError() {
        answers("""{"items":[]}""")
        assertEquals(ScheduleResult.NothingDated("Call Sam"), find("Call Sam"))
    }

    @Test
    fun aBadAnswerGoesToTheNextModel() {
        answers("I found a lunch!", oneEvent)
        assertTrue(find() is ScheduleResult.Found)
        assertEquals(2, requests.size)
    }

    @Test
    fun theDayTableTravelsWithTheNoteAndTheCalendarDoesNot() {
        answers(oneEvent)
        find()
        val prompt = requests.single().messages.joinToString { it.toString() }
        assertTrue(prompt.contains("Monday 2026-11-02"))
        assertTrue(prompt.contains("Lunch with Sam Tue 1pm"))
    }
}

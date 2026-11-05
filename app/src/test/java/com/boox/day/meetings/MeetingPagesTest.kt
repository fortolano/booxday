package com.boox.day.meetings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.boox.day.actionitems.MemoPageText
import com.boox.day.ai.AiRetryExecutor
import com.boox.day.ai.MeetingRecap
import com.boox.day.ai.MeetingRecapAction
import com.boox.day.ai.MeetingRecapResult
import com.boox.day.ai.MeetingRecapService
import com.boox.day.api.OpenRouterAccount
import com.boox.day.api.ai.AiResponse
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.DayPage
import com.boox.day.data.db.Page
import com.boox.day.data.db.TextAnnotation
import com.boox.day.sync.EventMemoService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/** A recap is never read back as notes, a repeat costs nothing, and notes follow their event (Nov 2026). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MeetingPagesTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val day = "2026-11-03"
    private val originalSend = AiRetryExecutor.send
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = AppDatabase.getDatabase(context)
        OpenRouterAccount.onPaidRequestSucceeded()
    }

    @After
    fun tearDown() {
        AiRetryExecutor.send = originalSend
        AppDatabase.closeDatabase()
    }

    private fun box(pageId: String, text: String, source: String = "manual") = TextAnnotation(
        id = UUID.randomUUID().toString(), content = text, x = 0f, y = 0f, width = 500f, height = 50f, fontSize = 30f, fontWeight = 400,
        textColor = 0, backgroundColor = 0, textAlign = "left", padding = 0f, pageId = pageId, sourceType = source
    )

    private fun meeting(eventId: String): Pair<String, String> = runBlocking {
        val notes = Page(linkedEventId = eventId).also { db.pageDao().create(it) }
        val recap = Page(linkedEventId = eventId).also { db.pageDao().create(it) }
        db.dayPageDao().insert(DayPage(date = day, pageIds = listOf(notes.id, recap.id), openPageId = notes.id))
        db.textAnnotationDao().insert(box(notes.id, "Budget agreed"))
        db.textAnnotationDao().insert(box(recap.id, "Recap · Team sync", MeetingPages.RECAP_SOURCE))
        notes.id to recap.id
    }

    @Test
    fun aSavedRecapIsNeverReadAsNotes() = runBlocking {
        val (notes, recap) = meeting("e1")
        val pages = MeetingPages.of(context, "e1")!!
        assertEquals(listOf(notes), pages.notes)
        assertEquals(listOf(recap), pages.recap)
        assertEquals(emptyList<String>(), MemoPageText.pageLines(context, null, recap))
    }

    @Test
    fun theFingerprintChangesWithTheNotes() = runBlocking {
        val (notes, _) = meeting("e2")
        val before = MeetingPages.fingerprint(context, listOf(notes))
        assertEquals(before, MeetingPages.fingerprint(context, listOf(notes)))
        db.textAnnotationDao().insert(box(notes, "One more line"))
        assertNotEquals(before, MeetingPages.fingerprint(context, listOf(notes)))
    }

    @Test
    fun aPushedEventKeepsItsNotes() = runBlocking {
        meeting("local-1")
        val service = EventMemoService(context)
        assertNull("the service's id finds nothing before", service.getLinkedMemo("cloud-1"))
        db.pageDao().relinkEvent("local-1", "cloud-1")
        assertEquals(2, service.getLinkedMemo("cloud-1")?.pageIds?.size)
    }

    @Test
    fun aRecapIsKeptAndARepeatMakesNoCall() = runBlocking {
        var calls = 0
        AiRetryExecutor.send = { request, _ ->
            calls++
            AiResponse.Success(
                """{"summary":"Budget agreed.","decisions":[],"action_items":[{"text":"Send budget","owner":"","due_date":""}],"follow_ups":[],"open_questions":[]}""",
                request.model.id, null
            )
        }
        val done = MeetingRecapService.make(context, "e3", "Team sync", day, "Budget agreed", "print") as MeetingRecapResult.Done
        assertEquals("Budget agreed.", done.recap.summary)
        val cached = MeetingRecapService.cached(context, "e3")!!
        assertEquals("print", cached.second)
        assertEquals("Send budget", cached.first.actionItems.single().text)
        assertEquals("the second visit reads the kept copy", 1, calls)
    }

    @Test
    fun aRecapWithNoSummaryGoesToTheNextModel() = runBlocking {
        var calls = 0
        AiRetryExecutor.send = { request, _ ->
            calls++
            AiResponse.Success(if (calls == 1) "not json" else """{"summary":"Done.","decisions":[],"action_items":[],"follow_ups":[],"open_questions":[]}""", request.model.id, null)
        }
        assertTrue(MeetingRecapService.make(context, "e4", "Team sync", day, "x", "p") is MeetingRecapResult.Done)
        assertEquals(2, calls)
    }

    @Test
    fun longRecapsBreakAtLineEndsAndEmptyPartsAreLeftOut() {
        val pages = MeetingRecapSaver.pagesOf("one\ntwo\nthree\nfour") { it.lines().size <= 2 }
        assertEquals(listOf("one\ntwo", "three\nfour"), pages)
        val text = MeetingRecapSaver.text("Team sync", MeetingRecap(summary = "S", actionItems = listOf(MeetingRecapAction("Do it", "Anna", "2026-11-06"))))
        assertTrue(text.contains("• Do it · Anna · 2026-11-06"))
        assertFalse(text.contains("Decisions"))
    }

    @Test
    fun aRecapFitsOnePageWhenThereIsRoomAndSplitsWhenNot() {
        val recap = MeetingRecap(summary = "S", decisions = List(6) { "D$it" }, followUps = List(6) { "F$it" })
        assertEquals(1, recapPages(recap, 30).size)
        assertTrue(recapPages(recap, 8).size > 1)
    }
}

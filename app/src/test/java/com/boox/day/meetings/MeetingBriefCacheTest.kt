package com.boox.day.meetings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.CalendarEvent
import com.boox.day.data.db.SummaryCache
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A second Brief Me on the same material is free: no key is set here, so any AI call would fail. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MeetingBriefCacheTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `the same material comes from the cache`() = runBlocking {
        val event = CalendarEvent(id = "e1", calendarId = "work", calendarName = "work", title = "Team sync", dayId = "2026-11-10", startTime = 0L)
        val kept = MeetingBrief(points = listOf("Budget was agreed"), worthAsking = listOf("Who owns the launch?"))
        val hash = MeetingBriefService.material(event, emptyList(), emptyList()).hashCode().toString()
        AppDatabase.getDatabase(context).summaryCacheDao().save(
            SummaryCache(id = "meeting_brief_e1", type = "meeting_brief", dateKey = event.dayId, contentHash = hash, resultJson = Gson().toJson(kept), generatedAt = 1L, validHours = 1)
        )

        assertEquals(kept, MeetingBriefService.brief(context, event, emptyList(), emptyList()))
    }
}

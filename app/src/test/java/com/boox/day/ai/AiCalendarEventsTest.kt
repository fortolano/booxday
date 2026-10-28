package com.boox.day.ai

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.CalendarEvent
import com.boox.day.data.db.CalendarSelectionEntity
import com.boox.day.data.db.ConnectedAccountEntity
import com.boox.day.data.db.LocalEvent
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/**
 * A day's events for the AI features (Oct 2026): Daily Summary, Weekly Summary and Smart Suggestions
 * read only events kept on the tablet, so a synced Google/Outlook calendar never reached them (Go
 * 10.3: 607 synced events, none on the tablet). Synced and tablet events are read, each event once,
 * from the calendars shown in BooxDay (the privacy policy: only Optimize My Day reads hidden ones).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AiCalendarEventsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val db get() = AppDatabase.getDatabase(context)

    @Before
    fun calendars() = runBlocking {
        db.connectedAccountDao().upsert(
            ConnectedAccountEntity(id = "acc", providerId = "GOOGLE", accountType = "GOOGLE_PERSONAL",
                email = "me@example.com", displayName = "Me", isActive = true, createdAt = 1_760_000_000_000)
        )
        db.calendarSelectionDao().upsert(selection("work", shown = true))
        db.calendarSelectionDao().upsert(selection("hidden", shown = false))
    }

    @After
    fun closeDatabase() {
        AppDatabase.closeDatabase()
    }

    @Test
    fun `a day's synced and tablet events are both read, each event once`() = runBlocking {
        db.calendarEventDao().upsertAll(listOf(
            synced("meeting", "2026-11-02", hour = 9),
            synced("pushed-long-ago", "2026-11-02", hour = 11),
            synced("conference", "2026-11-01", hour = 8, endDayId = "2026-11-03"),
            synced("other-day", "2026-11-05", hour = 9)
        ))
        db.localEventDao().insert(onTablet("dentist", "2026-11-02", hour = 10))
        // Written to Google from the tablet: the synced copy is the same event
        db.localEventDao().insert(onTablet("local-copy", "2026-11-02", hour = 9, providerEventId = "meeting"))
        db.localEventDao().insert(onTablet("older-copy", "2026-11-02", hour = 11, googleEventId = "pushed-long-ago"))

        val events = AiCalendarEvents.forDay(context, LocalDate.parse("2026-11-02"))

        assertEquals(listOf("conference", "meeting", "dentist", "pushed-long-ago"), events.map { it.title })
    }

    @Test
    fun `a calendar hidden in BooxDay is left out, as on the calendar`() = runBlocking {
        db.calendarEventDao().upsertAll(listOf(
            synced("shown", "2026-11-02", hour = 9),
            synced("private", "2026-11-02", hour = 12, calendarId = "hidden")
        ))

        val events = AiCalendarEvents.forDay(context, LocalDate.parse("2026-11-02"))

        assertEquals(listOf("shown"), events.map { it.title })
    }

    @Test
    fun `all-day events come first, then by start time`() {
        val merged = AiCalendarEvents.merge(
            synced = listOf(synced("late", "2026-11-02", hour = 16).toLocalEvent(), synced("holiday", "2026-11-02", hour = 0, allDay = true).toLocalEvent()),
            onTablet = listOf(onTablet("early", "2026-11-02", hour = 7))
        )
        assertEquals(listOf("holiday", "early", "late"), merged.map { it.title })
    }

    private fun selection(calendarId: String, shown: Boolean) = CalendarSelectionEntity(
        id = "acc-$calendarId", accountId = "acc", calendarId = calendarId, calendarName = calendarId,
        calendarColor = null, isFetchEnabled = shown
    )

    private fun synced(
        id: String, dayId: String, hour: Int, endDayId: String = dayId, allDay: Boolean = false, calendarId: String = "work"
    ) = CalendarEvent(
        id = id, accountId = "acc", calendarId = calendarId, calendarName = calendarId, title = id,
        dayId = dayId, endDayId = endDayId, startTime = millis(dayId, hour), endTime = millis(dayId, hour) + 3_600_000,
        isAllDay = allDay
    )

    private fun onTablet(
        title: String, dayId: String, hour: Int, providerEventId: String? = null, googleEventId: String? = null
    ) = LocalEvent(
        title = title, dayId = dayId, endDayId = dayId, startTime = millis(dayId, hour), endTime = millis(dayId, hour) + 3_600_000,
        providerEventId = providerEventId, googleEventId = googleEventId
    )

    private fun millis(dayId: String, hour: Int) =
        LocalDate.parse(dayId).atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

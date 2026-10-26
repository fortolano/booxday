package com.boox.day.data.db

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
import java.time.ZoneId

/**
 * Calendar dots for events (Oct 2026): a week-long event had a dot only on its first day, and an
 * event that started last month had none this month (reported 22 Jan: a full-week appointment showed
 * only on Monday). A dot must appear exactly on the days whose event list shows the event.
 * Robolectric, in-memory Room.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class EventBadgeDaysTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun cloudEventsGetADotOnEveryDayTheirListShowsThem() = runBlocking {
        db.connectedAccountDao().upsert(
            ConnectedAccountEntity(id = "acc", providerId = "GOOGLE", accountType = "GOOGLE_PERSONAL",
                email = "me@example.com", displayName = "Me", isActive = true, createdAt = 1_760_000_000_000)
        )
        db.calendarSelectionDao().upsert(selection("work", shown = true))
        db.calendarSelectionDao().upsert(selection("hidden", shown = false))
        db.calendarEventDao().upsertAll(listOf(
            event("week", "work", "2026-11-02", "2026-11-06"),
            event("from-october", "work", "2026-10-30", "2026-11-02"),
            event("into-december", "work", "2026-11-29", "2026-12-03"),
            event("hidden-calendar", "hidden", "2026-11-10", "2026-11-11"),
            event("no-end-day", "work", "2026-11-20", ""),
            event("other-month", "work", "2026-12-15", "2026-12-15"),
        ))

        val dots = days("2026-11") { first, last -> db.calendarEventDao().getDaySpansFiltered(first, last) }

        val expected = listOf("01", "02", "03", "04", "05", "06", "29", "30").map { "2026-11-$it" }.toSet()
        assertEquals(expected, dots)
        for (day in novemberDays()) {
            val listed = db.calendarEventDao().getByDayIdFilteredSync(day).isNotEmpty()
            assertEquals("day list and dot disagree on $day", listed, day in dots)
        }
    }

    @Test
    fun aLongEventFillsTheMonthAndNoMore() = runBlocking {
        db.localEventDao().insert(localEvent("quarter", "2026-10-01", "2026-12-31"))

        val dots = days("2026-11") { first, last -> db.localEventDao().getDaySpans(first, last) }

        assertEquals(novemberDays().toSet(), dots)
    }

    @Test
    fun localEventsCountUntilTheyAreDeleted() = runBlocking {
        db.localEventDao().insert(localEvent("trip", "2026-11-27", "2026-12-02"))
        db.localEventDao().insert(localEvent("gone", "2026-11-10", "2026-11-12").copy(syncStatus = SyncStatus.PENDING_DELETE))

        val dots = days("2026-11") { first, last -> db.localEventDao().getDaySpans(first, last) }

        assertEquals(setOf("2026-11-27", "2026-11-28", "2026-11-29", "2026-11-30"), dots)
        assertTrue(db.localEventDao().getEventsForDay("2026-11-11").isEmpty())
    }

    private suspend fun days(yearMonth: String, query: suspend (String, String) -> List<EventDaySpan>): Set<String> {
        val (first, last) = monthDayRange(yearMonth)
        return daysCoveredInMonth(query(first, last), yearMonth)
    }

    private fun novemberDays() = (1..30).map { "2026-11-%02d".format(it) }

    private fun selection(calendarId: String, shown: Boolean) = CalendarSelectionEntity(
        id = "acc-$calendarId", accountId = "acc", calendarId = calendarId, calendarName = calendarId,
        calendarColor = null, isFetchEnabled = shown
    )

    private fun event(id: String, calendarId: String, dayId: String, endDayId: String) = CalendarEvent(
        id = id, accountId = "acc", calendarId = calendarId, calendarName = calendarId, title = id,
        dayId = dayId, endDayId = endDayId, startTime = millis(dayId), endTime = millis(dayId) + 3_600_000
    )

    private fun localEvent(id: String, dayId: String, endDayId: String) = LocalEvent(
        id = id, title = id, dayId = dayId, endDayId = endDayId, startTime = millis(dayId), endTime = millis(dayId) + 3_600_000
    )

    private fun millis(dayId: String) =
        LocalDate.parse(dayId).atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

package com.boox.day.today

import android.app.Application
import android.content.pm.ProviderInfo
import androidx.test.core.app.ApplicationProvider
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.CalendarEvent
import com.boox.day.data.db.CalendarSelectionEntity
import com.boox.day.data.db.ConnectedAccountEntity
import com.boox.day.data.db.LocalEvent
import com.boox.day.data.db.Page
import com.boox.day.data.db.Task
import com.boox.day.share.TasksFeedContract
import com.boox.day.share.TasksFeedProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * What the sleep-screen agenda and the daily note read for a day (Oct 2026): events of the
 * calendars the user shows plus local ones, in order; the Tasks rows with something on them; and
 * no memo page made for a day that has none. Robolectric, the app's database and tasks feed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DayDigestLoaderTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: AppDatabase
    private val day = LocalDate.of(2026, 10, 24)

    @Before
    fun setUp() {
        db = AppDatabase.getDatabase(context)
        Robolectric.buildContentProvider(TasksFeedProvider::class.java)
            .create(ProviderInfo().apply { authority = TasksFeedContract.AUTHORITY }).get()
    }

    @After
    fun tearDown() {
        AppDatabase.closeDatabase()
    }

    @Test
    fun eventsOfShownCalendarsAndLocalEventsInOrder() = runBlocking {
        db.connectedAccountDao().upsert(
            ConnectedAccountEntity(id = "acc", providerId = "GOOGLE", accountType = "GOOGLE_PERSONAL",
                email = "me@example.com", displayName = "Me", isActive = true, createdAt = 1_760_000_000_000)
        )
        db.calendarSelectionDao().upsert(selection("work", shown = true))
        db.calendarSelectionDao().upsert(selection("hidden", shown = false))
        db.calendarEventDao().upsertAll(listOf(
            event("e-late", "work", "Review", startHour = 15),
            event("e-early", "work", "Stand-up", startHour = 9),
            event("e-hidden", "hidden", "Not shown", startHour = 10),
            event("e-allday", "work", "Holiday", startHour = 0).copy(isAllDay = true, endTime = null),
            event("e-other-day", "work", "Tomorrow", startHour = 9, date = day.plusDays(1)),
        ))
        db.localEventDao().insert(LocalEvent(id = "l1", title = "Dentist", dayId = day.toString(), endDayId = day.toString(),
            startTime = millis(day, 11), endTime = millis(day, 12), location = "King St"))

        val digest = DayDigestLoader(context).load(day, nextDays = 2)

        assertEquals(listOf("Holiday", "Stand-up", "Dentist", "Review"), digest.events.map { it.title })
        assertEquals("King St", digest.events.first { it.id == "l1" }.location)
        assertEquals(listOf(day.plusDays(1), day.plusDays(2)), digest.nextDays.map { it.first })
        assertEquals(listOf("Tomorrow"), digest.nextDays.first().second.map { it.title })
    }

    @Test
    fun tasksWithContentInPageOrder() = runBlocking {
        val page = Page().also { db.pageDao().create(it) }
        db.taskDao().insertAll((0 until 10).map { row ->
            Task(dayDate = day.toString(), taskPageIndex = 0, rowIndex = row, pageId = page.id,
                typedText = when (row) { 0 -> "Buy bread"; 2 -> "Call Mary\nabout Sunday"; else -> null },
                isCompleted = row == 0, priority = if (row == 2) "A" else "")
        })

        val digest = DayDigestLoader(context).load(day, withFullTaskText = true)

        assertEquals(listOf("Buy bread", "Call Mary"), digest.tasks.map { it.title })
        assertEquals(listOf(true, false), digest.tasks.map { it.completed })
        assertEquals("A", digest.tasks[1].priority)
        assertEquals("Call Mary\nabout Sunday", digest.tasks[1].fullText)
        assertEquals(1, digest.doneCount)
        assertEquals(2, digest.topLevelCount)
    }

    @Test
    fun aDayWithoutMemosGetsNoPageMade() = runBlocking {
        val loader = DayDigestLoader(context)
        val digest = loader.load(day, withMemoText = true)
        assertTrue(digest.memoPages.isEmpty())
        assertNull(loader.memoPageId(day))
        assertNull("no DayPage created", db.dayPageDao().getByDate(day.toString()))
    }

    private fun selection(calendarId: String, shown: Boolean) = CalendarSelectionEntity(
        id = "acc-$calendarId", accountId = "acc", calendarId = calendarId, calendarName = calendarId,
        calendarColor = null, isFetchEnabled = shown
    )

    private fun event(id: String, calendarId: String, title: String, startHour: Int, date: LocalDate = day) = CalendarEvent(
        id = id, accountId = "acc", calendarId = calendarId, calendarName = calendarId, title = title,
        dayId = date.toString(), endDayId = date.toString(), startTime = millis(date, startHour), endTime = millis(date, startHour + 1)
    )

    private fun millis(date: LocalDate, hour: Int) =
        date.atTime(hour, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
}

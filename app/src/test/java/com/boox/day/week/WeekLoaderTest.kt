package com.boox.day.week

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.CalendarEvent
import com.boox.day.data.db.Page
import com.boox.day.data.db.RecurringTaskTemplate
import com.boox.day.data.db.Task
import com.boox.day.tasks.TaskPaginationTest.Companion.ROW_HEIGHT
import com.boox.day.tasks.TaskPaginationTest.Companion.TOP
import com.boox.day.tasks.TaskPaginationTest.Companion.strokeInRow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

/** The Week page reads and never makes anything, and it shows what the plan's decisions say (Nov 2026). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WeekLoaderTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val settingsBefore = GlobalAppSettings.current
    private val monday = LocalDate.of(2026, 11, 2)
    private val today = monday.plusDays(1)
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = AppDatabase.getDatabase(context)
        GlobalAppSettings.update(settingsBefore.copy(tasksInkCanvasHeight = TOP + 10 * ROW_HEIGHT, tasksInkTopOffset = TOP))
    }

    @After
    fun tearDown() {
        GlobalAppSettings.update(settingsBefore)
        AppDatabase.closeDatabase()
    }

    private fun loader(events: (String) -> List<CalendarEvent> = { emptyList() }) = WeekLoader(context, db, { events(it) })

    private fun load(loader: WeekLoader = loader()) = runBlocking { loader.load(monday, today) }

    private fun newPage() = Page().also { db.pageDao().create(it) }

    private fun row(day: LocalDate, row: Int, text: String?, page: Page = newPage(), due: LocalDate? = null, parent: String? = null) =
        Task(dayDate = day.toString(), taskPageIndex = 0, rowIndex = row, pageId = page.id, typedText = text, dueDay = due?.toString(), parentTaskId = parent)

    private fun count(table: String) =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    private fun template(title: String) =
        RecurringTaskTemplate(title = title, recurrenceType = "DAILY", anchorDate = monday.minusDays(7).toString())

    @Test
    fun browsingMakesNothing() {
        db.recurringTaskTemplateDao().insert(template("Water plants"))
        val tables = listOf("task", "page")
        val before = tables.map { count(it) }
        repeat(3) { load() }
        assertEquals(before, tables.map { count(it) })
    }

    @Test
    fun blankSlotsAreLeftOutAndHandwritingOnAClosedPageIsKept() {
        val page = newPage()
        db.taskDao().insertAll((0 until 10).map { row(monday, it, if (it == 0) "Typed" else null, page) })
        db.strokeDao().create(strokeInRow(page.id, row = 1, from = 0.2f, to = 0.6f))
        db.strokeDao().create(strokeInRow(page.id, row = 1, from = 0.3f, to = 0.7f))
        assertEquals(listOf("Typed", null), load().days.first().tasks.map { it.text })
    }

    @Test
    fun aTaskDueFridayShowsOnBothDays() {
        val friday = monday.plusDays(4)
        db.taskDao().insertAll(listOf(row(monday, 0, "Call Anna", due = friday)))
        val days = load().days
        assertEquals(listOf(false), days[0].tasks.map { it.due })
        assertEquals(listOf(true), days[4].tasks.map { it.due })
    }

    @Test
    fun aRepeatIsPreviewedOnlyWhereItHasNoRowAndNotInThePast() {
        val template = template("Water plants")
        db.recurringTaskTemplateDao().insert(template)
        db.taskDao().insertAll(listOf(row(today.plusDays(1), 0, "Water plants").copy(templateId = template.id)))
        val days = load().days
        assertEquals("past day", 0, days[0].tasks.size)
        assertEquals(listOf(true), days[1].tasks.map { it.repeat })
        assertEquals("the row, not a preview too", listOf(false), days[2].tasks.map { it.repeat })
    }

    @Test
    fun subtasksAreLeftOutAndFinishedTasksAreCounted() {
        val parent = row(monday, 0, "Parent")
        db.taskDao().insertAll(listOf(parent, row(monday, 1, "Child", parent = parent.id), row(monday, 2, "Done").copy(isCompleted = true)))
        val day = load().days.first()
        assertEquals(listOf("Parent"), day.tasks.map { it.text })
        assertEquals(1, day.finished.size)
    }

    @Test
    fun eventsComeFromTheCalendarsOwnRuleAndALaterDayOfATimedEventIsAllDay() {
        val zone = java.time.ZoneId.systemDefault()
        val start = monday.atTime(9, 30).atZone(zone).toInstant().toEpochMilli()
        val event = CalendarEvent(
            id = "e", accountId = "a", calendarId = "c", calendarName = "c", title = "Trip",
            dayId = monday.toString(), endDayId = monday.plusDays(1).toString(), startTime = start
        )
        val lastDay = monday.plusDays(1).toString()
        val days = load(loader { if (it <= lastDay) listOf(event) else emptyList() }).days
        assertEquals(570, days[0].events.single().minute)
        assertTrue(days[1].events.single().allDay)
        assertEquals(0, days[2].events.size)
    }

    @Test
    fun theWeekStartFollowsTheSetting() {
        val wednesday = LocalDate.of(2026, 11, 4)
        assertEquals(DayOfWeek.MONDAY, AppSettings.FirstDayOfWeek.Monday.weekStart(wednesday).dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, AppSettings.FirstDayOfWeek.Sunday.weekStart(wednesday).dayOfWeek)
        assertEquals(DayOfWeek.SATURDAY, AppSettings.FirstDayOfWeek.Saturday.weekStart(wednesday).dayOfWeek)
    }

    @Test
    fun theHeaderNamesTheWeekAcrossMonthsAndYears() {
        val nov = LocalDate.of(2026, 11, 2)
        val oct = LocalDate.of(2026, 10, 26)
        val dec = LocalDate.of(2026, 12, 28)
        assertEquals("Nov 2 – 8, 2026", WeekPresenter.range(nov, nov.plusDays(6), false, true, Locale.US))
        assertEquals("Oct 26 – Nov 1, 2026", WeekPresenter.range(oct, oct.plusDays(6), false, true, Locale.US))
        assertEquals("Dec 28, 2026 – Jan 3, 2027", WeekPresenter.range(dec, dec.plusDays(6), false, true, Locale.US))
        assertEquals("2 – 8 Nov 2026", WeekPresenter.range(nov, nov.plusDays(6), true, true, Locale.US))
        assertEquals("Nov 2 – 8", WeekPresenter.range(nov, nov.plusDays(6), false, false, Locale.US))
    }
}

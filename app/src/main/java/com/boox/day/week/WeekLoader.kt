package com.boox.day.week

import android.content.Context
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.CalendarEvent
import com.boox.day.data.db.RecurringTaskRuleEvaluator
import com.boox.day.data.db.Task
import com.boox.day.share.HandwrittenRows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A task line: [task] is null for a repeat that has no row on its day yet. */
data class WeekTaskItem(
    val task: Task?,
    val text: String?,
    val due: Boolean = false,
    val repeat: Boolean = false
)

data class WeekEventItem(val event: CalendarEvent, val minute: Int?, val allDay: Boolean)

data class WeekDayData(
    val date: LocalDate,
    val events: List<WeekEventItem>,
    val tasks: List<WeekTaskItem>,
    val finished: List<WeekTaskItem>
)

data class WeekData(val start: LocalDate, val days: List<WeekDayData>, val overdue: List<WeekTaskItem>)

/**
 * Reads one week for the Week page (Nov 2026). **Read only**: selecting a day creates memo pages, task
 * pages and repeating rows, so this never goes through that path. [events] is the calendar's own rule
 * for a day (hidden calendars, the source filter, tablet copies); the range queries ignore those.
 */
class WeekLoader(
    context: Context,
    private val db: AppDatabase = AppDatabase.getDatabase(context.applicationContext),
    private val events: suspend (String) -> List<CalendarEvent>,
    private val zone: ZoneId = ZoneId.systemDefault()
) {
    private val app = context.applicationContext

    suspend fun load(start: LocalDate, today: LocalDate): WeekData = withContext(Dispatchers.IO) {
        val end = start.plusDays(6)
        val dao = db.taskDao()
        val handwriting = HandwrittenRows(app, db.openHelper.readableDatabase)
        fun hasContent(task: Task) = text(task) != null || handwriting.inRow(task.pageId, task.dayDate, task.rowIndex)

        val written = dao.getTasksForDateRange(start.toString(), end.toString())
            .filter { it.parentTaskId == null && hasContent(it) }
            .groupBy { it.dayDate }
        val dueHere = dao.getTasksDueBetween(start.toString(), end.toString())
            .filter { it.parentTaskId == null && it.dueDay != it.dayDate }
            .groupBy { it.dueDay }

        val days = (0L..6L).map { offset ->
            val date = start.plusDays(offset)
            val day = date.toString()
            val rows = written[day].orEmpty()
            val open = rows.filter { !it.isCompleted }.sortedWith(TASK_ORDER).map { WeekTaskItem(it, text(it)) }
            val due = dueHere[day].orEmpty().sortedWith(TASK_ORDER).map { WeekTaskItem(it, text(it), due = true) }
            WeekDayData(
                date = date,
                events = events(day).map { eventItem(it, date) },
                tasks = open + due + if (date >= today) repeats(date, rows) else emptyList(),
                finished = rows.filter { it.isCompleted }.map { WeekTaskItem(it, text(it)) }
            )
        }
        val overdue = dao.getOverdueTasksFlow(today.toString()).first()
            .filter { it.parentTaskId == null }
            .map { WeekTaskItem(it, text(it)) }
        WeekData(start, days, overdue)
    }

    /** Repeats whose row doesn't exist yet: it's only made when the day is opened, so this is a preview. */
    private fun repeats(date: LocalDate, rows: List<Task>): List<WeekTaskItem> {
        val made = rows.mapNotNullTo(HashSet()) { it.templateId }
        return db.recurringTaskTemplateDao().getActiveTemplatesForDate(date.toString())
            .filter { it.id !in made && RecurringTaskRuleEvaluator.shouldGenerateOnDate(it, date) }
            .map { WeekTaskItem(task = null, text = it.title, repeat = true) }
    }

    private fun eventItem(event: CalendarEvent, date: LocalDate): WeekEventItem {
        val start = Instant.ofEpochMilli(event.startTime).atZone(zone)
        // A day inside a longer event has no start of its own
        val continues = !event.isAllDay && start.toLocalDate() != date
        return WeekEventItem(
            event = event,
            minute = if (event.isAllDay || continues) null else start.hour * 60 + start.minute,
            allDay = event.isAllDay || continues
        )
    }

    companion object {
        /** A, B, C, then no priority; then as written. */
        private val TASK_ORDER = compareBy<Task>(
            { if (it.priority.isBlank()) "Z" else it.priority },
            { it.taskPageIndex },
            { it.rowIndex }
        )

        /** The task's words: read now, typed, then the stored handwriting (the order used everywhere). */
        fun text(task: Task): String? =
            listOf(task.convertedText, task.typedText, task.recognizedText).firstOrNull { !it.isNullOrBlank() }?.trim()
    }
}

package com.boox.day.today

import android.content.Context
import android.util.Log
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.BooxNoteRepository
import com.boox.day.data.db.TaskRepository
import com.boox.day.data.db.UnifiedOcrRepository
import com.boox.day.ocr.model.OcrSource
import com.boox.day.share.TasksFeedQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Reads one day for the sleep-screen agenda and the Markdown daily note (Oct 2026). Works in a
 * cold process (a worker): only the database and BooxDay's own tasks feed, no view model.
 *
 * - Events: the calendars the user shows (calendar_selections) plus local events, merged and
 *   ordered like EventsViewModel.getEventsForDateSync.
 * - Tasks: the tasks feed's day list (share/TasksFeedProvider), which already knows which rows have
 *   something on them, handwriting-only rows included.
 * - Memo pages: the day's DayPage, read only; a day without one has no memo (none is created).
 */
class DayDigestLoader(context: Context) {
    private val app = context.applicationContext
    private val db get() = AppDatabase.getDatabase(app)

    suspend fun load(
        date: LocalDate,
        nextDays: Int = 0,
        withMemoText: Boolean = false,
        withFullTaskText: Boolean = false,
    ): DayDigest = withContext(Dispatchers.IO) {
        DayDigest(
            date = date,
            events = events(date, withNotes = true),
            tasks = tasks(date, withFullTaskText),
            streakDays = runCatching { TaskRepository(app).getCurrentStreak() }.getOrDefault(0),
            memoPages = memoPages(date, withMemoText),
            nextDays = (1..nextDays).map { n -> date.plusDays(n.toLong()).let { it to events(it, withNotes = false) } },
        )
    }

    /** Today's memo page id with ink, for the memo panel: the page that is open on that day, else the first. */
    fun memoPageId(date: LocalDate): String? {
        val dayPage = db.dayPageDao().getByDate(date.toString()) ?: return null
        val pageId = dayPage.openPageId?.takeIf { it in dayPage.pageIds } ?: dayPage.pageIds.firstOrNull()
        return pageId?.takeIf { db.strokeDao().hasStrokesForPage(it) }
    }

    private suspend fun events(date: LocalDate, withNotes: Boolean): List<DigestEvent> {
        val day = date.toString()
        val cloud = db.calendarEventDao().getByDayIdFilteredSync(day)
        val local = ShownEvents.tabletOnly(cloud, db.localEventDao().getEventsForDay(day))
        val notes = if (withNotes) BooxNoteRepository(app) else null
        val merged = cloud.map { DigestEvent(it.id, it.title, it.startTime, it.endTime, it.isAllDay, it.location) } +
            local.map { DigestEvent(it.id, it.title, it.startTime, it.endTime, it.isAllDay, it.location) }
        return merged
            .sortedWith(compareByDescending<DigestEvent> { it.allDay }.thenBy { it.startMs })
            .map { event ->
                if (notes == null) event
                else event.copy(booxNoteTitles = runCatching { notes.getNotesLinkedToEvent(event.id).map { it.title } }
                    .getOrDefault(emptyList()))
            }
    }

    private fun tasks(date: LocalDate, withFullText: Boolean): List<DigestTask> {
        // Query Room directly through the shared TasksFeedQuery helper: bypasses the external
        // opt-in ContentProvider gate (Decision D1), has no external 200-task cap, and propagates
        // DB read errors instead of silently exporting/rendering an empty task list (M5(4)).
        return TasksFeedQuery.query(
            context = app,
            mode = TasksFeedQuery.Mode.TODAY,
            day = date.toString(),
            today = date.toString(),
            limit = null,
        ).map { row ->
            DigestTask(
                id = row.taskId,
                title = if (row.handwrittenOnly) null else row.title,
                completed = row.completed,
                priority = row.priority,
                isSubtask = row.parentTaskId != null,
                fullText = if (withFullText) row.fullText else null,
            )
        }
    }

    private suspend fun memoPages(date: LocalDate, withText: Boolean): List<DigestMemoPage> {
        val dayPage = db.dayPageDao().getByDate(date.toString()) ?: return emptyList()
        val ocr = if (withText) UnifiedOcrRepository(app) else null
        val withPictures = db.ImageDao().getPagesWithImages(dayPage.pageIds).toSet()
        return dayPage.pageIds.map { pageId ->
            DigestMemoPage(
                pageId = pageId,
                text = ocr?.getOcrText(OcrSource.MEMO, date.toString(), pageId)?.text?.takeIf { it.isNotBlank() },
                textBoxes = if (withText) db.textAnnotationDao().getByPageId(pageId).map { it.content }.filter { it.isNotBlank() }
                else emptyList(),
                hasInk = db.strokeDao().hasStrokesForPage(pageId),
                hasPicture = pageId in withPictures,
            )
        }
    }

    private companion object {
        const val TAG = "DayDigestLoader"
    }
}

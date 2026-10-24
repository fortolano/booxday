package com.boox.day.today

import android.content.Context
import android.net.Uri
import android.util.Log
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.BooxNoteRepository
import com.boox.day.data.db.TaskRepository
import com.boox.day.data.db.UnifiedOcrRepository
import com.boox.day.ocr.model.OcrSource
import com.boox.day.share.TasksFeedContract
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
        val cloudIds = cloud.mapTo(HashSet()) { it.id }
        val local = db.localEventDao().getEventsForDay(day).filter { it.id !in cloudIds }
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
        val uri = Uri.withAppendedPath(TasksFeedContract.BASE_URI, TasksFeedContract.PATH_TODAY).buildUpon()
            .appendQueryParameter("day", date.toString())
            .appendQueryParameter("limit", "200")
            .build()
        val out = mutableListOf<DigestTask>()
        try {
            app.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val id = c.getColumnIndexOrThrow(TasksFeedContract.TASK_ID)
                val title = c.getColumnIndexOrThrow(TasksFeedContract.TITLE)
                val handwritten = c.getColumnIndexOrThrow(TasksFeedContract.HANDWRITTEN_ONLY)
                val completed = c.getColumnIndexOrThrow(TasksFeedContract.COMPLETED)
                val priority = c.getColumnIndexOrThrow(TasksFeedContract.PRIORITY)
                val parent = c.getColumnIndexOrThrow(TasksFeedContract.PARENT_TASK_ID)
                while (c.moveToNext()) {
                    val taskId = c.getString(id)
                    val fullText = if (withFullText) db.taskDao().getById(taskId)?.let { task ->
                        listOf(task.convertedText, task.typedText, task.recognizedText).firstOrNull { !it.isNullOrBlank() }?.trim()
                    } else null
                    out += DigestTask(
                        id = taskId,
                        title = if (c.getInt(handwritten) == 1) null else c.getString(title),
                        completed = c.getInt(completed) == 1,
                        priority = c.getString(priority).orEmpty(),
                        isSubtask = !c.isNull(parent),
                        fullText = fullText,
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Tasks for $date not read: ${e.message}")
        }
        return out
    }

    private suspend fun memoPages(date: LocalDate, withText: Boolean): List<DigestMemoPage> {
        val dayPage = db.dayPageDao().getByDate(date.toString()) ?: return emptyList()
        val ocr = if (withText) UnifiedOcrRepository(app) else null
        return dayPage.pageIds.map { pageId ->
            DigestMemoPage(
                pageId = pageId,
                text = ocr?.getOcrText(OcrSource.MEMO, date.toString(), pageId)?.text?.takeIf { it.isNotBlank() },
                textBoxes = if (withText) db.textAnnotationDao().getByPageId(pageId).map { it.content }.filter { it.isNotBlank() }
                else emptyList(),
                hasInk = db.strokeDao().hasStrokesForPage(pageId),
            )
        }
    }

    private companion object {
        const val TAG = "DayDigestLoader"
    }
}

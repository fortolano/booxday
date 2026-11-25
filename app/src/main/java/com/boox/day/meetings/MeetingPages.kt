package com.boox.day.meetings

import android.content.Context
import com.boox.day.ai.MemoPageContent
import com.boox.day.ai.MemoToTasksService
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.CalendarEvent
import com.boox.day.io.ExportText
import com.boox.day.sync.EventMemoService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A meeting's pages (Nov 2026). A saved recap sits on a page linked to the meeting, so it is told apart
 * from the notes by its text box's source: read back as notes, a recap would feed the next recap.
 */
object MeetingPages {
    const val RECAP_SOURCE = "meeting_recap"
    /** A longer meeting is read up to here, and the sheet says so. */
    const val MAX_PAGES = 12

    data class Pages(val date: String, val notes: List<String>, val recap: List<String>)

    /** [skipped]: pages past [MAX_PAGES]. */
    data class Read(val text: String, val pages: Int, val skipped: Int)

    /** The meeting a memo page belongs to, a tablet-only one too; null for any other page. */
    suspend fun eventOf(context: Context, pageId: String): CalendarEvent? = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context.applicationContext)
        val eventId = db.pageDao().getById(pageId)?.linkedEventId ?: return@withContext null
        db.calendarEventDao().getById(eventId) ?: db.localEventDao().getById(eventId)?.toCalendarEvent()
    }

    /** Null when the meeting has no pages. */
    suspend fun of(context: Context, eventId: String): Pages? {
        val memo = EventMemoService(context.applicationContext).getLinkedMemo(eventId) ?: return null
        val (recap, notes) = memo.pageIds.partition { isRecap(context, it) }
        return Pages(memo.date, notes, recap)
    }

    private suspend fun isRecap(context: Context, pageId: String): Boolean =
        MemoPageContent.load(context, pageId).let { page ->
            page.strokes.isEmpty() && page.textAnnotations.any { it.sourceType == RECAP_SOURCE }
        }

    /** Changes when the notes change, without reading them: a repeat costs nothing. */
    suspend fun fingerprint(context: Context, pageIds: List<String>): String = withContext(Dispatchers.Default) {
        val parts = mutableListOf<String>()
        for (pageId in pageIds) {
            val page = MemoPageContent.load(context, pageId)
            val ink = page.strokes.sumOf { it.id.hashCode().toLong() }
            val typed = page.textAnnotations.filter { it.sourceType != EventMemoService.HEADER_SOURCE }.joinToString { it.content }.hashCode()
            parts += "${page.strokes.size}:$ink:$typed"
        }
        parts.joinToString("|")
    }

    /**
     * The notes' words, for the AI: a page's saved reading when nothing changed since, else the AI reads
     * its ink-only picture (excluding typed boxes and any saved recap box on hybrid pages).
     * Stops before calling vision OCR on further pages once [maxChars] is already filled, and reports
     * the actual count of read vs skipped pages (M6(4)).
     * @throws Exception with a plain message when no model could read a page.
     */
    suspend fun readText(
        context: Context,
        date: String,
        pageIds: List<String>,
        maxChars: Int = com.boox.day.ai.MeetingRecapService.MAX_TEXT_LENGTH
    ): Read {
        val candidates = pageIds.take(MAX_PAGES)
        val parts = mutableListOf<String>()
        var totalChars = 0
        var pagesRead = 0

        for (pageId in candidates) {
            if (totalChars >= maxChars) break
            val typed = com.boox.day.actionitems.MemoPageText.pageLines(context, null, pageId)
            val saved = ExportText.savedText(context, date, pageId)
            val ink = if (saved != null) {
                saved.joinToString("\n")
            } else {
                val pageContent = MemoPageContent.load(context, pageId)
                if (pageContent.strokes.isEmpty()) "" else readPicture(context, pageId)
            }
            val pageText = (listOf(ink) + typed).filter { it.isNotBlank() }.joinToString("\n")
            pagesRead++
            if (pageText.isNotBlank()) {
                val sep = if (parts.isEmpty()) 0 else 2
                parts += pageText
                totalChars += sep + pageText.length
            }
        }
        return Read(
            text = parts.joinToString("\n\n").take(maxChars),
            pages = pagesRead,
            skipped = (pageIds.size - pagesRead).coerceAtLeast(0)
        )
    }

    private suspend fun readPicture(context: Context, pageId: String): String {
        val picture = MemoPageContent.renderInkOnly(context, pageId) ?: return ""
        return try {
            MemoToTasksService.readHandwriting(picture)
        } finally {
            picture.recycle()
        }
    }
}

package com.boox.day.meetings

import android.content.Context
import com.boox.day.ai.MemoPageContent
import com.boox.day.ai.MemoToTasksService
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
     * its picture. Typed boxes count too; the meeting header and any saved recap don't.
     * @throws Exception with a plain message when no model could read a page.
     */
    suspend fun readText(context: Context, date: String, pageIds: List<String>): Read {
        val read = pageIds.take(MAX_PAGES)
        val parts = read.map { pageId ->
            val typed = com.boox.day.actionitems.MemoPageText.pageLines(context, null, pageId)
            val saved = ExportText.savedText(context, date, pageId)
            val ink = saved?.joinToString("\n") ?: if (MemoPageContent.load(context, pageId).strokes.isEmpty()) "" else readPicture(context, pageId)
            (listOf(ink) + typed).filter { it.isNotBlank() }.joinToString("\n")
        }
        return Read(parts.filter { it.isNotBlank() }.joinToString("\n\n"), read.size, pageIds.size - read.size)
    }

    private suspend fun readPicture(context: Context, pageId: String): String {
        val picture = MemoPageContent.renderWholePage(context, pageId) ?: return ""
        return try {
            MemoToTasksService.readHandwriting(picture)
        } finally {
            picture.recycle()
        }
    }
}

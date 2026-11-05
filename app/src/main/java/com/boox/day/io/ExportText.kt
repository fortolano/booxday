package com.boox.day.io

import android.content.Context
import com.boox.day.actionitems.MemoPageText
import com.boox.day.ai.MemoPageContent
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.UnifiedOcrRepository
import com.boox.day.data.db.UnifiedOcrText
import com.boox.day.data.db.PageRepository
import com.boox.day.ocr.DigitalInkService
import com.boox.day.ocr.model.OcrSource
import com.boox.day.ocr.model.UnifiedOcrStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A memo page's words for "Typed text under each page" (Nov 2026). Saved page reading exists only
 * with AI page reading on, so it is used when it's up to date; otherwise the tablet reads the page
 * itself: free, offline, no AI, and less accurate, which the heading in the PDF says.
 */
internal object ExportText {

    const val HEADING = "Typed text (may have mistakes)"

    /** [notRead]: the page has handwriting that couldn't be read (no language pack). */
    data class PageWords(val lines: List<String>, val notRead: Boolean)

    /** [ink] null when the handwriting language pack isn't on the tablet. Off the main thread. */
    suspend fun read(context: Context, date: String, pageId: String, ink: DigitalInkService?): PageWords {
        val typed = MemoPageText.pageLines(context, null, pageId)
        savedText(context, date, pageId)?.let { return PageWords(it + typed, false) }
        if (ink != null) return PageWords(MemoPageText.pageLines(context, ink, pageId), false)
        return PageWords(typed, hasInk(context, pageId))
    }

    /** The page's words, or as many rows as it has lines, for counting pages without reading them. */
    suspend fun estimate(context: Context, date: String, pageId: String): PageWords {
        val typed = MemoPageText.pageLines(context, null, pageId)
        savedText(context, date, pageId)?.let { return PageWords(it + typed, false) }
        val strokes = MemoPageContent.load(context, pageId).strokes
        return PageWords(List(MemoPageText.lines(strokes).size) { "-" } + typed, false)
    }

    /** The page's saved reading, when nothing changed on the page since. */
    internal suspend fun savedText(context: Context, date: String, pageId: String): List<String>? = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val db = AppDatabase.getDatabase(app)
        val id = UnifiedOcrText.generateId(OcrSource.MEMO, date, pageId)
        val reading = db.unifiedOcrDao().getOcrPage(id)?.takeIf { it.status == UnifiedOcrStatus.SUCCESS.name } ?: return@withContext null
        val changed = PageRepository(app).getById(pageId)?.updatedAt?.time ?: return@withContext null
        if (changed > reading.ocrAt) return@withContext null
        UnifiedOcrRepository(app).getOcrText(OcrSource.MEMO, date, pageId)?.text
            ?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }?.takeIf { it.isNotEmpty() }
    }

    private suspend fun hasInk(context: Context, pageId: String): Boolean = withContext(Dispatchers.IO) {
        AppDatabase.getDatabase(context.applicationContext).strokeDao().hasStrokesForPage(pageId)
    }
}

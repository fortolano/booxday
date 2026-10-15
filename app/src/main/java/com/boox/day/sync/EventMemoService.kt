package com.boox.day.sync

import android.content.Context
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import com.boox.day.data.PageDataManager
import com.boox.day.data.db.CalendarEvent
import com.boox.day.data.db.DayPageRepository
import com.boox.day.data.db.Page
import com.boox.day.data.db.PageRepository
import com.boox.day.data.db.StrokeDao
import com.boox.day.data.db.TextAnnotation
import com.boox.day.data.db.TextAnnotationRepository
import com.boox.day.data.db.AppDatabase
import com.boox.day.editor.DrawCanvas
import com.boox.day.utils.DateTimeFormatUtils
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * Service for creating and managing event-linked memos.
 * Allows users to create memo pages linked to Google Calendar events.
 */
class EventMemoService(context: Context) {
    companion object {
        private const val TAG = "EventMemoService"

        /** sourceType of the header's text boxes (not part of the notes themselves) */
        const val HEADER_SOURCE = "event_memo_header"

        // Header text styling (page px). Meeting mode (Oct 2026): stacked title / time / rule across the
        // page width, so it fits the Go 6 as well as the wide screens it was first laid out for
        private const val TITLE_FONT_SIZE = 52f     // Title font
        private const val TIME_FONT_SIZE = 37f      // Time font (smaller)
        private const val HEADER_FONT_WEIGHT = 700  // Bold
        private const val HEADER_Y_POSITION = 30f   // Offset from top
        private const val HEADER_X_POSITION = 50f   // Left and right margin
        private const val TITLE_MAX_LINES = 2       // Longer titles end in "…"
    }

    /** A meeting's notes: their day and pages, in the day's page order. */
    data class LinkedMemo(val date: String, val pageIds: List<String>)

    private val appContext = context.applicationContext
    private val dayPageRepository = DayPageRepository(context)
    private val pageRepository = PageRepository(context)
    private val textAnnotationRepository = TextAnnotationRepository(context)
    private val db = AppDatabase.getDatabase(context)
    private val strokeDao = db.strokeDao()
    private val textAnnotationDao = db.textAnnotationDao()

    /**
     * Result of memo creation or lookup
     */
    sealed class MemoResult {
        data class Created(val pageId: String, val date: String) : MemoResult()
        data class Existing(val pageId: String, val date: String) : MemoResult()
        data class Error(val message: String) : MemoResult()
    }

    /**
     * Check if an event has a linked memo page.
     */
    suspend fun hasLinkedMemo(eventId: String): Boolean = withContext(Dispatchers.IO) {
        pageRepository.getByLinkedEventId(eventId) != null
    }

    /**
     * Get the page ID of an event's linked memo, or null if none exists.
     */
    suspend fun getLinkedMemoPageId(eventId: String): String? = withContext(Dispatchers.IO) {
        pageRepository.getByLinkedEventId(eventId)?.id
    }

    /** All pages of an event's notes still on their day, in page order; null when it has none. */
    suspend fun getLinkedMemo(eventId: String): LinkedMemo? = withContext(Dispatchers.IO) {
        val linked = pageRepository.getAllByLinkedEventId(eventId).map { it.id }.toSet()
        if (linked.isEmpty()) return@withContext null
        val date = linked.firstNotNullOfOrNull { dayPageRepository.getDateByPageId(it) } ?: return@withContext null
        val pages = dayPageRepository.getByDate(date)?.pageIds.orEmpty().filter { it in linked }
        if (pages.isEmpty()) null else LinkedMemo(date, pages)
    }

    /**
     * Get all event IDs that have linked memos.
     * Used to show memo icons in the events grid.
     */
    suspend fun getLinkedEventIds(): Set<String> = withContext(Dispatchers.IO) {
        pageRepository.getAllLinkedEventIds()
    }

    /**
     * Create or get a memo page linked to an event.
     * - If memo already exists, returns the existing page
     * - If first page of that date is empty, uses it and links to event
     * - Otherwise creates a new page
     *
     * Adds a styled header with event title and time.
     */
    suspend fun createOrGetMemo(event: CalendarEvent): MemoResult = withContext(Dispatchers.IO) {
        try {
            // Check if memo already exists for this event (its first page still on its day)
            val existing = getLinkedMemo(event.id)
            if (existing != null) {
                Log.d(TAG, "Found existing memo for event ${event.id}: page ${existing.pageIds.first()}")
                return@withContext MemoResult.Existing(existing.pageIds.first(), existing.date)
            }

            // Get or create page for the event's date
            val pageId = dayPageRepository.getOrCreatePageForDate(event.dayId)

            // Check if the first page is empty (no strokes AND no text annotations)
            val hasStrokes = strokeDao.hasStrokesForPage(pageId)
            val hasText = textAnnotationDao.hasTextAnnotationsForPage(pageId)
            val isPageEmpty = !hasStrokes && !hasText

            val targetPageId = if (isPageEmpty) {
                // Use the existing empty page
                Log.d(TAG, "Using existing empty page $pageId for event memo")
                pageId
            } else {
                // Create a new page for this date
                val newPageId = dayPageRepository.createNewPageForDate(event.dayId)
                Log.d(TAG, "Created new page $newPageId for event memo (first page had content)")
                newPageId
            }

            // Link the page to the event
            pageRepository.setLinkedEventId(targetPageId, event.id)

            // Add the header text annotation
            addEventHeader(targetPageId, event)

            Log.i(TAG, "Created memo for event '${event.title}' on ${event.dayId}: page $targetPageId")
            MemoResult.Created(targetPageId, event.dayId)

        } catch (e: Exception) {
            Log.e(TAG, "Error creating memo for event ${event.id}", e)
            MemoResult.Error(e.message ?: "Unknown error creating memo")
        }
    }

    /**
     * Add a styled header across the page: the event title (bold, up to two lines), its time below,
     * then a separator line. Shown at once when the page is open on the canvas.
     */
    private suspend fun addEventHeader(pageId: String, event: CalendarEvent) {
        // Format the time
        val timeText = if (event.isAllDay) {
            "All day"
        } else {
            DateTimeFormatUtils.formatTimeRange(event.startTime, event.endTime)
        }

        // Memo pages span the screen width (both layouts)
        val textWidth = appContext.resources.displayMetrics.widthPixels - 2 * HEADER_X_POSITION
        val displayTitle = fitTitle(event.title.trim(), textWidth)
        val titleHeight = textHeight(displayTitle, TITLE_FONT_SIZE, bold = true, textWidth)
        val timeY = HEADER_Y_POSITION + titleHeight + 4f
        val timeHeight = textHeight(timeText, TIME_FONT_SIZE, bold = false, textWidth)

        // Title annotation (left-aligned)
        val titleAnnotation = TextAnnotation(
            id = UUID.randomUUID().toString(),
            content = displayTitle,
            x = HEADER_X_POSITION,
            y = HEADER_Y_POSITION,
            width = textWidth,
            height = titleHeight,
            fontSize = TITLE_FONT_SIZE,
            fontWeight = HEADER_FONT_WEIGHT,
            textColor = 0xFF000000.toInt(),  // Black
            backgroundColor = 0x00000000,     // Transparent
            textAlign = "left",
            padding = 0f,
            pageId = pageId,
            sourceType = HEADER_SOURCE
        )

        // Time annotation (below the title)
        val timeAnnotation = TextAnnotation(
            id = UUID.randomUUID().toString(),
            content = timeText,
            x = HEADER_X_POSITION,
            y = timeY,
            width = textWidth,
            height = timeHeight,
            fontSize = TIME_FONT_SIZE,
            fontWeight = 400,  // Normal weight for time
            textColor = 0xFF444444.toInt(),  // Dark gray for subtle contrast
            backgroundColor = 0x00000000,     // Transparent
            textAlign = "left",
            padding = 0f,
            pageId = pageId,
            sourceType = HEADER_SOURCE
        )

        // Separator line (using thin background). A space, not "": pageDrawing skips empty text boxes,
        // so with "" the line was never drawn
        val lineAnnotation = TextAnnotation(
            id = UUID.randomUUID().toString(),
            content = " ",
            x = HEADER_X_POSITION,
            y = timeY + timeHeight + 10f,
            width = textWidth,
            height = 3f,    // Thin line
            fontSize = 1f,
            fontWeight = 400,
            textColor = 0x00000000,           // Transparent text
            backgroundColor = 0xFF000000.toInt(),  // Black line (better for e-ink)
            textAlign = "left",
            padding = 0f,
            pageId = pageId,
            sourceType = HEADER_SOURCE
        )

        val header = listOf(titleAnnotation, timeAnnotation, lineAnnotation)
        header.forEach { textAnnotationRepository.insert(it) }
        // The page may already be open (the day's empty page): show the header on it now
        if (PageDataManager.isPageLoaded(pageId)) {
            withContext(Dispatchers.Main) {
                header.forEach { PageDataManager.addTextAnnotation(pageId, it) }
                PageDataManager.invalidatePreviewCache(pageId)
                DrawCanvas.forceUpdate.emit(null)
            }
        }
        Log.d(TAG, "Added header annotations to page $pageId: '$displayTitle' | '$timeText'")
    }

    /** The title as it fits in [TITLE_MAX_LINES] lines, ending in "…" when cut. */
    private fun fitTitle(title: String, width: Float): String {
        if (layout(title, TITLE_FONT_SIZE, bold = true, width).lineCount <= TITLE_MAX_LINES) return title
        var words = title.split(' ')
        while (words.size > 1) {
            words = words.dropLast(1)
            val candidate = words.joinToString(" ").trimEnd(',', ';', ':', '-') + "…"
            if (layout(candidate, TITLE_FONT_SIZE, bold = true, width).lineCount <= TITLE_MAX_LINES) return candidate
        }
        return title.take(40) + "…"
    }

    private fun textHeight(text: String, size: Float, bold: Boolean, width: Float): Float =
        layout(text, size, bold, width).height.toFloat()

    // Same text layout as TextBoxRenderer (sans-serif, includePad), so the boxes fit what's drawn
    private fun layout(text: String, size: Float, bold: Boolean, width: Float): StaticLayout {
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
            .setIncludePad(true)
            .build()
    }

    /**
     * Unlink a memo from an event (doesn't delete the page, just removes the link).
     */
    suspend fun unlinkMemo(eventId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val page = pageRepository.getByLinkedEventId(eventId)
            if (page != null) {
                pageRepository.clearLinkedEventId(page.id)
                Log.i(TAG, "Unlinked memo page ${page.id} from event $eventId")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unlinking memo", e)
            false
        }
    }
}

package com.boox.day.io

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.boox.day.data.datastore.A4_HEIGHT
import com.boox.day.data.datastore.A4_WIDTH
import com.boox.day.data.db.DayPageRepository
import com.boox.day.export.DailyNoteMarkdown
import com.boox.day.ocr.DigitalInkService
import com.boox.day.today.DayDigest
import com.boox.day.today.DayDigestLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * What the Export dialog shows for the current choices (Nov 2026): the first page with memo content,
 * drawn by the export's own code so it can be trusted, and how much the file will hold. A multi-day
 * PDF shows its first day, not its contents page, so ticking an option shows up (owner, 5 Nov 2026).
 */
internal object ExportPreview {

    /** [picture] null when there is nothing to export. */
    data class View(val picture: Bitmap?, val summary: String, val canExport: Boolean)

    private const val NOTE_WIDTH = 420f

    /** Off the main thread. [widthPx] is the picture's width on screen. */
    suspend fun make(context: Context, target: ExportTarget, choices: ExportChoices, widthPx: Int): View =
        withContext(Dispatchers.IO) {
            val days = daysOf(context, target)
            when (choices.kind) {
                ExportKind.PDF -> pdf(context, target, days, choices, widthPx)
                ExportKind.IMAGES -> images(context, target, days, widthPx)
                ExportKind.MARKDOWN -> markdown(context, target, days, choices, widthPx)
            }
        }

    private fun daysOf(context: Context, target: ExportTarget): List<ExportTarget.DayPages> = when (target) {
        is ExportTarget.Days -> target.days
        is ExportTarget.DayPages -> listOf(target)
        is ExportTarget.Page -> listOf(
            ExportTarget.DayPages(DayPageRepository(context).getDateByPageId(target.pageId).orEmpty(), listOf(target.pageId))
        )
        is ExportTarget.Book -> emptyList()
    }

    private fun nothing(target: ExportTarget) = View(null, ExportOutcome.emptyMessage(target), false)

    private suspend fun pdf(
        context: Context, target: ExportTarget, days: List<ExportTarget.DayPages>, choices: ExportChoices, widthPx: Int
    ): View {
        val engine = CompactExportEngine(context)
        val options = ExportOptions(
            layout = if (choices.fitMore) ExportLayout.COMPACT else ExportLayout.ONE_PER_PAGE,
            typedText = choices.typedText,
            eventsAndTasks = choices.eventsAndTasks
        )
        // Reading handwriting takes time, so only the first day is read; the rest is counted by its lines
        val readScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val plan = try {
            val ink = if (choices.typedText) DigitalInkService.create(context, readScope).takeIf { it.isReady() } else null
            var firstDay: String? = null
            engine.plan(days, options) { date, pageId ->
                if (firstDay == null) firstDay = date
                if (date == firstDay) ExportText.read(context, date, pageId, ink) else ExportText.estimate(context, date, pageId)
            }
        } finally {
            readScope.cancel()
        }
        if (plan.isEmpty) return nothing(target)

        val picture = createBitmap(widthPx, widthPx * A4_HEIGHT / A4_WIDTH)
        val canvas = Canvas(picture)
        canvas.drawColor(Color.WHITE)
        canvas.scale(widthPx.toFloat() / A4_WIDTH, widthPx.toFloat() / A4_WIDTH)
        engine.drawSheet(canvas, plan, plan.sheets.first(), if (plan.contents.isEmpty()) 1 else 2, asPicture = false)

        val strokes = plan.memoPages.sumOf { it.strokes }
        val bytes = ExportSize.pdf(plan.memoPages.size, strokes, plan.memoPages.sumOf { it.pictures }, choices.fitMore)
        val pages = ExportSize.count(plan.pageCount, "page")
        val summary = "${if (choices.typedText && plan.pageCount > 1) "About $pages" else pages} · ${ExportSize.label(bytes)}"
        return View(picture, summary, true)
    }

    private suspend fun images(
        context: Context, target: ExportTarget, days: List<ExportTarget.DayPages>, widthPx: Int
    ): View {
        val engine = CompactExportEngine(context)
        val measured = days.flatMap { engine.measureAllContent(it.date, it.pageIds) }
        if (measured.isEmpty()) return nothing(target)
        val first = ExportEngine(context).renderBitmapForPage(measured.first().pageId) ?: return nothing(target)
        val bytes = ExportSize.images(measured.size, measured.sumOf { it.strokes }, measured.sumOf { it.pictures })
        return View(shrink(first, widthPx), "${ExportSize.count(measured.size, "picture")} · ${ExportSize.label(bytes)}", true)
    }

    private suspend fun markdown(
        context: Context, target: ExportTarget, days: List<ExportTarget.DayPages>, choices: ExportChoices, widthPx: Int
    ): View {
        val engine = CompactExportEngine(context)
        val measured = days.map { engine.measureAllContent(it.date, it.pageIds) }.filter { it.isNotEmpty() }
        if (measured.isEmpty()) return nothing(target)
        val pages = measured.flatten()
        val notes = ExportSize.count(measured.size, "note")
        val summary = if (choices.pageImages) {
            val bytes = ExportSize.images(pages.size, pages.sumOf { it.strokes }, pages.sumOf { it.pictures })
            "$notes, ${ExportSize.count(pages.size, "picture")} · ${ExportSize.label(bytes)}"
        } else notes

        val date = pages.first().date
        val digest = DayDigestLoader(context).load(LocalDate.parse(date), withMemoText = true, withFullTaskText = true)
        val page = if (choices.pageImages) ExportEngine(context).renderBitmapForPage(pages.first().pageId) else null
        return View(notePicture(digest, page, widthPx), summary, true)
    }

    /** The note as a notes app shows it, as far as it fits. Recycles [page]. */
    private fun notePicture(digest: DayDigest, page: Bitmap?, widthPx: Int): Bitmap {
        val picture = createBitmap(widthPx, widthPx * A4_HEIGHT / A4_WIDTH)
        val canvas = Canvas(picture)
        canvas.drawColor(Color.WHITE)
        val scale = widthPx / NOTE_WIDTH
        canvas.scale(scale, scale)
        val heading = Paint().apply {
            color = Color.BLACK
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val body = Paint().apply {
            color = Color.BLACK
            textSize = 10.5f
            isAntiAlias = true
        }
        var y = 40f
        val left = 22f
        canvas.drawText(digest.date.format(NOTE_DATE), left, y, heading.apply { textSize = 17f })
        heading.textSize = 13f
        fun section(title: String, lines: List<String>) {
            if (lines.isEmpty()) return
            y += 30f
            canvas.drawText(title, left, y, heading)
            y += 3f
            lines.take(4).forEach {
                y += 15f
                canvas.drawText(it, left + 8f, y, body)
            }
        }
        section("Schedule", digest.events.map { "- ${it.title}" })
        section("Tasks", digest.tasks.map { "- [${if (it.completed) "x" else " "}] ${it.title ?: "Handwritten task"}" })
        val memos = DailyNoteMarkdown.shownPages(digest)
        if (memos.isNotEmpty()) {
            y += 30f
            canvas.drawText("Memos", left, y, heading)
            y += 10f
            if (page != null) {
                val width = NOTE_WIDTH - 2 * left
                val small = shrink(page, (width * scale * 2).toInt())
                val height = width * small.height / small.width
                canvas.drawBitmap(
                    small, null, android.graphics.RectF(left, y, left + width, y + height),
                    Paint(Paint.FILTER_BITMAP_FLAG)
                )
                small.recycle()
            } else {
                memos.first().text?.lines()?.take(12)?.forEach {
                    y += 15f
                    canvas.drawText(it.trim(), left, y, body)
                }
            }
        } else page?.recycle()
        return picture
    }

    private val NOTE_DATE = java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", java.util.Locale.ENGLISH)

    /** [source] halved until close, then to [widthPx]: one big step drops thin ink. Recycles [source]. */
    private fun shrink(source: Bitmap, widthPx: Int): Bitmap {
        var picture = source
        while (picture.width >= widthPx * 2) {
            val half = picture.scale(picture.width / 2, picture.height / 2, filter = true)
            if (half !== picture) picture.recycle()
            picture = half
        }
        val final = picture.scale(widthPx, picture.height * widthPx / picture.width, filter = true)
        if (final !== picture) picture.recycle()
        return final
    }
}

/**
 * A file's size, roughly, from how much is on the pages: fitted to real exports (29 Oct demo page,
 * 423 strokes: 296 KB in Fit more, 441 KB one memo per page, 351 KB as a PNG). "about" is meant.
 */
internal object ExportSize {
    private const val KB = 1000L
    private const val MB = 1000 * KB
    private const val PICTURE = 200 * KB

    fun pdf(memoPages: Int, strokes: Int, pictures: Int, fitMore: Boolean): Long =
        if (fitMore) memoPages * 40 * KB + strokes * 650L + pictures * PICTURE
        else memoPages * 20 * KB + strokes * 1000L + pictures * PICTURE

    fun images(pages: Int, strokes: Int, pictures: Int): Long =
        pages * 30 * KB + strokes * 750L + pictures * PICTURE

    fun label(bytes: Long): String = when {
        bytes < 950 * KB -> "about ${maxOf((bytes / (100.0 * KB)).roundToInt(), 1) * 100} KB"
        else -> "about ${maxOf((bytes.toDouble() / MB).roundToInt(), 1)} MB"
    }

    fun count(n: Int, thing: String): String = if (n == 1) "1 $thing" else "$n ${thing}s"
}

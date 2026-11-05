package com.boox.day.io

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.core.graphics.createBitmap
import com.boox.day.ai.MemoPageContent
import com.boox.day.data.datastore.A4_HEIGHT
import com.boox.day.data.datastore.A4_WIDTH
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.DayPageRepository
import com.boox.day.data.db.PageRepository
import com.boox.day.data.db.getBackgroundType
import com.boox.day.editor.drawing.drawBg
import com.boox.day.ocr.DigitalInkService
import com.boox.day.today.DayDigestLoader
import com.boox.day.today.DigestEvent
import com.boox.day.utils.ensureNotMainThread
import com.boox.day.utils.TaggedLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/**
 * PDFs of memo days (Nov 2026: any number of days, with a contents page, page numbers and the
 * optional events, tasks and typed text).
 *
 * Fit more on each page groups memo pages onto A4 pages under date headers: each is roughly half an
 * A4 page in height, so typically 2 fit. One memo per page starts each memo on its own page.
 */
class CompactExportEngine(
    private val context: Context,
    private val pageRepo: PageRepository = PageRepository(context),
    private val dayPageRepo: DayPageRepository = DayPageRepository(context)
) {
    private val log = TaggedLog("CompactExportEngine")

    /* ---------------------------- Constants ---------------------------- */

    companion object {
        // A4 at 72 DPI (PDF points)
        private const val PDF_A4_WIDTH = A4_WIDTH
        private const val PDF_A4_HEIGHT = A4_HEIGHT

        // Margins (in PDF points)
        private const val PAGE_MARGIN_TOP = 8       // Minimal margins to maximize content space
        private const val PAGE_MARGIN_BOTTOM = 10
        private const val PAGE_MARGIN_LEFT = 20
        private const val PAGE_MARGIN_RIGHT = 20

        // Derived content area
        private val A4_CONTENT_HEIGHT = PDF_A4_HEIGHT - PAGE_MARGIN_TOP - PAGE_MARGIN_BOTTOM  // 824
        private val A4_CONTENT_WIDTH = PDF_A4_WIDTH - PAGE_MARGIN_LEFT - PAGE_MARGIN_RIGHT    // 555

        // Headers and spacing (in PDF points) - minimal overhead for 2 memos per page
        private const val DATE_HEADER_HEIGHT = 18
        private const val INTER_DAY_SPACING = 8
        private const val INTER_PAGE_SPACING = 4
        private const val CONTENT_PADDING = 5

        // The tallest block that fits a PDF page under its date header
        private val MAX_BLOCK_HEIGHT = A4_CONTENT_HEIGHT - DATE_HEADER_HEIGHT - INTER_PAGE_SPACING

        // Reference width for scale factor calculation
        // Use this to ensure consistent PDF output across different screen sizes
        // Larger value = smaller content in PDF = more memos fit per page
        private const val REFERENCE_WIDTH = 1404  // Typical e-ink tablet width

        // Text sizes (in PDF points)
        private const val HEADER_TEXT_SIZE = 14f
        private const val FOOTER_TEXT_SIZE = 9f
        private const val HEADER_LINE_WIDTH = 1f

        /** Room kept for "Page 4 of 6". */
        private const val FOOTER_HEIGHT = 18

        /** White kept under a memo's last ink when something follows it. */
        private const val SLICE_MARGIN = 30

        // The contents page
        private const val CONTENTS_TITLE_SIZE = 22f
        private const val CONTENTS_SUBTITLE_SIZE = 12f
        private const val CONTENTS_ROW_SIZE = 12.5f
        private const val CONTENTS_FIRST_ROW = 150f
        private const val CONTENTS_MAX_ROW_STEP = 32f
        private const val CONTENTS_SIDE = 40f
    }

    /* ---------------------------- Data Classes ---------------------------- */

    /** How far a memo page's content reaches, in page pixels. */
    internal data class MeasuredPage(
        val date: String,              // "2025-12-15"
        val pageId: String,
        val pageIndexInDay: Int,       // 0, 1, 2... (which page within that day)
        val totalPagesInDay: Int,      // Total pages for this day
        val left: Int,                 // Left edge of the picture
        val width: Int,                // Width of the picture
        val contentTop: Int,           // Top of actual content
        val contentBottom: Int,        // Bottom of actual content
        val frameTop: Int = 0,         // Top of the whole page
        val frameBottom: Int = 0,      // Bottom of the whole page
        val strokes: Int = 0,
        val pictures: Int = 0,
        val blankRows: List<Float> = emptyList()
    )

    /** A memo page, or a part of one too tall for a PDF page. Drawn when its PDF page is. */
    internal data class ContentBlock(
        val page: MeasuredPage,
        val top: Int,                  // Top of this block (page pixels)
        val bottom: Int,               // Bottom of this block (page pixels)
        val contentHeight: Int         // Scaled height for PDF layout
    ) : SheetBlock {
        override val date: String get() = page.date
        override val height: Int get() = contentHeight
    }

    /**
     * Output page containing multiple content blocks.
     */
    internal data class OutputPage(
        val blocks: MutableList<SheetBlock> = mutableListOf(),
        var usedHeight: Int = 0,
        val capacity: Int = A4_CONTENT_HEIGHT
    ) {
        val remainingHeight: Int get() = capacity - usedHeight
    }

    /** How the pages of a PDF are set out. */
    internal data class SheetStyle(
        val marginTop: Int,
        val marginBottom: Int,
        val dateHeaders: Boolean,
        val footer: Boolean
    ) {
        val capacity: Int get() = PDF_A4_HEIGHT - marginTop - marginBottom - if (footer) FOOTER_HEIGHT else 0
    }

    /** A whole PDF, worked out before anything is drawn. */
    internal data class SheetPlan(
        val style: SheetStyle,
        val sheets: List<OutputPage>,
        val scaleFactor: Float,
        /** Each day and its first PDF page; empty when the file has no contents page. */
        val contents: List<Pair<String, Int>>,
        /** Memo pages whose handwriting couldn't be typed. */
        val pagesNotRead: Int,
        val memoPages: List<MeasuredPage>
    ) {
        val pageCount: Int get() = sheets.size + if (contents.isEmpty()) 0 else 1
        val isEmpty: Boolean get() = sheets.isEmpty()
    }

    /** Reads a memo page's words for the typed text. */
    internal fun interface WordsReader {
        suspend fun read(date: String, pageId: String): ExportText.PageWords
    }

    /* ---------------------------- Public API ---------------------------- */

    /** Writes [days] as one PDF. [target] is what the user chose, for the messages. */
    suspend fun exportDays(
        target: ExportTarget,
        days: List<ExportTarget.DayPages>,
        folderUri: Uri,
        baseFileName: String,
        options: ExportOptions
    ): ExportOutcome = withContext(Dispatchers.IO) {
        ensureNotMainThread("ExportPdf")
        log.i("Starting PDF export: ${days.size} days, ${options.layout}")

        val readScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val plan = try {
            val ink = if (options.typedText) DigitalInkService.create(context, readScope).takeIf { it.isReady() } else null
            plan(days, options) { date, pageId -> ExportText.read(context, date, pageId, ink) }
        } finally {
            readScope.cancel()
        }
        if (plan.isEmpty) return@withContext ExportOutcome.Empty(ExportOutcome.emptyMessage(target))
        log.i("Packed ${plan.memoPages.size} memo pages into ${plan.pageCount} PDF pages")

        // One picture at a time
        val saved = ExportFiles.save(
            context, folderUri, "$baseFileName.pdf", "application/pdf", options.overwrite,
            fallback = options.fallbackFolderUri,
            finish = { file -> options.pdfTitle?.let { PdfTitle.set(file, it) } }
        ) { out ->
            val pdfDocument = PdfDocument()
            try {
                var pageNumber = 1
                if (plan.contents.isNotEmpty()) {
                    drawOnNewPage(pdfDocument, pageNumber++) { canvas ->
                        drawContents(canvas, plan, options.datesLabel)
                        drawPageFooter(canvas, 1, plan.pageCount)
                    }
                }
                plan.sheets.forEachIndexed { index, sheet ->
                    val number = pageNumber++
                    drawOnNewPage(pdfDocument, number) { canvas ->
                        drawSheet(canvas, plan, sheet, number, asPicture = true)
                    }
                    options.onProgress?.invoke(index + 1, plan.sheets.size)
                }
                pdfDocument.writeTo(out)
            } finally {
                pdfDocument.close()
            }
        }
        val message = ExportFolderLabel.savedMessage(saved.displayName, ExportFolderLabel.of(saved.folder))
        ExportOutcome.Saved(
            listOf(saved.uri), "application/pdf",
            ExportOutcome.withNotes(message, handwritingNotRead = plan.pagesNotRead > 0, movedToDefault = saved.folder != folderUri)
        )
    }

    private suspend fun drawOnNewPage(document: PdfDocument, number: Int, draw: suspend (Canvas) -> Unit) {
        val page = document.startPage(PdfDocument.PageInfo.Builder(PDF_A4_WIDTH, PDF_A4_HEIGHT, number).create())
        try {
            page.canvas.drawColor(Color.WHITE)
            draw(page.canvas)
        } finally {
            document.finishPage(page)
        }
    }

    /* ---------------------------- Planning ---------------------------- */

    /**
     * Works out every PDF page without drawing one, so the contents page and "Page 4 of 6" know the
     * numbers and the dialog can show the count. [words] is asked only with typed text on.
     */
    internal suspend fun plan(
        days: List<ExportTarget.DayPages>,
        options: ExportOptions,
        words: WordsReader
    ): SheetPlan {
        val measured = days.map { measureAllContent(it.date, it.pageIds) }.filter { it.isNotEmpty() }
        val compact = options.layout == ExportLayout.COMPACT
        val manyDays = measured.size > 1
        val headers = compact || manyDays || options.hasExtras
        val style = SheetStyle(
            marginTop = if (headers) PAGE_MARGIN_TOP else 0,
            marginBottom = if (compact) PAGE_MARGIN_BOTTOM else 0,
            dateHeaders = headers,
            footer = manyDays
        )
        val all = measured.flatten()
        if (all.isEmpty()) return SheetPlan(style, emptyList(), 1f, emptyList(), 0, emptyList())

        val scaleFactor = scaleFactorFor(all)
        val tallest = style.capacity - DATE_HEADER_HEIGHT - INTER_PAGE_SPACING
        var notRead = 0
        val blocks = mutableListOf<SheetBlock>()
        for (day in measured) {
            val date = day.first().date
            if (options.eventsAndTasks) dayInfo(date, startsSheet = !compact)?.let { blocks += it }
            for (page in day) {
                if (compact) blocks += contentBlocks(listOf(page), scaleFactor, tallest) else blocks += pageSlice(page)
                if (options.typedText) {
                    val text = words.read(date, page.pageId)
                    if (text.notRead) notRead++
                    if (text.lines.isNotEmpty()) {
                        blocks += TextFlowBlock(date, ExportText.HEADING, text.lines.map { TextRow(it) })
                    }
                }
            }
        }

        val sheets = packContentBlocks(blocks, style.capacity, style.dateHeaders)
        val contents = if (!manyDays) emptyList() else sheets.flatMapIndexed { index, sheet ->
            sheet.blocks.map { it.date }.distinct().map { it to index + 2 }  // the contents page is page 1
        }.distinctBy { it.first }
        return SheetPlan(style, sheets, scaleFactor, contents, notRead, all)
    }

    /** A memo page at full page width, down to its last ink. */
    private fun pageSlice(page: MeasuredPage) = PageSliceBlock(
        date = page.date,
        pageId = page.pageId,
        left = page.left.toFloat(),
        top = page.frameTop.toFloat(),
        bottom = minOf(page.frameBottom, page.contentBottom + SLICE_MARGIN).toFloat(),
        scale = PDF_A4_WIDTH.toFloat() / page.width,
        startsSheet = true,
        blankRows = page.blankRows
    )

    private suspend fun dayInfo(date: String, startsSheet: Boolean): DayInfoBlock? {
        val digest = DayDigestLoader(context).load(LocalDate.parse(date), withFullTaskText = true)
        val use24Hour = GlobalAppSettings.current.timeFormat == AppSettings.TimeFormat.TwentyFourHour
        val events = digest.events.map { TextRow("${eventTime(it, use24Hour)}  ${it.title.trim()}") }
        val tasks = digest.tasks.map { task ->
            val text = (task.fullText ?: task.title)?.lines()?.firstOrNull { it.isNotBlank() }?.trim()
            TextRow((if (task.isSubtask) "  " else "") + (text ?: "Handwritten task (not read yet)"), task.completed)
        }
        return if (events.isEmpty() && tasks.isEmpty()) null else DayInfoBlock(date, events, tasks, startsSheet)
    }

    private fun eventTime(event: DigestEvent, use24Hour: Boolean): String {
        if (event.allDay) return "All day"
        val pattern = DateTimeFormatter.ofPattern(if (use24Hour) "H:mm" else "h:mm a", Locale.ENGLISH)
        return Instant.ofEpochMilli(event.startMs).atZone(ZoneId.systemDefault()).format(pattern)
    }

    /* ---------------------------- Content Measurement ---------------------------- */

    /**
     * Measure the given pages. Empty ones are skipped. Text boxes and file chips count: a page of
     * typed text alone used to be left out, and a meeting's title was cropped off above its notes.
     */
    internal suspend fun measureAllContent(date: String, pageIds: List<String>): List<MeasuredPage> {
        val measured = mutableListOf<MeasuredPage>()
        pageIds.forEachIndexed { index, pageId ->
            val page = MemoPageContent.load(context, pageId)
            val bounds = MemoPageRenderer.contentBounds(page)
            if (bounds == null) {
                log.d("Skipping empty page: $pageId")
                return@forEachIndexed
            }
            val frame = MemoPageRenderer.frame(page)
            measured += MeasuredPage(
                date = date,
                pageId = pageId,
                pageIndexInDay = index,
                totalPagesInDay = pageIds.size,
                left = floor(frame.left).toInt(),
                width = frame.width,
                contentTop = floor(bounds.top).toInt() - CONTENT_PADDING,
                contentBottom = ceil(bounds.bottom).toInt() + CONTENT_PADDING,
                frameTop = floor(frame.top).toInt(),
                frameBottom = floor(frame.top).toInt() + frame.height,
                strokes = page.strokes.size,
                pictures = page.images.size,
                blankRows = MemoPageRenderer.blankRows(page)
            )
        }
        return measured
    }

    /** Fits the widest page. At least [REFERENCE_WIDTH], so 2 memos fit per page. */
    internal fun scaleFactorFor(pages: List<MeasuredPage>): Float =
        A4_CONTENT_WIDTH.toFloat() / maxOf(REFERENCE_WIDTH, pages.maxOf { it.width })

    /** Each page as one block, or several when it is too tall for a PDF page. Ink is never cut off. */
    internal fun contentBlocks(
        pages: List<MeasuredPage>,
        scaleFactor: Float,
        maxHeight: Int = MAX_BLOCK_HEIGHT
    ): List<ContentBlock> {
        val tallestBlock = floor(maxHeight / scaleFactor).toInt()
        return pages.flatMap { page ->
            (page.contentTop until page.contentBottom step tallestBlock).map { top ->
                val bottom = minOf(top + tallestBlock, page.contentBottom)
                ContentBlock(page, top, bottom, ceil((bottom - top) * scaleFactor).toInt())
            }
        }
    }

    /** One block as a picture, on the page's background. The caller recycles it. */
    internal suspend fun renderBlock(block: ContentBlock): Bitmap {
        val content = MemoPageContent.load(context, block.page.pageId)
        val row = pageRepo.getById(block.page.pageId) ?: throw IOException("Page not found")
        val bitmap = createBitmap(block.page.width, (block.bottom - block.top).coerceAtLeast(1))
        try {
            val canvas = Canvas(bitmap)
            // White background
            canvas.drawColor(Color.WHITE)
            val corner = Offset(block.page.left.toFloat(), block.top.toFloat())
            drawBg(context, canvas, row.getBackgroundType(), row.background, corner, showMargin = false)
            MemoPageRenderer.drawMemoPage(context, canvas, content, corner)
        } catch (e: Throwable) {
            bitmap.recycle()
            throw e
        }
        return bitmap
    }

    /* ---------------------------- Bin Packing Algorithm ---------------------------- */

    /**
     * Pack blocks into output pages, day by day. A block that doesn't fit moves to the next page or,
     * when it can be cut, runs on to it; a day that goes on to a new page gets its date header again.
     */
    internal fun packContentBlocks(
        blocks: List<SheetBlock>,
        capacity: Int = A4_CONTENT_HEIGHT,
        dateHeaders: Boolean = true
    ): List<OutputPage> {
        val pages = mutableListOf<OutputPage>()
        var currentPage = OutputPage(capacity = capacity)
        pages.add(currentPage)

        fun nextPage() {
            currentPage = OutputPage(capacity = capacity)
            pages.add(currentPage)
        }

        for ((date, dayBlocks) in blocks.groupBy { it.date }.toSortedMap()) {
            var index = 0
            var carried: SheetBlock? = null  // goes first on the next page
            while (carried != null || index < dayBlocks.size) {
                val block = carried ?: dayBlocks[index++]
                carried = null
                if (block.startsSheet && currentPage.blocks.isNotEmpty() && !currentPage.blocks.last().keepWithNext) nextPage()

                val header = if (dateHeaders && currentPage.blocks.none { it.date == date }) DATE_HEADER_HEIGHT else 0
                val needed = block.height + header + INTER_PAGE_SPACING

                // The block after this one has to fit too, or both go to the next page
                val follower = if (block.keepWithNext) dayBlocks.getOrNull(index) else null
                if (follower != null && !follower.startsSheet && currentPage.blocks.isNotEmpty() &&
                    needed + follower.height + INTER_PAGE_SPACING > currentPage.remainingHeight
                ) {
                    nextPage()
                    carried = block
                    continue
                }

                if (needed <= currentPage.remainingHeight) {
                    currentPage.blocks.add(block)
                    currentPage.usedHeight += needed
                    continue
                }
                val parts = block.split(currentPage.remainingHeight - header - INTER_PAGE_SPACING)
                when {
                    parts != null -> {
                        currentPage.blocks.add(parts.first)
                        currentPage.usedHeight += parts.first.height + header + INTER_PAGE_SPACING
                        nextPage()
                        carried = parts.second
                    }
                    currentPage.blocks.isNotEmpty() -> {
                        nextPage()
                        carried = block
                    }
                    // Taller than a page and can't be cut: it gets a page to itself
                    else -> {
                        currentPage.blocks.add(block)
                        currentPage.usedHeight += needed
                    }
                }
            }

            // Spacing after a day, if there's more days coming
            currentPage.usedHeight += INTER_DAY_SPACING
        }

        return pages.filter { it.blocks.isNotEmpty() }
    }

    /* ---------------------------- PDF Rendering ---------------------------- */

    /**
     * Draws one PDF page's content on [canvas], which is A4 in PDF points.
     *
     * @param asPicture Fit more on each page draws each memo as a picture, as it always has. The
     *     dialog's small preview draws the same thing as vectors, which stay sharp that small.
     */
    internal suspend fun drawSheet(canvas: Canvas, plan: SheetPlan, sheet: OutputPage, pageNumber: Int, asPicture: Boolean) {
        var currentY = plan.style.marginTop.toFloat()
        var lastDate: String? = null

        for (block in sheet.blocks) {
            if (block.date != lastDate) {
                if (lastDate != null) currentY += INTER_DAY_SPACING
                if (plan.style.dateHeaders) currentY = drawDateHeader(canvas, block.date, currentY)
                lastDate = block.date
            }
            when (block) {
                is ContentBlock -> if (asPicture) {
                    val bitmap = renderBlock(block)
                    try {
                        drawContentBitmap(canvas, bitmap, currentY, plan.scaleFactor)
                    } finally {
                        bitmap.recycle()
                    }
                } else {
                    drawMemo(
                        canvas, block.page.pageId, PAGE_MARGIN_LEFT.toFloat(), currentY, plan.scaleFactor,
                        Offset(block.page.left.toFloat(), block.top.toFloat()),
                        block.page.width, block.bottom - block.top
                    )
                }
                is PageSliceBlock -> drawMemo(
                    canvas, block.pageId, 0f, currentY, block.scale, Offset(block.left, block.top),
                    ceil(PDF_A4_WIDTH / block.scale).toInt(), ceil(block.bottom - block.top).toInt()
                )
                is DayInfoBlock -> block.draw(canvas, currentY)
                is TextFlowBlock -> block.draw(canvas, currentY)
            }
            currentY += block.height + INTER_PAGE_SPACING
        }

        if (plan.style.footer) drawPageFooter(canvas, pageNumber, plan.pageCount)
    }

    /** Part of a memo page as vectors: [corner] of the page at ([x], [y]), [scale] from page pixels to points. */
    private suspend fun drawMemo(
        canvas: Canvas, pageId: String, x: Float, y: Float, scale: Float, corner: Offset, width: Int, height: Int
    ) {
        val content = MemoPageContent.load(context, pageId)
        val row = pageRepo.getById(pageId) ?: throw IOException("Page not found")
        canvas.save()
        try {
            canvas.translate(x, y)
            canvas.scale(scale, scale)
            canvas.clipRect(0, 0, width, height)
            drawBg(context, canvas, row.getBackgroundType().resolveForExport(null), row.background, corner, scale, showMargin = false)
            MemoPageRenderer.drawMemoPage(context, canvas, content, corner)
        } finally {
            canvas.restore()
        }
    }

    /** The first page of a file with several days: the dates, then each day and its page. */
    private fun drawContents(canvas: Canvas, plan: SheetPlan, dates: String?) {
        val title = Paint().apply {
            color = Color.BLACK
            textSize = CONTENTS_TITLE_SIZE
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val body = Paint().apply {
            color = Color.BLACK
            textSize = CONTENTS_ROW_SIZE
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }
        val number = Paint(body).apply { textAlign = Paint.Align.RIGHT }
        val line = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 0.5f
        }
        canvas.drawText("BooxDay notes", CONTENTS_SIDE, 78f, title)
        dates?.let { canvas.drawText(it, CONTENTS_SIDE, 102f, Paint(body).apply { textSize = CONTENTS_SUBTITLE_SIZE }) }

        val right = PDF_A4_WIDTH - CONTENTS_SIDE
        val room = PDF_A4_HEIGHT - CONTENTS_FIRST_ROW - FOOTER_HEIGHT * 2
        val step = minOf(CONTENTS_MAX_ROW_STEP, room / plan.contents.size)
        plan.contents.forEachIndexed { index, (date, page) ->
            val y = CONTENTS_FIRST_ROW + index * step
            canvas.drawText(contentsDate(date), CONTENTS_SIDE, y, body)
            canvas.drawText(page.toString(), right, y, number)
            canvas.drawLine(CONTENTS_SIDE, y + step * 0.28f, right, y + step * 0.28f, line)
        }
    }

    private fun contentsDate(date: String): String =
        LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH))

    /**
     * Draw date header: ▬▬▬ December 14, 2025 ▬▬▬
     */
    private fun drawDateHeader(canvas: Canvas, date: String, y: Float): Float {
        val formattedDate = formatDateForHeader(date)

        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = HEADER_TEXT_SIZE
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val linePaint = Paint().apply {
            color = Color.DKGRAY
            strokeWidth = HEADER_LINE_WIDTH
            isAntiAlias = true
        }

        val centerX = PDF_A4_WIDTH / 2f
        val textWidth = textPaint.measureText(formattedDate)
        val lineGap = 12f
        val lineY = y + DATE_HEADER_HEIGHT / 2

        // Left line
        canvas.drawLine(
            PAGE_MARGIN_LEFT.toFloat(), lineY,
            centerX - textWidth / 2 - lineGap, lineY,
            linePaint
        )

        // Date text
        canvas.drawText(formattedDate, centerX, y + DATE_HEADER_HEIGHT - 10, textPaint)

        // Right line
        canvas.drawLine(
            centerX + textWidth / 2 + lineGap, lineY,
            (PDF_A4_WIDTH - PAGE_MARGIN_RIGHT).toFloat(), lineY,
            linePaint
        )

        return y + DATE_HEADER_HEIGHT
    }

    /**
     * Draw the content bitmap, scaled to fit.
     */
    private fun drawContentBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        y: Float,
        scaleFactor: Float
    ): Float {
        // Source rect (full bitmap)
        val srcRect = Rect(0, 0, bitmap.width, bitmap.height)

        // Destination rect (scaled to PDF dimensions)
        val destWidth = (bitmap.width * scaleFactor).toInt()
        val destHeight = (bitmap.height * scaleFactor).toInt()
        val destRect = Rect(
            PAGE_MARGIN_LEFT,
            y.toInt(),
            PAGE_MARGIN_LEFT + destWidth,
            y.toInt() + destHeight
        )

        canvas.drawBitmap(bitmap, srcRect, destRect, null)

        return y + destHeight
    }

    /** "Page 4 of 6", bottom right. */
    private fun drawPageFooter(canvas: Canvas, pageNumber: Int, pageCount: Int) {
        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = FOOTER_TEXT_SIZE
            typeface = Typeface.DEFAULT
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("Page $pageNumber of $pageCount", PDF_A4_WIDTH - CONTENTS_SIDE, PDF_A4_HEIGHT - 14f, textPaint)
    }

    /**
     * Format date for header display: "Sunday, December 14, 2025"
     */
    private fun formatDateForHeader(dateString: String): String {
        return try {
            val date = LocalDate.parse(dateString)
            val formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH)
            date.format(formatter)
        } catch (e: Exception) {
            log.w("Failed to parse date: $dateString")
            dateString
        }
    }
}

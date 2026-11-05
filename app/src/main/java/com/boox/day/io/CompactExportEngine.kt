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
import com.boox.day.data.db.DayPageRepository
import com.boox.day.data.db.PageRepository
import com.boox.day.data.db.getBackgroundType
import com.boox.day.editor.drawing.drawBg
import com.boox.day.utils.ensureNotMainThread
import com.boox.day.utils.TaggedLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Compact Export Engine for intelligent PDF export.
 *
 * Groups multiple memo pages onto single A4 pages with date headers and sub-page numbers,
 * maximizing space efficiency. Each memo page is roughly half an A4 page in height,
 * so typically 2 memos fit per A4 page.
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
        private const val SUBPAGE_LABEL_HEIGHT = 0  // Disabled - causes too much overhead
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
        private const val SUBPAGE_TEXT_SIZE = 11f
        private const val FOOTER_TEXT_SIZE = 10f
        private const val HEADER_LINE_WIDTH = 1f
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
        val contentBottom: Int         // Bottom of actual content
    )

    /** A memo page, or a part of one too tall for a PDF page. Drawn when its PDF page is. */
    internal data class ContentBlock(
        val page: MeasuredPage,
        val top: Int,                  // Top of this block (page pixels)
        val bottom: Int,               // Bottom of this block (page pixels)
        val contentHeight: Int         // Scaled height for PDF layout
    ) {
        val date: String get() = page.date
        val pageIndexInDay: Int get() = page.pageIndexInDay
        val totalPagesInDay: Int get() = page.totalPagesInDay
    }

    /**
     * Output page containing multiple content blocks.
     */
    internal data class OutputPage(
        val blocks: MutableList<ContentBlock> = mutableListOf(),
        var usedHeight: Int = 0
    ) {
        val remainingHeight: Int get() = A4_CONTENT_HEIGHT - usedHeight
    }

    /* ---------------------------- Public API ---------------------------- */

    /**
     * Export pages in compact PDF format.
     *
     * @param target The export target (must be DayPages)
     * @param folderUri Target folder URI
     * @param baseFileName Base filename without extension
     * @param options Export options
     * @return Result message
     */
    suspend fun exportCompactPdf(
        target: ExportTarget.DayPages,
        folderUri: Uri,
        baseFileName: String,
        options: ExportOptions
    ): ExportOutcome = withContext(Dispatchers.IO) {
        ensureNotMainThread("CompactExportPdf")

        log.i("Starting compact PDF export for date: ${target.date}, pages: ${target.pageIds.size}")

        // Phase 1: Measure all content (no pictures yet: a whole day of them would not fit in memory)
        val measured = measureAllContent(target.date, target.pageIds)

        if (measured.isEmpty()) {
            return@withContext ExportOutcome.Empty(
                if (target.pageIds.size == 1) ExportOutcome.EMPTY_PAGE_MESSAGE else ExportOutcome.EMPTY_PAGES_MESSAGE
            )
        }

        // Phase 2: Pack content blocks into output pages
        val scaleFactor = scaleFactorFor(measured)
        log.d("Scale factor: $scaleFactor")
        val outputPages = packContentBlocks(contentBlocks(measured, scaleFactor))

        log.i("Packed ${measured.size} memo pages into ${outputPages.size} PDF pages")

        // Phase 3: Render PDF document, one picture at a time
        val saved = ExportFiles.save(context, folderUri, "$baseFileName.pdf", "application/pdf", options.overwrite) { out ->
            val pdfDocument = PdfDocument()
            try {
                outputPages.forEachIndexed { index, outputPage ->
                    renderOutputPage(pdfDocument, outputPage, index + 1, scaleFactor)
                }
                pdfDocument.writeTo(out)
            } finally {
                pdfDocument.close()
            }
        }
        ExportOutcome.Saved(
            listOf(saved.uri), "application/pdf",
            ExportFolderLabel.savedMessage(saved.displayName, ExportFolderLabel.of(folderUri))
        )
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
                contentBottom = ceil(bounds.bottom).toInt() + CONTENT_PADDING
            )
        }
        return measured
    }

    /** Fits the widest page. At least [REFERENCE_WIDTH], so 2 memos fit per page. */
    internal fun scaleFactorFor(pages: List<MeasuredPage>): Float =
        A4_CONTENT_WIDTH.toFloat() / maxOf(REFERENCE_WIDTH, pages.maxOf { it.width })

    /** Each page as one block, or several when it is too tall for a PDF page. Ink is never cut off. */
    internal fun contentBlocks(pages: List<MeasuredPage>, scaleFactor: Float): List<ContentBlock> {
        val tallestBlock = floor(MAX_BLOCK_HEIGHT / scaleFactor).toInt()
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
     * Pack content blocks into output pages.
     *
     * Handles the case where a single day has more content than fits on one A4 page
     * by splitting across multiple pages with continued date headers.
     */
    internal fun packContentBlocks(blocks: List<ContentBlock>): List<OutputPage> {
        val pages = mutableListOf<OutputPage>()

        // Group by date, maintaining chronological order
        val blocksByDate = blocks.groupBy { it.date }.toSortedMap()

        var currentPage = OutputPage()
        pages.add(currentPage)

        for ((date, dayBlocks) in blocksByDate) {
            val sortedDayBlocks = dayBlocks.sortedBy { it.pageIndexInDay }

            // Add each memo page one by one, creating new PDF pages as needed
            var isFirstBlockOfDay = true

            for (block in sortedDayBlocks) {
                // Calculate height needed for this single block
                val blockHeight = calculateSingleBlockHeight(block, isFirstBlockOfDay)

                // Check if this block fits on current page
                if (currentPage.blocks.isNotEmpty() && currentPage.remainingHeight < blockHeight) {
                    // Current page is full, start a new PDF page
                    currentPage = OutputPage()
                    pages.add(currentPage)
                    // On continuation pages, we still need the header
                    isFirstBlockOfDay = true  // Show header on new page too
                }

                // Add this block to current page
                addSingleBlockToPage(currentPage, block, isFirstBlockOfDay)
                isFirstBlockOfDay = false
            }

            // Add inter-day spacing after completing a day (if there's more days coming)
            currentPage.usedHeight += INTER_DAY_SPACING
        }

        return pages.filter { it.blocks.isNotEmpty() }
    }

    /**
     * Calculate height needed for a single content block.
     *
     * @param block The content block
     * @param includeHeader Whether to include date header height (true for first block of day on a page)
     */
    private fun calculateSingleBlockHeight(block: ContentBlock, includeHeader: Boolean): Int {
        var height = block.contentHeight

        // Add header height if this is first block of day on this page
        if (includeHeader) {
            height += DATE_HEADER_HEIGHT
        }

        // Add sub-page label height if multi-page day
        if (block.totalPagesInDay > 1) {
            height += SUBPAGE_LABEL_HEIGHT
        }

        // Add spacing
        height += INTER_PAGE_SPACING

        return height
    }

    /**
     * Add a single content block to a page.
     */
    private fun addSingleBlockToPage(page: OutputPage, block: ContentBlock, isFirstBlockOfDay: Boolean) {
        // Mark if this block needs a header when rendering
        // We track this by checking if it's the first block with this date on this page
        page.blocks.add(block)
        page.usedHeight += calculateSingleBlockHeight(block, isFirstBlockOfDay)
    }

    // Keep track of whether header was drawn for tracking during render
    // This is handled in renderOutputPage by checking lastDate

    /* ---------------------------- PDF Rendering ---------------------------- */

    /**
     * Render an output page to the PDF document.
     */
    private suspend fun renderOutputPage(
        pdfDocument: PdfDocument,
        page: OutputPage,
        pageNumber: Int,
        scaleFactor: Float
    ) {
        val pageInfo = PdfDocument.PageInfo.Builder(PDF_A4_WIDTH, PDF_A4_HEIGHT, pageNumber).create()
        val pdfPage = pdfDocument.startPage(pageInfo)
        val canvas = pdfPage.canvas

        // White background
        canvas.drawColor(Color.WHITE)

        var currentY = PAGE_MARGIN_TOP.toFloat()
        var lastDate: String? = null

        for (block in page.blocks) {
            // Draw date header if new date
            if (block.date != lastDate) {
                if (lastDate != null) {
                    // Add spacing between days
                    currentY += INTER_DAY_SPACING
                }
                currentY = drawDateHeader(canvas, block.date, currentY)
                lastDate = block.date
            }

            // Sub-page labels disabled to maximize content space
            // The date header is sufficient to identify content

            // Draw cropped content bitmap
            val bitmap = renderBlock(block)
            try {
                currentY = drawContentBitmap(canvas, bitmap, currentY, scaleFactor)
            } finally {
                bitmap.recycle()
            }

            // Add spacing between pages
            currentY += INTER_PAGE_SPACING
        }

        // No footer - keep pages clean

        pdfDocument.finishPage(pdfPage)
    }

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
     * Draw sub-page label: "Page 1"
     */
    private fun drawSubPageLabel(canvas: Canvas, pageNum: Int, y: Float): Float {
        val textPaint = Paint().apply {
            color = Color.GRAY
            textSize = SUBPAGE_TEXT_SIZE
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        canvas.drawText("Page $pageNum", PAGE_MARGIN_LEFT.toFloat(), y + SUBPAGE_LABEL_HEIGHT - 6, textPaint)

        return y + SUBPAGE_LABEL_HEIGHT
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

    /**
     * Draw page footer with page number.
     */
    private fun drawPageFooter(canvas: Canvas, pageNumber: Int) {
        val textPaint = Paint().apply {
            color = Color.GRAY
            textSize = FOOTER_TEXT_SIZE
            typeface = Typeface.DEFAULT
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val footerY = PDF_A4_HEIGHT - PAGE_MARGIN_BOTTOM / 2f
        canvas.drawText("Page $pageNumber", PDF_A4_WIDTH / 2f, footerY, textPaint)
    }

    /**
     * Format date for header display: "December 14, 2025"
     */
    private fun formatDateForHeader(dateString: String): String {
        return try {
            val date = LocalDate.parse(dateString)
            val formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)
            date.format(formatter)
        } catch (e: Exception) {
            log.w("Failed to parse date: $dateString")
            dateString
        }
    }
}

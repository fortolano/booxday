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
import com.boox.day.SCREEN_WIDTH
import com.boox.day.data.datastore.A4_HEIGHT
import com.boox.day.data.datastore.A4_WIDTH
import com.boox.day.data.db.DayPageRepository
import com.boox.day.data.db.Image
import com.boox.day.data.db.Page
import com.boox.day.data.db.PageRepository
import com.boox.day.data.db.Stroke
import com.boox.day.data.db.getBackgroundType
import com.boox.day.editor.drawing.drawBg
import com.boox.day.editor.drawing.drawImage
import com.boox.day.editor.drawing.drawStroke
import com.boox.day.utils.ensureNotMainThread
import com.boox.day.utils.TaggedLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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

    /**
     * Content measurement result for a single memo page.
     */
    private data class ContentBlock(
        val date: String,              // "2025-12-15"
        val pageId: String,
        val pageIndexInDay: Int,       // 0, 1, 2... (which page within that day)
        val totalPagesInDay: Int,      // Total pages for this day
        val contentTop: Int,           // Top of actual content (pixels, unscaled)
        val contentBottom: Int,        // Bottom of actual content (pixels, unscaled)
        val contentHeight: Int,        // Scaled height for PDF layout
        val bitmap: Bitmap             // Pre-rendered content bitmap
    )

    /**
     * Output page containing multiple content blocks.
     */
    private data class OutputPage(
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
    ): String = withContext(Dispatchers.IO) {
        ensureNotMainThread("CompactExportPdf")

        log.i("Starting compact PDF export for date: ${target.date}, pages: ${target.pageIds.size}")

        // Phase 1: Measure all content blocks
        // Use the larger of actual screen width or reference width to ensure 2 memos fit per page
        val effectiveWidth = maxOf(SCREEN_WIDTH, REFERENCE_WIDTH)
        val scaleFactor = A4_CONTENT_WIDTH.toFloat() / effectiveWidth.toFloat()
        log.d("Scale factor: $scaleFactor (effectiveWidth=$effectiveWidth, screenWidth=$SCREEN_WIDTH)")
        val contentBlocks = measureAllContent(target.date, target.pageIds, scaleFactor)

        if (contentBlocks.isEmpty()) {
            return@withContext "No content to export"
        }

        // Phase 2: Pack content blocks into output pages
        val outputPages = packContentBlocks(contentBlocks, scaleFactor)

        log.i("Packed ${contentBlocks.size} memo pages into ${outputPages.size} PDF pages")

        // Phase 3: Render PDF document
        val pdfDocument = PdfDocument()
        try {
            outputPages.forEachIndexed { index, outputPage ->
                renderOutputPage(pdfDocument, outputPage, index + 1, scaleFactor)
            }

            // Save PDF
            val result = savePdfDocument(pdfDocument, folderUri, baseFileName, options)

            // Recycle bitmaps
            contentBlocks.forEach { it.bitmap.recycle() }

            result
        } finally {
            pdfDocument.close()
        }
    }

    /* ---------------------------- Content Measurement ---------------------------- */

    /**
     * Measure all content blocks for the given pages.
     */
    private fun measureAllContent(
        date: String,
        pageIds: List<String>,
        scaleFactor: Float
    ): List<ContentBlock> {
        val blocks = mutableListOf<ContentBlock>()
        val totalPages = pageIds.size

        pageIds.forEachIndexed { index, pageId ->
            val block = measureContentBlock(
                pageId = pageId,
                date = date,
                pageIndexInDay = index,
                totalPagesInDay = totalPages,
                scaleFactor = scaleFactor
            )
            if (block != null) {
                blocks.add(block)
            }
        }

        return blocks
    }

    /**
     * Measure a single content block.
     */
    private fun measureContentBlock(
        pageId: String,
        date: String,
        pageIndexInDay: Int,
        totalPagesInDay: Int,
        scaleFactor: Float
    ): ContentBlock? {
        // Fetch page data
        val (page, strokes) = pageRepo.getWithStrokeById(pageId)
        val (_, images) = pageRepo.getWithImageById(pageId)

        // Skip empty pages
        if (strokes.isEmpty() && images.isEmpty()) {
            log.d("Skipping empty page: $pageId")
            return null
        }

        // Find actual content bounds
        val contentTop = findContentTop(strokes, images)
        val contentBottom = findContentBottom(strokes, images)

        // Render the page content to bitmap
        val bitmap = renderPageBitmap(page, strokes, images, contentTop, contentBottom)

        // Calculate scaled height for PDF layout
        val rawHeight = contentBottom - contentTop + CONTENT_PADDING * 2
        val scaledHeight = (rawHeight * scaleFactor).toInt()

        return ContentBlock(
            date = date,
            pageId = pageId,
            pageIndexInDay = pageIndexInDay,
            totalPagesInDay = totalPagesInDay,
            contentTop = contentTop,
            contentBottom = contentBottom,
            contentHeight = scaledHeight,
            bitmap = bitmap
        )
    }

    private fun findContentTop(strokes: List<Stroke>, images: List<Image>): Int {
        val strokeTop = strokes.minOfOrNull { it.top.toInt() } ?: Int.MAX_VALUE
        val imageTop = images.minOfOrNull { it.y } ?: Int.MAX_VALUE
        val minTop = minOf(strokeTop, imageTop)
        return if (minTop == Int.MAX_VALUE) 0 else (minTop - CONTENT_PADDING).coerceAtLeast(0)
    }

    private fun findContentBottom(strokes: List<Stroke>, images: List<Image>): Int {
        val strokeBottom = strokes.maxOfOrNull { it.bottom.toInt() } ?: 0
        val imageBottom = images.maxOfOrNull { it.y + it.height } ?: 0
        return maxOf(strokeBottom, imageBottom) + CONTENT_PADDING
    }

    /**
     * Render a page to a cropped bitmap (only the content area).
     */
    private fun renderPageBitmap(
        page: Page,
        strokes: List<Stroke>,
        images: List<Image>,
        contentTop: Int,
        contentBottom: Int
    ): Bitmap {
        val contentHeight = contentBottom - contentTop
        val bitmap = createBitmap(SCREEN_WIDTH, contentHeight.coerceAtLeast(100))
        val canvas = Canvas(bitmap)

        // White background
        canvas.drawColor(Color.WHITE)

        // Draw background pattern (offset by contentTop)
        val bgOffset = Offset(0f, contentTop.toFloat())
        drawBg(context, canvas, page.getBackgroundType(), page.background, bgOffset)

        // Draw images (offset by contentTop)
        val contentOffset = Offset(0f, -contentTop.toFloat())
        images.forEach { drawImage(context, canvas, it, contentOffset) }

        // Draw strokes (offset by contentTop)
        strokes.forEach { drawStroke(canvas, it, contentOffset) }

        return bitmap
    }

    /* ---------------------------- Bin Packing Algorithm ---------------------------- */

    /**
     * Pack content blocks into output pages.
     *
     * Handles the case where a single day has more content than fits on one A4 page
     * by splitting across multiple pages with continued date headers.
     */
    private fun packContentBlocks(
        blocks: List<ContentBlock>,
        scaleFactor: Float
    ): List<OutputPage> {
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
                if (currentPage.remainingHeight < blockHeight) {
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
    private fun renderOutputPage(
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
            currentY = drawContentBitmap(canvas, block, currentY, scaleFactor)

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
        block: ContentBlock,
        y: Float,
        scaleFactor: Float
    ): Float {
        val bitmap = block.bitmap

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

    /* ---------------------------- File Saving ---------------------------- */

    /**
     * Save PDF document to the target folder.
     */
    private fun savePdfDocument(
        pdfDocument: PdfDocument,
        folderUri: Uri,
        baseFileName: String,
        options: ExportOptions
    ): String {
        val fileName = "$baseFileName.pdf"

        return try {
            when (folderUri.scheme) {
                "file" -> {
                    val folder = File(requireNotNull(folderUri.path))
                    if (!folder.exists()) folder.mkdirs()
                    val file = File(folder, fileName)
                    FileOutputStream(file).use { out ->
                        pdfDocument.writeTo(out)
                    }
                    log.i("Saved compact PDF: ${file.absolutePath}")
                    ExportFolderLabel.savedMessage(fileName, ExportFolderLabel.of(folderUri))
                }
                else -> {
                    // For content:// URIs, use ContentResolver
                    val resolver = context.contentResolver
                    val destUri = createFileInDirectory(folderUri, fileName, "application/pdf")
                    if (destUri != null) {
                        resolver.openOutputStream(destUri)?.use { out ->
                            pdfDocument.writeTo(out)
                        }
                        log.i("Saved compact PDF via ContentResolver")
                        ExportFolderLabel.savedMessage(fileName, ExportFolderLabel.of(folderUri))
                    } else {
                        "Error: Could not create file"
                    }
                }
            }
        } catch (e: Exception) {
            log.e("Failed to save PDF: ${e.message}", e)
            "Error saving PDF: ${e.message}"
        }
    }

    /**
     * Create a file in a directory URI (for SAF directories).
     */
    private fun createFileInDirectory(dirUri: Uri, fileName: String, mimeType: String): Uri? {
        return try {
            if (android.provider.DocumentsContract.isTreeUri(dirUri)) {
                val treeDocId = android.provider.DocumentsContract.getTreeDocumentId(dirUri)
                val parentDocUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(dirUri, treeDocId)
                android.provider.DocumentsContract.createDocument(
                    context.contentResolver,
                    parentDocUri,
                    mimeType,
                    fileName
                )
            } else {
                null
            }
        } catch (e: Exception) {
            log.e("Failed to create file in directory: ${e.message}")
            null
        }
    }
}

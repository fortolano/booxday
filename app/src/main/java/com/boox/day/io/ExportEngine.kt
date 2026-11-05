package com.boox.day.io

import android.content.ClipData
import android.content.ClipboardManager
import com.boox.day.BuildConfig
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import androidx.compose.ui.geometry.Offset
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import com.boox.day.TAG
import com.boox.day.ai.MemoPageContent
import com.boox.day.data.AppRepository
import com.boox.day.data.datastore.A4_HEIGHT
import com.boox.day.data.datastore.A4_WIDTH
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.BookRepository
import com.boox.day.data.db.DayPageRepository
import com.boox.day.data.db.Page
import com.boox.day.data.db.PageRepository
import com.boox.day.data.db.getBackgroundType
import com.boox.day.editor.drawing.drawBg
import com.boox.day.ui.SnackState.Companion.logAndShowError
import com.boox.day.ui.components.getFolderList
import com.boox.day.utils.ensureNotMainThread
import android.util.Log
import com.boox.day.utils.TaggedLog
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/* ---------------------------- Public API ---------------------------- */

enum class ExportFormat { PDF, PNG, JPEG, XOPP }

enum class ExportLayout {
    ONE_PER_PAGE,   // Original behavior - one memo per PDF page (may have empty space)
    COMPACT         // Smart grouping - multiple memos per page with date headers
}

sealed class ExportTarget {
    data class Book(val bookId: String) : ExportTarget()
    data class Page(val pageId: String) : ExportTarget()
    data class DayPages(val date: String, val pageIds: List<String>) : ExportTarget()
}

data class ExportOptions(
    val copyToClipboard: Boolean = true,
    val targetFolderUri: Uri? = null, // can be made to also get from it fileName.
    val overwrite: Boolean = false,   // TODO: Fix it -- for now it does not work correctly (it overwrites the files too often)
    val fileName: String? = null,
    val layout: ExportLayout = ExportLayout.COMPACT  // Default to compact for efficient PDF exports
)

class ExportEngine(
    private val context: Context,
    private val pageRepo: PageRepository = PageRepository(context),
    private val bookRepo: BookRepository = BookRepository(context),
    private val dayPageRepo: DayPageRepository = DayPageRepository(context)
) {
    private val log = TaggedLog("ExportEngine")

    private companion object {
        const val PDF_MIME_TYPE = "application/pdf"
    }

    /**
     * Exports [target]. Never throws, because callers run it in a scope with nothing to catch an
     * exception (one used to close the app): whatever goes wrong comes back as [ExportOutcome.Failed].
     */
    suspend fun export(
        target: ExportTarget, format: ExportFormat, options: ExportOptions = ExportOptions()
    ): ExportOutcome = try {
        // prepare file name and folder
        val (folderUri, baseFileName) = createFileNameAndFolder(target, format, options)
        // TODO: Retrieve all necessary data from the target, so that specific format exporter does not need to handle reading from db.
        //       For book it should be done page by page.
        when (format) {
            ExportFormat.PDF -> exportAsPdf(target, folderUri, baseFileName, options)
            ExportFormat.PNG, ExportFormat.JPEG -> exportAsImages(
                target, folderUri, baseFileName, format, options
            )

            ExportFormat.XOPP -> exportAsXopp(target, folderUri, baseFileName, options)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: OutOfMemoryError) {
        log.e("Export ran out of memory", e)
        ExportOutcome.Failed(ExportOutcome.TOO_BIG_MESSAGE)
    } catch (e: Exception) {
        log.e("Export failed: ${e.message}", e)
        ExportOutcome.Failed(ExportOutcome.FAILED_MESSAGE)
    }


    /* -------------------- PDF EXPORT -------------------- */

    private suspend fun exportAsPdf(
        target: ExportTarget, folderUri: Uri, baseFileName: String, options: ExportOptions
    ): ExportOutcome {
        // Use CompactExportEngine for COMPACT layout with DayPages or single Page
        if (options.layout == ExportLayout.COMPACT) {
            when (target) {
                is ExportTarget.DayPages -> {
                    return CompactExportEngine(context, pageRepo, dayPageRepo).exportCompactPdf(
                        target = target,
                        folderUri = folderUri,
                        baseFileName = baseFileName,
                        options = options
                    )
                }
                is ExportTarget.Page -> {
                    // For single page with COMPACT, convert to DayPages target
                    val dayDate = dayPageRepo.getDateByPageId(target.pageId)
                    if (dayDate != null) {
                        val dayPagesTarget = ExportTarget.DayPages(
                            date = dayDate,
                            pageIds = listOf(target.pageId)
                        )
                        return CompactExportEngine(context, pageRepo, dayPageRepo).exportCompactPdf(
                            target = dayPagesTarget,
                            folderUri = folderUri,
                            baseFileName = baseFileName,
                            options = options
                        )
                    }
                    // If not a day page, fall through to original behavior
                }
                else -> {
                    // Book export: fall through to original behavior
                }
            }
        }

        // Empty pages are left out, as in the compact layout; a book keeps all of its pages
        val pageIds = when (target) {
            is ExportTarget.Book -> pagesOf(target)
            else -> pagesOf(target).filter { hasContent(it) }
        }
        if (pageIds.isEmpty()) return emptyOutcome(target)

        val saved = ExportFiles.save(context, folderUri, "$baseFileName.pdf", PDF_MIME_TYPE, options.overwrite) { out ->
            PdfDocument().use { doc ->
                var nextPageNumber = 1
                pageIds.forEach { pageId -> nextPageNumber = writePageToPdfDocument(doc, pageId, nextPageNumber) }
                doc.writeTo(out)
            }
        }
        if (options.copyToClipboard && target is ExportTarget.Page) copyPagePngLink(
            context, target.pageId
        ) // You may want a separate PDF variant

        return ExportOutcome.Saved(
            listOf(saved.uri), PDF_MIME_TYPE,
            ExportFolderLabel.savedMessage(saved.displayName, ExportFolderLabel.of(folderUri))
        )
    }

    /* -------------------- IMAGE EXPORT (PNG / JPEG) -------------------- */

    private suspend fun exportAsImages(
        target: ExportTarget,
        folderUri: Uri,
        baseFileName: String,
        format: ExportFormat,
        options: ExportOptions
    ): ExportOutcome {
        val (ext, mime, compressFormat) = when (format) {
            ExportFormat.PNG -> Triple("png", "image/png", Bitmap.CompressFormat.PNG)
            ExportFormat.JPEG -> Triple("jpg", "image/jpeg", Bitmap.CompressFormat.JPEG)
            else -> error("Unsupported image format")
        }

        val saved = mutableListOf<ExportFiles.Saved>()
        var failed = 0
        pagesOf(target).forEachIndexed { index, pageId ->
            val bitmap = renderBitmapForPage(pageId) ?: return@forEachIndexed
            // Each page separately, with numbered file names, except a single page
            val fileName = if (target is ExportTarget.Page) baseFileName else "$baseFileName-p${index + 1}"
            try {
                saved += ExportFiles.save(context, folderUri, "$fileName.$ext", mime, options.overwrite) { out ->
                    if (!bitmap.compress(compressFormat, 100, out)) throw IOException("Image not written")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
                log.e("Page image not saved: ${e.message}", e)
            } finally {
                bitmap.recycle()
            }
        }

        return when {
            saved.isEmpty() && failed == 0 -> emptyOutcome(target)
            saved.isEmpty() -> ExportOutcome.Failed(ExportOutcome.FAILED_MESSAGE)
            else -> {
                if (options.copyToClipboard && format == ExportFormat.PNG && target is ExportTarget.Page) {
                    copyPagePngLink(context, target.pageId)
                }
                ExportOutcome.Saved(
                    saved.map { it.uri }, mime,
                    ExportFolderLabel.savedPagesMessage(saved.map { it.displayName }, failed, ExportFolderLabel.of(folderUri))
                )
            }
        }
    }

    /** The pages of [target], in order. */
    private fun pagesOf(target: ExportTarget): List<String> = when (target) {
        is ExportTarget.Book -> bookRepo.getById(target.bookId)?.pageIds ?: throw IOException("Book ID not found")
        is ExportTarget.Page -> listOf(target.pageId)
        is ExportTarget.DayPages -> target.pageIds
    }

    private suspend fun hasContent(pageId: String): Boolean =
        MemoPageRenderer.hasContent(MemoPageContent.load(context, pageId))

    private fun emptyOutcome(target: ExportTarget) = ExportOutcome.Empty(
        if (target is ExportTarget.Page) ExportOutcome.EMPTY_PAGE_MESSAGE else ExportOutcome.EMPTY_PAGES_MESSAGE
    )

    /* -------------------- XOPP export -------------------- */

    private suspend fun exportAsXopp(
        target: ExportTarget,
        folderUri: Uri,
        baseFileName: String,
        options: ExportOptions
    ): ExportOutcome {
        val mime = "application/x-xopp"
        val saved = ExportFiles.save(context, folderUri, "$baseFileName.xopp", mime, options.overwrite) { out ->
            XoppFile(context).writeToXoppStream(target, out)
        }
        return ExportOutcome.Saved(
            listOf(saved.uri), mime,
            ExportFolderLabel.savedMessage(saved.displayName, ExportFolderLabel.of(folderUri))
        )
    }

    /* -------------------- File naming and folder path -------------------- */

    /**
     * Returns: Pair(folderUri, fileNameWithoutExtension)
     *
     * Rules:
     *  Book export:
     *      folder: Documents/BooxDay/<folderHierarchy>/BookTitle
     *      file:   BookTitle
     *
     *  Page export (belongs to a book):
     *      folder: Documents/BooxDay/<folderHierarchy>/BookTitle
     *      file:   BookTitle-p<PageNumber>   (falls back to BookTitle-p? if no number)
     *
     *  Day page export:
     *      folder: Documents/BooxDay/Calendar/<year-month>
     *      file:   BooxDay-<date>-p<PageNumber>
     *
     *  Quick page export:
     *      folder: Documents/BooxDay/<folderHierarchyFromPageParent?>
     *      file:   booxday-<timestamp>
     *
     * - If options.saveToUri is provided, it must point to a directory (tree/document folder Uri or file:// directory).
     */
    fun createFileNameAndFolder(
        target: ExportTarget, format: ExportFormat, options: ExportOptions
    ): Pair<Uri, String> {
        val fileName =
            sanitizeFileName(options.fileName?.trim()?.takeIf { it.isNotBlank() } ?: createFileName(
                target
            ))

        // If caller provided a directory Uri, accept both SAF directory and file:// directory.
        options.targetFolderUri?.let { provided ->
            if (!isDirectoryUri(provided) && !isFileDirectory(provided)) {
                throw IllegalArgumentException(
                    "ExportOptions.targetFolderUri must point to a directory (SAF tree/document folder or file:// directory). Maybe folder was deleted?"
                )
            }
            return provided to fileName
        }

        // Default export directory under Documents/BooxDay/<subfolder>
        val subfolderPath = createSubfolderName(target, format)
        val folderUri = getDefaultExportDirectoryUri(subfolderPath)
        return folderUri to fileName
    }

    /**
     * Builds a subfolder path relative to the "BooxDay" export root.
     *
     * Rules:
     * - Book (PDF/XOPP): folder hierarchy of the book.
     * - Book (PNG/JPEG): folder hierarchy + a folder for the book itself.
     * - Page (in a book): folder hierarchy + a folder for the book itself.
     * - Day page: "Calendar/<year-month>" subfolder (e.g., "Calendar/2024-11")
     * - Quick Page: folder hierarchy of the page.
     *
     * @return A path without leading/trailing slashes, or an empty string.
     */
    fun createSubfolderName(target: ExportTarget, format: ExportFormat): String {
        // Helper to build a full folder hierarchy path from a parent folder ID.
        fun buildFolderPath(parentFolderId: String?): String {
            return parentFolderId?.let {
                // Fetches folder hierarchy and joins their sanitized titles with "/".
                getFolderList(context, it)
                    .reversed()
                    .joinToString("/") { folder -> sanitizeFileName(folder.title) }
            }.orEmpty()
        }

        return when (target) {
            is ExportTarget.Book -> {
                val book = bookRepo.getById(target.bookId)
                    ?: run { log.e("Book ID not found"); return "" }

                val basePath = buildFolderPath(book.parentFolderId)
                val bookTitleFolder = sanitizeFileName(book.title)

                // For image formats, create an extra subfolder named after the book.
                if (format == ExportFormat.PNG || format == ExportFormat.JPEG) {
                    listOfNotNull(basePath.takeIf { it.isNotEmpty() }, bookTitleFolder)
                        .joinToString("/")
                } else {
                    basePath
                }
            }

            is ExportTarget.Page -> {
                val page = pageRepo.getById(target.pageId)
                    ?: run { log.e("Page ID not found"); return "" }

                // Check if the page belongs to a book.
                val book = page.notebookId?.let { bookRepo.getById(it) }

                if (book != null) {
                    // Page is inside a book: create path from the book's hierarchy + book title.
                    val basePath = buildFolderPath(book.parentFolderId)
                    val bookTitleFolder = sanitizeFileName(book.title)
                    listOfNotNull(basePath.takeIf { it.isNotEmpty() }, bookTitleFolder)
                        .joinToString("/")
                } else {
                    // Check if this is a day page
                    val dayDate = dayPageRepo.getDateByPageId(target.pageId)
                    if (dayDate != null) {
                        // Day page: organize by year-month under Calendar folder
                        val yearMonth = dayDate.substring(0, 7)  // "2024-11" from "2024-11-24"
                        "Calendar/$yearMonth"
                    } else {
                        // This is a "Quick Page": use its own folder hierarchy.
                        buildFolderPath(page.parentFolderId)
                    }
                }
            }

            is ExportTarget.DayPages -> {
                // Day pages: organize by year-month under Calendar folder
                val yearMonth = target.date.substring(0, 7)  // "2024-11" from "2024-11-24"
                "Calendar/$yearMonth"
            }
        }
    }


    // Create a default directory Uri under Documents/BooxDay/<subfolderPath> using file:// scheme.
    private fun getDefaultExportDirectoryUri(subfolderPath: String): Uri {
        val documentsDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)

        val targetPath = listOfNotBlank("BooxDay", subfolderPath).joinToString(File.separator)
        val dir = File(documentsDir, targetPath)
        if (!dir.exists()) dir.mkdirs()
        return dir.toUri()
    }

    /**
     * Returns: fileNameWithoutExtension
     *
     * Book export: BookTitle
     * Page export in book: BookTitle-p<PageNumber> (or p?)
     * Day page: BooxDay-<date>-p<PageNumber> (e.g., BooxDay-2024-11-24-p1)
     * Quick page: booxday-<timestamp>
     */
    fun createFileName(target: ExportTarget): String {
        return when (target) {
            is ExportTarget.Book -> {
                val book =
                    bookRepo.getById(target.bookId) ?: run { log.e("Book ID not found"); return "" }
                sanitizeFileName(book.title)
            }

            is ExportTarget.Page -> {
                val page =
                    pageRepo.getById(target.pageId) ?: run { log.e("Page ID not found"); return "" }

                val book = page.notebookId?.let { bookRepo.getById(it) }

                if (book != null) {
                    // Page inside a book
                    val bookTitle = sanitizeFileName(book.title)
                    val pageNumber = getPageNumber(page.notebookId, page.id)?.plus(1)
                    val pageToken = if ((pageNumber ?: 0) >= 1) "p$pageNumber" else "p_"
                    "$bookTitle-$pageToken"
                } else {
                    // Check if this is a day page
                    val dayDate = dayPageRepo.getDateByPageId(target.pageId)
                    if (dayDate != null) {
                        // Day page - use date and page index
                        val pageIndex = dayPageRepo.getPageIndexForDay(target.pageId)?.plus(1) ?: 1
                        "BooxDay-$dayDate-p$pageIndex"
                    } else {
                        // Quick page (neither book nor day)
                        val timeStamp = getReadableUtcTimestamp()
                        "booxday-$timeStamp"
                    }
                }
            }

            is ExportTarget.DayPages -> {
                // Multi-page day export - use date as base name
                "BooxDay-${target.date}"
            }
        }
    }

    /* -------------------- Shared Drawing & PDF Helpers -------------------- */

    /** Writes a page from PDF page [firstPageNumber] on, and returns the number after its last. */
    private suspend fun writePageToPdfDocument(doc: PdfDocument, pageId: String, firstPageNumber: Int): Int {
        ensureNotMainThread("ExportPdf")
        val data = fetchPageData(pageId)
        val frame = MemoPageRenderer.frame(data.content)

        val scaleFactor = A4_WIDTH.toFloat() / frame.width
        val scaledHeight = (frame.height * scaleFactor).toInt()
        var pageNumber = firstPageNumber

        if (GlobalAppSettings.current.paginatePdf) {
            var currentTop = 0
            while (currentTop < scaledHeight) {
                val pageInfo =
                    PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageNumber).create()
                val page = doc.startPage(pageInfo)
                drawPage(
                    canvas = page.canvas,
                    data = data,
                    frame = frame,
                    scroll = Offset(0f, currentTop.toFloat()),
                    scaleFactor = scaleFactor
                )
                doc.finishPage(page)
                currentTop += A4_HEIGHT
                pageNumber++
            }
        } else {
            val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, scaledHeight, pageNumber).create()
            val page = doc.startPage(pageInfo)
            drawPage(
                canvas = page.canvas, data = data, frame = frame, scroll = Offset.Zero, scaleFactor = scaleFactor
            )
            doc.finishPage(page)
            pageNumber++
        }
        return pageNumber
    }

    /** The whole page at its own size, or null when there is nothing to draw. */
    internal suspend fun renderBitmapForPage(pageId: String, withTextBoxes: Boolean = true): Bitmap? {
        ensureNotMainThread("ExportBitmap")
        val data = fetchPageData(pageId)
        val content = if (withTextBoxes) data.content
            else data.content.copy(textAnnotations = emptyList(), attachments = emptyList())
        if (!MemoPageRenderer.hasContent(content)) return null

        val frame = MemoPageRenderer.frame(content)
        val bitmap = createBitmap(frame.width, frame.height)
        try {
            val canvas = Canvas(bitmap)
            // Scale = 1f (bitmap is native logical size)
            drawBg(context, canvas, data.page.getBackgroundType(), data.page.background, frame.corner, showMargin = false)
            MemoPageRenderer.drawMemoPage(context, canvas, content, frame.corner)
        } catch (e: Throwable) {
            bitmap.recycle()
            throw e
        }
        return bitmap
    }

    /**
     * The page's ink and images, for reading it for search. Text boxes stay out: search gets them
     * as typed, from [com.boox.day.ocr.TextAnnotationIndexer].
     *
     * MUST be called from background thread.
     *
     * @param pageId The page ID to render
     * @return Bitmap of the page content, or null if page not found
     */
    suspend fun renderBitmapForPagePublic(pageId: String): Bitmap? {
        return try {
            renderBitmapForPage(pageId, withTextBoxes = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("ExportEngine", "Failed to render bitmap for page $pageId", e)
            null
        }
    }

    private fun drawPage(
        canvas: Canvas, data: PageData, frame: MemoPageRenderer.Frame, scroll: Offset, scaleFactor: Float
    ) {
        canvas.scale(scaleFactor, scaleFactor)
        val corner = frame.corner + scroll / scaleFactor
        drawBg(
            context,
            canvas,
            data.page.getBackgroundType()
                .resolveForExport(getPageNumber(data.page.notebookId, data.page.id)),
            data.page.background,
            corner,
            scaleFactor,
            showMargin = false
        )
        MemoPageRenderer.drawMemoPage(context, canvas, data.content, corner)
    }

    /* -------------------- Data Fetch -------------------- */

    private data class PageData(val page: Page, val content: MemoPageContent.Page)

    /** The page as it is now: what's on screen and not saved yet included. */
    private suspend fun fetchPageData(pageId: String): PageData {
        val page = pageRepo.getById(pageId) ?: throw IOException("Page not found")
        return PageData(page, MemoPageContent.load(context, pageId))
    }

    /* -------------------- Clipboard Helpers -------------------- */

    private fun copyPagePngLink(context: Context, pageId: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = """
            [[../attachments/BooxDay/Pages/booxday-page-$pageId.png]]
            [[BooxDay Link][booxday://page-$pageId]]
        """.trimIndent()
        clipboard.setPrimaryClip(ClipData.newPlainText("BooxDay Page Link", text))
    }

    private fun copyBookPdfLink(context: Context, bookId: String, bookName: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = """
            [[../attachments/BooxDay/Notebooks/$bookName.pdf]]
            [[BooxDay Book Link][booxday://book-$bookId]]
        """.trimIndent()
        clipboard.setPrimaryClip(ClipData.newPlainText("BooxDay Book PDF Link", text))
    }

    /* -------------------- Utilities -------------------- */

    /**
     * Gets the current time in UTC and formats it into a human-readable, filename-safe string.
     * Example output: "2025-10-11_21-48"
     */
    fun getReadableUtcTimestamp(): String {
        val currentUtcTime = ZonedDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")
        return currentUtcTime.format(formatter)
    }

    // Accepts SAF tree/document directory Uris OR file:// directory Uris
    private fun isDirectoryUri(uri: Uri): Boolean {
        // SAF tree directory
        if (android.provider.DocumentsContract.isTreeUri(uri)) return true

        // SAF document directory
        if (android.provider.DocumentsContract.isDocumentUri(context, uri)) {
            val resolver = context.contentResolver
            resolver.query(
                uri,
                arrayOf(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE),
                null,
                null,
                null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val mime = c.getString(0)
                    if (mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) {
                        return true
                    }
                }
            }
        }
        // file:// directory
        return isFileDirectory(uri)
    }

    private fun isFileDirectory(uri: Uri): Boolean {
        if (uri.scheme != "file") return false
        val path = uri.path ?: return false
        return File(path).isDirectory
    }

    // Simple PdfDocument.use extension
    private inline fun PdfDocument.use(block: (PdfDocument) -> Unit) {
        try {
            block(this)
        } finally {
            try {
                close()
            } catch (_: Exception) {
            }
        }
    }

    private fun listOfNotBlank(vararg parts: String): List<String> =
        parts.filter { it.isNotBlank() }

    // Retrieves the 0-based page number of a specific page within a book.
    // Returns null for day pages (pages without a notebookId).
    fun getPageNumber(bookId: String?, id: String): Int? {
        // Day pages don't have a book, so return null
        if (bookId == null) return null

        return try {
            AppRepository(context).getPageNumber(bookId, id)
        } catch (e: Exception) {
            log.w("getPageNumber failed: ${e.message}")
            null
        }
    }

}
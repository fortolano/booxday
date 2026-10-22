package com.boox.day.ocr.source

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.DayPageRepository
import com.boox.day.data.db.PageRepository
import com.boox.day.data.ensurePreviewsFullFolder
import com.boox.day.editor.utils.loadPersistBitmap
import com.boox.day.io.ExportEngine
import com.boox.day.ocr.model.OcrSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Bitmap source for Memo pages.
 *
 * Loads persisted bitmap previews from the file system.
 * Memos are stored as full-size PNG files in the app's internal storage.
 *
 * Storage location: {filesDir}/pages/previews/full/{pageId}-sy{scrollY}.png
 *
 * For OCR, we need bitmap content to extract text from handwritten notes.
 * The modification time is derived from either:
 * - Page.updatedAt in the database (preferred)
 * - File modification time (fallback)
 */
class MemoBitmapSource(
    private val context: Context,
    private val dayPageRepository: DayPageRepository,
    private val pageRepository: PageRepository
) : OcrBitmapSource {

    companion object {
        private const val TAG = "MemoBitmapSource"

        /** Ink ending this close under the saved bitmap is a stroke at the edge, not content below. */
        private const val BELOW_BITMAP_MARGIN_PX = 12f

        /**
         * Full screen (Oct 2026): ink or images written low on a page in full screen are not in a
         * bitmap saved from the normal view. True when the page's content reaches below the bitmap.
         */
        fun contentBelowBitmap(contentBottom: Float, bitmapHeight: Int): Boolean =
            contentBottom > bitmapHeight + BELOW_BITMAP_MARGIN_PX
    }

    override val sourceType: OcrSource = OcrSource.MEMO

    private val db by lazy { AppDatabase.getDatabase(context) }
    private val strokeDao by lazy { db.strokeDao() }
    private val imageDao by lazy { db.ImageDao() }
    private val textAnnotationDao by lazy { db.textAnnotationDao() }

    /**
     * Load the persisted bitmap for a memo page.
     *
     * Guards against stale/ghost bitmaps by verifying the page has actual content
     * (strokes or text annotations) in the database before loading the bitmap file.
     * This prevents phantom OCR results from EPD framebuffer artifacts persisted to disk.
     *
     * @param sourceId The date string (yyyy-MM-dd format)
     * @param pageId The page UUID
     * @return Loaded bitmap or null if not found or page has no real content
     */
    override suspend fun renderPage(sourceId: String, pageId: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // Guard: verify page has actual content before loading bitmap.
            // Stale bitmap files can exist on disk (e.g. from EPD framebuffer capture)
            // even when the page has 0 strokes and 0 text annotations.
            val hasStrokes = strokeDao.hasStrokesForPage(pageId)
            val hasAnnotations = if (!hasStrokes) textAnnotationDao.countByPageId(pageId) > 0 else true

            if (!hasStrokes && !hasAnnotations) {
                Log.d(TAG, "Skipping empty memo page $pageId (date=$sourceId) — no strokes or annotations")
                return@withContext null
            }

            // Load persisted bitmap from file system
            // Using requireExactMatch=false to get any cached version
            val bitmap = loadPersistBitmap(
                context = context,
                pageID = pageId,
                scroll = null,
                zoom = null,
                requireExactMatch = false
            )

            if (bitmap == null) {
                Log.d(TAG, "No persisted bitmap found for memo page $pageId (date=$sourceId)")
                return@withContext null
            }

            Log.d(TAG, "Loaded memo bitmap $pageId: ${bitmap.width}x${bitmap.height}")

            // Content below the saved bitmap (written in full screen, saved from the normal view):
            // read the whole page from the database so search finds all of it
            val contentBottom = maxOf(
                strokeDao.maxBottomForPage(pageId) ?: 0f,
                (imageDao.maxBottomForPage(pageId) ?: 0).toFloat()
            )
            if (contentBelowBitmap(contentBottom, bitmap.height)) {
                val wholePage = ExportEngine(context).renderBitmapForPagePublic(pageId)
                if (wholePage != null) {
                    Log.d(TAG, "Memo $pageId has content below its saved bitmap: using the whole page ${wholePage.width}x${wholePage.height}")
                    bitmap.recycle()
                    return@withContext wholePage
                }
            }
            bitmap

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load memo bitmap $pageId: ${e.message}", e)
            null
        }
    }

    /**
     * Get the modification time for a memo page.
     *
     * First tries to get from database (Page.updatedAt), falls back to file time.
     */
    override fun getSourceModifiedAt(sourceId: String, pageId: String): Long? {
        return try {
            // Try to get from database first
            val page = pageRepository.getById(pageId)
            if (page != null) {
                return page.updatedAt.time
            }

            // Fallback: check file modification time
            getFileModifiedAt(pageId)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get modification time for memo $pageId: ${e.message}")
            null
        }
    }

    /**
     * Get file modification time from the preview folder.
     */
    private fun getFileModifiedAt(pageId: String): Long? {
        val dir = ensurePreviewsFullFolder(context)

        // Find any file for this pageId
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith(pageId) }
        return files?.maxOfOrNull { it.lastModified() }
    }

    /**
     * Check if a memo page has a persisted bitmap.
     */
    override fun hasContent(sourceId: String, pageId: String): Boolean {
        return try {
            val dir = ensurePreviewsFullFolder(context)

            // Check if any preview file exists for this page
            val hasFile = dir.listFiles { f -> f.isFile && f.name.startsWith(pageId) }
                ?.isNotEmpty() ?: false

            hasFile

        } catch (e: Exception) {
            Log.e(TAG, "Error checking content for memo $pageId: ${e.message}")
            false
        }
    }

    /**
     * Get all page IDs for a given date.
     *
     * @param sourceId The date string (yyyy-MM-dd format)
     * @return List of page IDs for that date
     */
    override suspend fun getPageIds(sourceId: String): List<String> = withContext(Dispatchers.IO) {
        try {
            // Get DayPage for this date
            val dayPage = dayPageRepository.getByDate(sourceId)
            if (dayPage == null) {
                Log.d(TAG, "No DayPage found for date $sourceId")
                return@withContext emptyList()
            }

            // Return all page IDs
            dayPage.pageIds

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get page IDs for date $sourceId: ${e.message}")
            emptyList()
        }
    }

    /**
     * Get all dates that have memo pages within a date range.
     *
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return List of date strings with memo pages
     */
    suspend fun getDatesWithMemos(startDate: LocalDate, endDate: LocalDate): List<String> = withContext(Dispatchers.IO) {
        try {
            val formatter = DateTimeFormatter.ISO_LOCAL_DATE
            val dates = mutableListOf<String>()

            var current = startDate
            while (!current.isAfter(endDate)) {
                val dateStr = current.format(formatter)
                val dayPage = dayPageRepository.getByDate(dateStr)
                if (dayPage != null && dayPage.pageIds.isNotEmpty()) {
                    dates.add(dateStr)
                }
                current = current.plusDays(1)
            }

            Log.d(TAG, "Found ${dates.size} dates with memos in range")
            dates

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get dates with memos: ${e.message}")
            emptyList()
        }
    }

    /**
     * Get all memo pages within a date range.
     * Returns pairs of (date, pageId).
     */
    suspend fun getAllMemoPages(startDate: LocalDate, endDate: LocalDate): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            val formatter = DateTimeFormatter.ISO_LOCAL_DATE
            val results = mutableListOf<Pair<String, String>>()

            var current = startDate
            while (!current.isAfter(endDate)) {
                val dateStr = current.format(formatter)
                val dayPage = dayPageRepository.getByDate(dateStr)
                if (dayPage != null) {
                    for (pageId in dayPage.pageIds) {
                        results.add(Pair(dateStr, pageId))
                    }
                }
                current = current.plusDays(1)
            }

            Log.d(TAG, "Found ${results.size} total memo pages in range")
            results

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get all memo pages: ${e.message}")
            emptyList()
        }
    }

    /**
     * Get the most recent memo pages for OCR processing.
     *
     * @param limit Maximum number of pages to return
     * @return List of (date, pageId) pairs, ordered by most recently modified
     */
    suspend fun getRecentMemoPages(limit: Int): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            val dir = ensurePreviewsFullFolder(context)

            // Get all preview files sorted by modification time (newest first)
            val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".png") }
                ?.sortedByDescending { it.lastModified() }
                ?.take(limit * 2)  // Get extra to account for filtering
                ?: return@withContext emptyList()

            // Extract pageIds and find their dates
            val results = mutableListOf<Pair<String, String>>()
            for (file in files) {
                val pageId = extractPageIdFromFileName(file.name) ?: continue

                // Find the date for this page
                val dateForPage = getDateForMemoPage(pageId)
                if (dateForPage != null) {
                    results.add(Pair(dateForPage, pageId))
                }

                if (results.size >= limit) break
            }

            Log.d(TAG, "Found ${results.size} recent memo pages")
            results

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get recent memo pages: ${e.message}")
            emptyList()
        }
    }

    /**
     * Extract pageId from preview filename.
     * Filename format: {pageId}-sy{scrollY}.png or {pageId}.png
     */
    private fun extractPageIdFromFileName(fileName: String): String? {
        return when {
            fileName.contains("-sy") -> {
                // Format: {pageId}-sy{scrollY}.png
                val dashIndex = fileName.lastIndexOf("-sy")
                if (dashIndex > 0) fileName.substring(0, dashIndex) else null
            }
            fileName.endsWith(".png") -> {
                // Format: {pageId}.png
                fileName.removeSuffix(".png")
            }
            else -> fileName
        }
    }

    /**
     * Get the date for a memo page by scanning recent dates.
     * Returns null if the pageId doesn't belong to any memo.
     */
    private fun getDateForMemoPage(pageId: String): String? {
        return try {
            // Scan last 60 days to find which date has this pageId
            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(60)
            val formatter = DateTimeFormatter.ISO_LOCAL_DATE

            var current = startDate
            while (!current.isAfter(endDate)) {
                val dateStr = current.format(formatter)
                val dayPage = dayPageRepository.getByDate(dateStr)
                if (dayPage != null && dayPage.pageIds.contains(pageId)) {
                    return dateStr
                }
                current = current.plusDays(1)
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error finding date for memo page $pageId: ${e.message}")
            null
        }
    }
}

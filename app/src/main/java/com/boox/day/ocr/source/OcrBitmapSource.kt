package com.boox.day.ocr.source

import android.graphics.Bitmap
import com.boox.day.ocr.model.OcrSource

/**
 * Interface for rendering bitmaps from different content sources for OCR processing.
 *
 * Each source type (Boox Notes, Memos, Tasks) has its own implementation
 * that knows how to retrieve or render the bitmap for that content.
 *
 * IMPORTANT: Implementations must:
 * - Return null gracefully when content doesn't exist or can't be rendered
 * - NOT recycle the returned bitmap (caller is responsible)
 * - Be thread-safe (may be called from background threads)
 */
interface OcrBitmapSource {

    /**
     * The source type this implementation handles.
     */
    val sourceType: OcrSource

    /**
     * Render or load a page as a bitmap for OCR processing.
     *
     * @param sourceId The parent identifier (noteId for Boox Notes, date for Memos/Tasks)
     * @param pageId The page identifier within the source
     * @return Bitmap ready for OCR, or null if rendering failed
     *
     * IMPORTANT: Caller is responsible for recycling the returned bitmap.
     */
    suspend fun renderPage(sourceId: String, pageId: String): Bitmap?

    /**
     * Get the last modification timestamp for a page.
     *
     * Used to detect if content has changed since last OCR run.
     * The timestamp format varies by source:
     * - Boox Notes: Shape file modification time (epoch ms)
     * - Memos/Tasks: Page updatedAt or file modification time (epoch ms)
     *
     * @param sourceId The parent identifier
     * @param pageId The page identifier
     * @return Modification timestamp in epoch milliseconds, or null if unknown
     */
    fun getSourceModifiedAt(sourceId: String, pageId: String): Long?

    /**
     * Check if this source has any content to process.
     *
     * @param sourceId The parent identifier
     * @param pageId The page identifier
     * @return true if content exists and can be rendered
     */
    fun hasContent(sourceId: String, pageId: String): Boolean

    /**
     * Get all page IDs for a given source.
     *
     * @param sourceId The parent identifier
     * @return List of page IDs, or empty list if none found
     */
    suspend fun getPageIds(sourceId: String): List<String>
}

/**
 * Result from bitmap rendering with metadata.
 */
data class BitmapRenderResult(
    val bitmap: Bitmap,
    val width: Int,
    val height: Int,
    val sourceModifiedAt: Long
)

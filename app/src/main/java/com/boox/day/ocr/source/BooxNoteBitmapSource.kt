package com.boox.day.ocr.source

import android.graphics.Bitmap
import android.util.Log
import com.boox.day.booxnotes.BooxNative
import com.boox.day.ocr.model.OcrSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer

/**
 * Bitmap source for Boox Notes content.
 *
 * Uses BooxNative JNI to render pages from the .ksync storage.
 * This is a direct port of the rendering logic from the old OcrPipeline.
 *
 * Storage location: /storage/emulated/0/.ksync/
 * - document/{noteId}/shape/ - Stroke metadata (modification time used for staleness check)
 * - point/{noteId}/{pageId}/ - Raw stroke coordinates
 */
class BooxNoteBitmapSource : OcrBitmapSource {

    companion object {
        private const val TAG = "BooxNoteBitmapSource"
    }

    override val sourceType: OcrSource = OcrSource.BOOX_NOTE

    /**
     * Render a Boox Notes page to bitmap using native JNI.
     *
     * @param sourceId The note's uniqueId
     * @param pageId The page identifier
     * @return Android Bitmap or null if rendering failed
     */
    override suspend fun renderPage(sourceId: String, pageId: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (!BooxNative.isLibraryLoaded) {
                Log.w(TAG, "BooxNative library not loaded: ${BooxNative.libraryLoadError}")
                return@withContext null
            }

            // Use BooxNative JNI to render the page
            val booxBitmap = BooxNative.renderPageOnDevice(
                BooxNative.KSYNC_BASE_PATH,
                sourceId,
                pageId
            )

            if (booxBitmap == null || !booxBitmap.isValid()) {
                Log.w(TAG, "BooxNative returned null or invalid bitmap for $sourceId/$pageId")
                return@withContext null
            }

            // Convert BooxBitmap to Android Bitmap
            val androidBitmap = Bitmap.createBitmap(
                booxBitmap.width,
                booxBitmap.height,
                Bitmap.Config.ARGB_8888
            )
            androidBitmap.copyPixelsFromBuffer(ByteBuffer.wrap(booxBitmap.buffer))

            Log.d(TAG, "Rendered page $sourceId/$pageId: ${booxBitmap.width}x${booxBitmap.height}")
            androidBitmap

        } catch (e: Exception) {
            Log.e(TAG, "Failed to render page $sourceId/$pageId: ${e.message}", e)
            null
        }
    }

    /**
     * Get shape file modification time for staleness detection.
     *
     * The shape files in .ksync/document/{noteId}/shape/ contain stroke metadata.
     * When the user modifies a note, these files are updated.
     */
    override fun getSourceModifiedAt(sourceId: String, pageId: String): Long? {
        return try {
            val shapeDir = File("${BooxNative.KSYNC_BASE_PATH}/document/$sourceId/shape")
            if (!shapeDir.exists() || !shapeDir.isDirectory) {
                return null
            }

            // Find shape files for this page
            // Shape files are named like: {pageId}#{strokeId}#{timestamp}.zip
            val shapeFiles = shapeDir.listFiles { file ->
                file.isFile && file.name.startsWith(pageId)
            }

            // Return the most recent modification time among all shape files for this page
            shapeFiles?.maxOfOrNull { it.lastModified() }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get modification time for $sourceId/$pageId: ${e.message}")
            null
        }
    }

    /**
     * Check if the note/page has renderable content in .ksync.
     */
    override fun hasContent(sourceId: String, pageId: String): Boolean {
        return try {
            if (!BooxNative.isLibraryLoaded) return false

            // Check if point files exist for this page
            val pointDir = File("${BooxNative.KSYNC_BASE_PATH}/point/$sourceId/$pageId")
            if (!pointDir.exists() || !pointDir.isDirectory) {
                return false
            }

            // Check for actual point files
            val hasPoints = pointDir.listFiles()?.any { it.name.endsWith("#points") } ?: false
            hasPoints

        } catch (e: Exception) {
            Log.e(TAG, "Error checking content for $sourceId/$pageId: ${e.message}")
            false
        }
    }

    /**
     * Get all page IDs for a note by scanning the point directory.
     */
    override suspend fun getPageIds(sourceId: String): List<String> = withContext(Dispatchers.IO) {
        try {
            if (!BooxNative.isLibraryLoaded) {
                return@withContext emptyList()
            }

            // Use BooxNative helper function
            BooxNative.getPageIdsFromPointDirectory(sourceId)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get page IDs for $sourceId: ${e.message}")
            emptyList()
        }
    }

    /**
     * Get all notes on device by scanning .ksync/document.
     *
     * Returns list of note uniqueIds that have local data AND have titles.
     * Notes without titles ("orphaned" notes) cannot be opened via Boox APIs
     * and should not be indexed - same filter as BooxNoteIndexer uses.
     */
    suspend fun getAllNoteIds(): List<String> = withContext(Dispatchers.IO) {
        try {
            if (!BooxNative.isLibraryLoaded) {
                return@withContext emptyList()
            }

            val json = BooxNative.listAllNotesOnDevice(BooxNative.KSYNC_BASE_PATH)
            val result = JSONObject(json)

            if (!result.has("document_notes")) {
                return@withContext emptyList()
            }

            val notes = result.getJSONArray("document_notes")
            val noteIds = mutableListOf<String>()

            for (i in 0 until notes.length()) {
                val note = notes.getJSONObject(i)
                if (!note.has("unique_id")) continue

                // Filter: Only include notes with valid titles (same as BooxNoteIndexer)
                // Notes without titles are "orphaned" and can't be opened via Boox APIs
                val rawTitle = if (note.isNull("title")) null else note.optString("title")
                val title = if (rawTitle == "null" || rawTitle.isNullOrBlank()) null else rawTitle

                if (title != null) {
                    noteIds.add(note.getString("unique_id"))
                }
            }

            Log.d(TAG, "Found ${noteIds.size} titled notes on device")
            noteIds

        } catch (e: Exception) {
            Log.e(TAG, "Failed to list all notes: ${e.message}")
            emptyList()
        }
    }

    /**
     * Get note metadata for a specific note.
     *
     * @return Pair of (pageCount, hasPoints) or null if not found
     */
    fun getNoteMetadata(noteId: String): NoteMetadata? {
        return try {
            if (!BooxNative.isLibraryLoaded) return null

            val json = BooxNative.noteExistsOnDevice(BooxNative.KSYNC_BASE_PATH, noteId)
            val result = JSONObject(json)

            if (result.optString("status") != "success") return null

            NoteMetadata(
                noteId = noteId,
                exists = result.optBoolean("exists", false),
                hasShapes = result.optBoolean("has_shapes", false),
                hasPoints = result.optBoolean("has_points", false),
                shapeCount = result.optInt("shape_count", 0),
                pointDirs = result.optInt("point_dirs", 0)
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get metadata for $noteId: ${e.message}")
            null
        }
    }

    data class NoteMetadata(
        val noteId: String,
        val exists: Boolean,
        val hasShapes: Boolean,
        val hasPoints: Boolean,
        val shapeCount: Int,
        val pointDirs: Int
    )
}

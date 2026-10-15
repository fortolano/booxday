/**
 * BooxNative - JNI Bridge to Rust FFI Library for Boox Note Parsing and Rendering
 *
 * This singleton object provides the Kotlin interface to the native Rust library
 * (boox_note_android_ffi) that handles parsing and rendering of Boox handwritten notes.
 *
 * ## Architecture Overview
 *
 * ```
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │                        Android App (Kotlin)                         │
 * │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                 │
 * │  │MainActivity │  │PreviewAct.  │  │NoteDetailAct│                 │
 * │  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘                 │
 * │         │                │                │                         │
 * │         └────────────────┼────────────────┘                         │
 * │                          ▼                                          │
 * │                   ┌─────────────┐                                   │
 * │                   │ BooxNative  │  <── This file                    │
 * │                   │ (JNI calls) │                                   │
 * │                   └──────┬──────┘                                   │
 * └──────────────────────────┼──────────────────────────────────────────┘
 *                            │ JNI
 * ┌──────────────────────────┼──────────────────────────────────────────┐
 * │                          ▼                                          │
 * │              ┌─────────────────────┐                                │
 * │              │ libboox_note_android│                                │
 * │              │ _ffi.so (Rust)      │                                │
 * │              └──────────┬──────────┘                                │
 * │                         │                                           │
 * │         ┌───────────────┼───────────────┐                           │
 * │         ▼               ▼               ▼                           │
 * │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐                   │
 * │  │boox_note_   │ │boox_note_   │ │raqote       │                   │
 * │  │parser       │ │renderer     │ │(drawing)    │                   │
 * │  │(protobuf)   │ │(strokes)    │ │             │                   │
 * │  └─────────────┘ └─────────────┘ └─────────────┘                   │
 * │                     Native Libraries                                │
 * └─────────────────────────────────────────────────────────────────────┘
 * ```
 *
 * ## Two Rendering Modes
 *
 * ### 1. On-Device Mode (PRIMARY - Use this for calendar app)
 * Reads directly from Boox's .ksync storage at /storage/emulated/0/.ksync/
 * - Use: `renderPageOnDevice()`, `listPagesOnDevice()`, `noteExistsOnDevice()`
 * - Requires: MANAGE_EXTERNAL_STORAGE permission (Android 11+)
 * - Storage structure:
 *   ```
 *   /storage/emulated/0/.ksync/
 *   ├── document/{noteId}/
 *   │   ├── shape/           # Stroke metadata (ZIP with protobuf)
 *   │   ├── pageModel/pb/    # Page dimensions
 *   │   └── virtual/doc/pb/  # Document metadata
 *   └── point/{noteId}/{pageId}/
 *       └── {pageId}#{strokeId}#points  # Raw stroke coordinates
 *   ```
 *
 * ### 2. ZIP File Mode (LEGACY - For exported .note files)
 * Reads from exported .note ZIP archives
 * - Use: `renderPage()`, `listPages()`, `listNotesInFile()`
 * - File structure inside ZIP is similar but self-contained
 *
 * ## Usage Example for Calendar App Integration
 *
 * ```kotlin
 * // 1. First, query notes from content provider (see BooxNotesIntegration)
 * val notes = booxIntegration.getAllNotes()
 *
 * // 2. Check if note has on-device data
 * val existsJson = BooxNative.noteExistsOnDevice(BooxNative.KSYNC_BASE_PATH, note.uniqueId)
 * val exists = JSONObject(existsJson)
 *
 * // 3. Get page list (pageId comes from NoteReference.pageNameList JSON)
 * val pagesJson = BooxNative.listPagesOnDevice(BooxNative.KSYNC_BASE_PATH, note.uniqueId)
 *
 * // 4. Render specific page to bitmap
 * val bitmap = BooxNative.renderPageOnDevice(BooxNative.KSYNC_BASE_PATH, note.uniqueId, pageId)
 * if (bitmap != null && bitmap.width > 0) {
 *     // Convert to Android Bitmap
 *     val androidBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
 *     androidBitmap.copyPixelsFromBuffer(ByteBuffer.wrap(bitmap.buffer))
 * }
 * ```
 *
 * ## Error Handling
 *
 * - All functions return JSON strings with error details on failure
 * - renderPageOnDevice returns null if rendering fails
 * - Check logs with tag containing "Rust" for native-side errors
 *
 * ## Important Notes for Future Development
 *
 * 1. **Permission**: Must have MANAGE_EXTERNAL_STORAGE (Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
 * 2. **Threading**: JNI calls are synchronous - consider using coroutines for UI responsiveness
 * 3. **Memory**: Large notes can produce big bitmaps - consider thumbnail mode for lists
 * 4. **Page IDs**: Come from NoteReference.pageNameList JSON, not from Rust functions
 *
 * @see BooxBitmap The return type for render functions
 * @see BooxNotesIntegration For querying note metadata from Boox content provider
 */
package com.boox.day.booxnotes

import android.util.Log
import org.json.JSONObject

object BooxNative {
    /** Whether the native library loaded successfully */
    var isLibraryLoaded: Boolean = false
        private set

    /** Error message if library failed to load, null otherwise */
    var libraryLoadError: String? = null
        private set

    init {
        // Load the native Rust library at class initialization time.
        // The library is compiled for aarch64 (ARM64) architecture used by Boox devices.
        // Library location: app/src/main/jniLibs/arm64-v8a/libboox_note_android_ffi.so
        try {
            System.loadLibrary("boox_note_android_ffi")
            isLibraryLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            // Library not available on this ABI (e.g., running on x86 emulator).
            // Preview functionality will fail gracefully with null returns.
            // This allows the app to run on unsupported architectures for UI development.
            isLibraryLoaded = false
            libraryLoadError = e.message ?: "Failed to load native library"
        }
    }

    // ========================================================================
    // SECTION 1: LEGACY ZIP-BASED FUNCTIONS (For exported .note files)
    // ========================================================================
    // These functions work with .note files exported from Boox Notes app.
    // .note files are ZIP archives containing all note data in a self-contained format.
    // Use these when working with files shared/exported by users.
    // ========================================================================

    /**
     * List all notes contained in an exported .note file (ZIP archive).
     *
     * @param path Absolute path to the .note file
     * @return JSON string with array of note IDs and metadata, or error JSON
     */
    external fun listNotesInFile(path: String): String

    /**
     * List pages for a specific note within an exported .note file.
     *
     * @param path Absolute path to the .note file
     * @param noteId The note's unique identifier (from listNotesInFile)
     * @return JSON string with page list, or error JSON
     */
    external fun listPages(path: String, noteId: String): String

    /**
     * Render a page from an exported .note file to a bitmap.
     *
     * @param path Absolute path to the .note file
     * @param noteId The note's unique identifier
     * @param pageId The page identifier to render
     * @return BooxBitmap with rendered page, or null on error
     */
    external fun renderPage(path: String, noteId: String, pageId: String): BooxBitmap?

    // ========================================================================
    // SECTION 2: ON-DEVICE FUNCTIONS (For .ksync storage - PRIMARY USE)
    // ========================================================================
    // These functions read directly from Boox's internal storage at .ksync/
    // This is the RECOMMENDED approach for calendar app integration because:
    // 1. No need for user to export notes manually
    // 2. Always shows current note state (live data)
    // 3. Works with the note metadata from content provider
    //
    // IMPORTANT: Requires MANAGE_EXTERNAL_STORAGE permission on Android 11+
    // See MainActivity.checkAndRequestStoragePermission() for permission flow
    // ========================================================================

    /**
     * List pages available for a note from on-device .ksync storage.
     *
     * This function scans the .ksync directory structure to find all pages
     * associated with a note. It looks in both the shape/ and point/ directories.
     *
     * ## Return JSON Format
     * ```json
     * {
     *   "status": "success",
     *   "note_id": "0342de42e74b44bca8dc37c2422d5475",
     *   "pages": [
     *     {
     *       "page_id": "24c597e9becd446dabe50a167c01a376",
     *       "shape_file": "24c597e9...#67e8ea01...#1764471500144.zip",
     *       "has_points": true
     *     }
     *   ]
     * }
     * ```
     *
     * @param ksyncBase Base path to .ksync folder (use KSYNC_BASE_PATH constant)
     * @param uniqueId The note's uniqueId from content provider (NoteReference.uniqueId)
     * @return JSON string with page info, or error JSON with "status": "error"
     *
     * @see BooxNotesIntegration.getAllNotes For getting uniqueId values
     */
    external fun listPagesOnDevice(ksyncBase: String, uniqueId: String): String

    /**
     * Render a page directly from on-device .ksync storage to a bitmap.
     *
     * This is the PRIMARY rendering function for calendar app integration.
     * It reads stroke data from .ksync storage and renders to an ARGB bitmap.
     *
     * ## Rendering Pipeline (implemented in Rust)
     * 1. Read page dimensions from pageModel/pb/{pageId}
     * 2. Find shape files in document/{noteId}/shape/
     * 3. Parse protobuf to get stroke metadata (width, style, points_id)
     * 4. For each shape, find matching points file in point/{noteId}/{pageId}/
     * 5. Parse binary points data (x, y, pressure coordinates)
     * 6. Render strokes to raqote canvas with proper styling
     * 7. Return ARGB bitmap data wrapped in BooxBitmap
     *
     * ## Error Cases
     * - Returns null if note doesn't exist
     * - Returns null if page not found
     * - Returns null if no renderable strokes found
     * - Check logcat for detailed error messages
     *
     * @param ksyncBase Base path to .ksync folder (use KSYNC_BASE_PATH constant)
     * @param uniqueId The note's uniqueId from content provider
     * @param pageId The page ID (from NoteReference.pageNameList JSON or listPagesOnDevice)
     * @return BooxBitmap with rendered ARGB pixel data, or null on error
     *
     * @see BooxBitmap For converting to Android Bitmap
     * @see PreviewActivity For complete usage example
     */
    external fun renderPageOnDevice(ksyncBase: String, uniqueId: String, pageId: String): BooxBitmap?

    /**
     * Get detailed stroke data from a page on device as JSON.
     *
     * Use this to extract stroke information without rendering, e.g., for:
     * - Analyzing handwriting patterns
     * - Exporting stroke data to other formats
     * - Debugging rendering issues
     *
     * ## Return JSON Format
     * ```json
     * {
     *   "status": "success",
     *   "strokes": [
     *     {
     *       "stroke_id": "abc123...",
     *       "points_id": "def456...",
     *       "stroke_width": 3.5,
     *       "point_count": 150,
     *       "bbox": {"x": 100, "y": 200, "width": 300, "height": 50}
     *     }
     *   ]
     * }
     * ```
     *
     * @param ksyncBase Base path to .ksync folder
     * @param uniqueId The note's uniqueId
     * @param pageId The page ID
     * @return JSON string with stroke data or error
     */
    external fun getStrokeDataOnDevice(ksyncBase: String, uniqueId: String, pageId: String): String

    /**
     * Check if a note exists on device in .ksync storage.
     *
     * Quick existence check before attempting to render. Useful for:
     * - Filtering notes that have local data vs cloud-only
     * - Pre-validating before expensive render operations
     * - Showing availability indicators in UI
     *
     * ## Return JSON Format
     * ```json
     * {
     *   "status": "success",
     *   "note_id": "0342de42e74b44bca8dc37c2422d5475",
     *   "exists": true,
     *   "has_shapes": true,
     *   "has_points": true,
     *   "shape_count": 3,
     *   "point_dirs": 2
     * }
     * ```
     *
     * @param ksyncBase Base path to .ksync folder
     * @param uniqueId The note's uniqueId
     * @return JSON string with existence info
     */
    external fun noteExistsOnDevice(ksyncBase: String, uniqueId: String): String

    /**
     * Debug render - returns detailed info about parsing and matching.
     *
     * Diagnostic function that traces the entire rendering pipeline without
     * actually producing a bitmap. Invaluable for troubleshooting when
     * renderPageOnDevice returns null or blank.
     *
     * ## Return JSON includes:
     * - Files found at each storage location
     * - Parse results for shape protobuf
     * - Match status between shapes and points
     * - Any errors encountered at each step
     *
     * ## Example Debug Output
     * ```json
     * {
     *   "status": "success",
     *   "shape_files_found": 1,
     *   "shape_groups_parsed": 1,
     *   "shapes_in_groups": 27,
     *   "shapes_with_points_id": 27,
     *   "points_files_found": 27,
     *   "strokes_matched": 27,
     *   "strokes_rendered": 27
     * }
     * ```
     *
     * @param ksyncBase Base path to .ksync folder
     * @param uniqueId The note's uniqueId
     * @param pageId The page ID
     * @return JSON string with detailed debug info
     */
    external fun debugRenderOnDevice(ksyncBase: String, uniqueId: String, pageId: String): String

    /**
     * Get typed text content for a single on-device note.
     *
     * Boox stores typed note content under document/{noteId}/resource/data HTML files.
     * This is used as a preview/search fallback when a note has no drawable strokes
     * or the native page render cannot produce visible content.
     *
     * @param ksyncBase Base path to .ksync folder
     * @param uniqueId The note's uniqueId
     * @return Plain text content, or null if none exists / extraction fails
     */
    external fun getNoteTextContentOnDevice(ksyncBase: String, uniqueId: String): String?

    /**
     * List ALL notes on the device by scanning the filesystem directly.
     *
     * IMPORTANT: This bypasses the content provider and scans .ksync/document/
     * directly. This is necessary because the Boox content provider (NoteModel)
     * only returns Document notes (PDF/EPUB annotations, type=1) and does NOT
     * return standalone Library notes (type=0) - the actual handwritten notes.
     *
     * ## Why This Function Exists
     * The content provider query at:
     *   content://com.onyx.android.sdk.note.ContentProvider.mail/NoteModel
     * Returns ONLY Document notes (annotations on PDFs/EPUBs).
     *
     * Standalone notes created in Boox Notes app (what users actually want)
     * are stored in .ksync/document/{noteId}/ but are NOT indexed in the
     * content provider.
     *
     * ## Return JSON Format
     * ```json
     * {
     *   "total_notes": 15,
     *   "scanned_path": "/storage/emulated/0/.ksync/document",
     *   "document_notes": [
     *     {
     *       "unique_id": "0342de42e74b44bca8dc37c2422d5475",
     *       "has_document": true,
     *       "has_points": true,
     *       "shape_file_count": 3,
     *       "page_count": 2,
     *       "page_ids": ["24c597e9becd446dabe50a167c01a376", "abc123..."]
     *     }
     *   ]
     * }
     * ```
     *
     * ## Usage Example
     * ```kotlin
     * val json = BooxNative.listAllNotesOnDevice(BooxNative.KSYNC_BASE_PATH)
     * val result = JSONObject(json)
     * if (result.has("document_notes")) {
     *     val notes = result.getJSONArray("document_notes")
     *     for (i in 0 until notes.length()) {
     *         val note = notes.getJSONObject(i)
     *         val uniqueId = note.getString("unique_id")
     *         // Now can render with renderPageOnDevice(KSYNC_BASE_PATH, uniqueId, pageId)
     *     }
     * }
     * ```
     *
     * @param ksyncBase Base path to .ksync folder (use KSYNC_BASE_PATH constant)
     * @return JSON string with all filesystem notes, or error JSON
     */
    external fun listAllNotesOnDevice(ksyncBase: String): String

    // ========================================================================
    // SECTION 3: CONSTANTS
    // ========================================================================

    /**
     * Default base path for Boox .ksync storage on Android devices.
     *
     * This is where Boox Notes app stores all note data including:
     * - document/ : Note metadata, shapes (stroke definitions), page models
     * - point/    : Raw stroke coordinate data
     * - note/     : Standalone note protobuf data
     *
     * IMPORTANT: This path requires MANAGE_EXTERNAL_STORAGE permission on Android 11+
     * Use Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION to request.
     */
    const val KSYNC_BASE_PATH = "/storage/emulated/0/.ksync"

    /**
     * Compact page info parsed from listPagesOnDevice().
     *
     * This lets Kotlin make smarter recovery decisions without needing to know
     * the raw JSON structure returned by Rust.
     */
    data class OnDevicePage(
        val pageId: String,
        val shapeFiles: List<String>,
        val pointFiles: List<String>,
        val hasPageModel: Boolean
    ) {
        val hasShapes: Boolean get() = shapeFiles.isNotEmpty()
        val hasPoints: Boolean get() = pointFiles.isNotEmpty()
    }

    /**
     * Parse live page metadata from the native JSON response.
     *
     * Returns an empty list on any error so callers can safely fall back to
     * direct filesystem scanning.
     */
    fun getOnDevicePages(uniqueId: String): List<OnDevicePage> {
        if (!isLibraryLoaded) {
            return emptyList()
        }

        return try {
            val raw = listPagesOnDevice(KSYNC_BASE_PATH, uniqueId)
            if (raw.isBlank()) {
                return emptyList()
            }

            val root = JSONObject(raw)
            if (root.optString("status") == "error" || root.has("error")) {
                return emptyList()
            }

            val pages = root.optJSONArray("pages") ?: return emptyList()
            buildList {
                for (i in 0 until pages.length()) {
                    val page = pages.optJSONObject(i) ?: continue
                    val pageId = page.optString("page_id").trim()
                    if (pageId.isEmpty()) continue

                    add(
                        OnDevicePage(
                            pageId = pageId,
                            shapeFiles = jsonArrayToStrings(page.optJSONArray("shape_files")),
                            pointFiles = jsonArrayToStrings(page.optJSONArray("point_files")),
                            hasPageModel = page.optBoolean("has_page_model", false)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("BooxNative", "Failed to parse live page metadata for $uniqueId: ${e.message}")
            emptyList()
        }
    }

    /**
     * Extract page IDs from shape filenames when point directories are missing or stale.
     */
    fun getPageIdsFromShapeDirectory(uniqueId: String): List<String> {
        val shapeDir = java.io.File("$KSYNC_BASE_PATH/document/$uniqueId/shape")

        if (!shapeDir.exists() || !shapeDir.isDirectory) {
            Log.d("BooxNative", "Shape directory not found for note: $uniqueId")
            return emptyList()
        }

        val pageIds = shapeDir.listFiles()
            ?.filter { it.isFile && it.name.contains("#") }
            ?.mapNotNull { file ->
                file.name.substringBefore("#").takeIf { it.isNotBlank() }
            }
            ?.distinct()
            ?: emptyList()

        Log.d("BooxNative", "Found ${pageIds.size} pages with shape data for note $uniqueId")
        return pageIds
    }

    /**
     * Convenience helper for page recovery in Kotlin call sites.
     */
    fun getLivePageIds(uniqueId: String): List<String> =
        getOnDevicePages(uniqueId).map { it.pageId }

    // ========================================================================
    // SECTION 4: HELPER FUNCTIONS FOR DIGITAL INK RECOGNITION
    // ========================================================================
    // These functions parse raw point files to extract stroke data for
    // Google Digital Ink Recognition API (handwriting recognition).
    //
    // Unlike bitmap rendering (which throws away stroke data), these functions
    // preserve individual pen strokes with coordinates, pressure, and timing.
    // ========================================================================

    /**
     * Data class representing a single point in a stroke.
     *
     * Used for Digital Ink Recognition API which requires stroke point data.
     *
     * @property x X coordinate in pixels
     * @property y Y coordinate in pixels
     * @property pressure Pen pressure (0.0 to 1.0), not currently used by Digital Ink
     * @property timestamp Milliseconds since epoch when point was recorded
     */
    data class StrokePoint(
        val x: Float,
        val y: Float,
        val pressure: Float,
        val timestamp: Long
    )

    /**
     * Data class representing a complete stroke (sequence of points).
     *
     * @property strokeId The stroke identifier from the point filename
     * @property points List of points that make up this stroke
     */
    data class Stroke(
        val strokeId: String,
        val points: List<StrokePoint>
    )

    /**
     * Points table entry - describes a stroke's location in the file
     */
    data class PointsTableEntry(
        val strokeId: String,
        val startAddr: Int,
        val pointCount: Int,
        val flag: Int
    )

    /**
     * Parse a point file using the CORRECT format from Rust library.
     *
     * ACTUAL Binary Format (from boox-note-parser/src/points.rs):
     *
     * Header (76 bytes):
     * - 4 bytes: version (u32, big-endian)
     * - 36 bytes: page_id (UUID string)
     * - 36 bytes: points_id (UUID string)
     *
     * Point Data (middle of file, each point 16 bytes):
     * - 4 bytes: timestamp_rel (u32, big-endian)
     * - 4 bytes: x (f32, big-endian)
     * - 4 bytes: y (f32, big-endian)
     * - 1 byte: tilt_x (i8)
     * - 1 byte: tilt_y (i8)
     * - 2 bytes: pressure (u16, big-endian)
     *
     * Points Table (at END of file):
     * - Multiple entries, each 44 bytes:
     *   - 36 bytes: stroke_uuid (string)
     *   - 4 bytes: start_addr (u32, big-endian)
     *   - 4 bytes: packed (point_count in bits 31:4, flag in bits 3:0)
     * - Last 4 bytes of file: offset to points table start
     *
     * @param pointFile The point file to parse
     * @return List of strokes with properly separated point data
     */
    fun parsePointFileToStrokes(pointFile: java.io.File): List<Stroke> {
        if (!pointFile.exists() || !pointFile.canRead()) {
            android.util.Log.w("BooxNative", "Cannot read point file: ${pointFile.name}")
            return emptyList()
        }

        return try {
            val bytes = pointFile.readBytes()
            if (bytes.size < 80) {
                android.util.Log.w("BooxNative", "Point file too small: ${pointFile.name}")
                return emptyList()
            }

            val buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.BIG_ENDIAN)
            val baseTimestamp = pointFile.lastModified()

            // Read last 4 bytes to get points table offset
            buffer.position(bytes.size - 4)
            val pointsTableStart = buffer.getInt()

            if (pointsTableStart <= 76 || pointsTableStart >= bytes.size - 4) {
                android.util.Log.w("BooxNative", "Invalid points table offset: $pointsTableStart in ${pointFile.name}")
                return emptyList()
            }

            // Read points table entries (each 44 bytes)
            val pointsTableEnd = bytes.size - 4
            val entries = mutableListOf<PointsTableEntry>()

            buffer.position(pointsTableStart)
            while (buffer.position() < pointsTableEnd && buffer.remaining() >= 44) {
                // Read 36-byte stroke UUID string
                val uuidBytes = ByteArray(36)
                buffer.get(uuidBytes)
                val strokeId = String(uuidBytes, Charsets.UTF_8).trim()

                // Read start address and packed count/flag
                val startAddr = buffer.getInt()
                val packed = buffer.getInt()
                val pointCount = (packed ushr 4) and 0x0FFFFFFF
                val flag = packed and 0xF

                if (pointCount > 0 && startAddr >= 76 && startAddr < pointsTableStart) {
                    entries.add(PointsTableEntry(strokeId, startAddr, pointCount, flag))
                }
            }

            android.util.Log.d("BooxNative", "Found ${entries.size} stroke entries in points table")

            // Read points for each stroke
            val strokes = mutableListOf<Stroke>()
            for (entry in entries) {
                buffer.position(entry.startAddr)
                val points = mutableListOf<StrokePoint>()

                for (i in 0 until entry.pointCount) {
                    if (buffer.remaining() < 16) break

                    val timestampRel = buffer.getInt()
                    val x = buffer.getFloat()
                    val y = buffer.getFloat()
                    val tiltX = buffer.get()
                    val tiltY = buffer.get()
                    val pressure = buffer.getShort().toInt() and 0xFFFF

                    // Convert pressure to 0-1 range (max is 4095 based on docs)
                    val normalizedPressure = pressure.toFloat() / 4095f

                    points.add(StrokePoint(x, y, normalizedPressure, baseTimestamp + timestampRel))
                }

                if (points.isNotEmpty()) {
                    strokes.add(Stroke(entry.strokeId, points))
                }
            }

            android.util.Log.d("BooxNative", "Parsed ${strokes.size} strokes (${strokes.sumOf { it.points.size }} total points) from ${pointFile.name}")
            strokes

        } catch (e: Exception) {
            android.util.Log.e("BooxNative", "Error parsing point file: ${pointFile.name}", e)
            emptyList()
        }
    }

    /**
     * Get all page IDs for a note by scanning the point directory.
     *
     * This is a direct filesystem scan fallback that doesn't rely on the Rust FFI.
     * Each subdirectory in /storage/emulated/0/.ksync/point/{noteId}/ is a page ID.
     *
     * @param uniqueId Note unique identifier
     * @return List of page IDs that have stroke data
     */
    fun getPageIdsFromPointDirectory(uniqueId: String): List<String> {
        val pointDir = java.io.File("$KSYNC_BASE_PATH/point/$uniqueId")

        if (!pointDir.exists() || !pointDir.isDirectory) {
            android.util.Log.d("BooxNative", "Point directory not found for note: $uniqueId")
            return emptyList()
        }

        val pageIds = pointDir.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?.filter { pageDir ->
                // Only include pages that have actual point files
                val hasPoints = java.io.File("$KSYNC_BASE_PATH/point/$uniqueId/$pageDir")
                    .listFiles()
                    ?.any { it.name.endsWith("#points") } ?: false
                hasPoints
            }
            ?.sorted()  // Sort for consistent order
            ?: emptyList()

        android.util.Log.d("BooxNative", "Found ${pageIds.size} pages with stroke data for note $uniqueId")
        return pageIds
    }

    /**
     * Get all point files for a specific page.
     *
     * Point files are stored at:
     * /storage/emulated/0/.ksync/point/{noteId}/{pageId}/{pageId}#{strokeId}#points
     *
     * @param uniqueId Note unique identifier
     * @param pageId Page identifier
     * @return List of point files for the page
     */
    fun getPointFilesForPage(uniqueId: String, pageId: String): List<java.io.File> {
        val pointDir = java.io.File("$KSYNC_BASE_PATH/point/$uniqueId/$pageId")

        if (!pointDir.exists() || !pointDir.isDirectory) {
            android.util.Log.w("BooxNative", "Point directory not found: ${pointDir.absolutePath}")
            return emptyList()
        }

        val pointFiles = pointDir.listFiles()?.filter {
            it.isFile && it.name.endsWith("#points")
        } ?: emptyList()

        android.util.Log.d("BooxNative", "Found ${pointFiles.size} point files in ${pointDir.absolutePath}")
        return pointFiles
    }

    /**
     * Load all strokes for a page from point files.
     *
     * This is the main function for extracting stroke data for Digital Ink Recognition.
     *
     * CRITICAL: Each point file may contain MANY strokes (thousands of points).
     * We split them based on time gaps to get proper stroke boundaries.
     *
     * ## Usage for Digital Ink Recognition
     * ```kotlin
     * val strokes = BooxNative.loadPageStrokes(noteId, pageId)
     * val inkBuilder = Ink.builder()
     *
     * strokes.forEach { stroke ->
     *     val strokeBuilder = Ink.Stroke.builder()
     *     stroke.points.forEach { point ->
     *         strokeBuilder.addPoint(Ink.Point.create(point.x, point.y, point.timestamp))
     *     }
     *     inkBuilder.addStroke(strokeBuilder.build())
     * }
     *
     * val ink = inkBuilder.build()
     * val result = recognizer.recognize(ink).await()
     * ```
     *
     * @param uniqueId Note unique identifier
     * @param pageId Page identifier
     * @return List of strokes with parsed point data (properly split by time gaps)
     */
    fun loadPageStrokes(uniqueId: String, pageId: String): List<Stroke> {
        val pointFiles = getPointFilesForPage(uniqueId, pageId)

        if (pointFiles.isEmpty()) {
            android.util.Log.w("BooxNative", "No point files found for page $pageId in note $uniqueId")
            return emptyList()
        }

        val allStrokes = mutableListOf<Stroke>()

        pointFiles.forEach { file ->
            // Parse point file and split into multiple strokes based on time gaps
            val strokesFromFile = parsePointFileToStrokes(file)
            allStrokes.addAll(strokesFromFile)
        }

        android.util.Log.d("BooxNative", "Loaded ${allStrokes.size} strokes for page $pageId (total ${allStrokes.sumOf { it.points.size }} points)")
        return allStrokes
    }

    private fun jsonArrayToStrings(array: org.json.JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val value = array.optString(i).trim()
                if (value.isNotEmpty()) {
                    add(value)
                }
            }
        }
    }
}

/**
 * BooxNotesIntegration - Content Provider Interface for Boox Notes Metadata
 *
 * This class provides access to Boox note metadata through Android's ContentProvider
 * system and filesystem scanning for complete note discovery.
 *
 * ## Two Data Sources
 *
 * 1. **Content Provider** → Get note metadata (uniqueId, title, pageNameList)
 * 2. **BooxNative** → Use uniqueId to render from .ksync filesystem
 *
 * ## Usage Flow for Calendar App Integration
 *
 * ```kotlin
 * val booxIntegration = BooxNotesIntegration(context)
 * val notes = booxIntegration.getAllNotes()
 *
 * notes.forEach { note ->
 *     val pageId = extractFirstPageId(note.pageNameList)
 *     val bitmap = BooxNative.renderPageOnDevice(
 *         BooxNative.KSYNC_BASE_PATH,
 *         note.uniqueId,
 *         pageId
 *     )
 * }
 * ```
 */
package com.boox.day.booxnotes

import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Data class representing a note's metadata from the Boox content provider.
 */
data class NoteReference(
    val uniqueId: String,
    val title: String,
    val type: Int,
    val createdAt: Long?,
    val updatedAt: Long?,
    val pageCount: Int?,
    val pageNameList: String?,
    val noteSyncStatus: Int?,
    val favorite: Int?,
    val source: String?,
)

/**
 * Data class containing all date information for a note.
 * Addresses the sync date issue where updatedAt gets overwritten.
 */
data class NoteDateInfo(
    val originalDate: LocalDate?,
    val titleDate: LocalDate?,
    val databaseCreatedAt: LocalDateTime?,
    val databaseUpdatedAt: LocalDateTime?,
    val earliestShapeTimestamp: Long?,
    val latestShapeTimestamp: Long?,
    val dateSource: String,  // "title", "shape_file", "database", "unknown"
)

class BooxNotesIntegration(private val context: Context) {

    companion object {
        private const val TAG = "BooxNotesIntegration"
    }

    private val contentResolver: ContentResolver = context.contentResolver
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    private val notesAuthority = "com.onyx.android.sdk.note.ContentProvider.mail"
    private val noteModelUri: Uri = Uri.parse("content://$notesAuthority/NoteModel")

    // ========================================================================
    // Query Functions
    // ========================================================================

    /**
     * Get all notes from the Boox Notes app.
     */
    fun getAllNotes(): List<NoteReference> {
        Log.d(TAG, "Querying Boox content provider")

        val projection = arrayOf(
            "uniqueId", "title", "type", "createdAt", "updatedAt",
            "pageCount", "pageNameList", "noteSyncStatus", "favorite", "source"
        )
        val notes = mutableListOf<NoteReference>()

        val cursor = contentResolver.query(noteModelUri, projection, null, null, null)

        if (cursor == null) {
            Log.e(TAG, "Cursor is null - content provider may not be accessible")
            return notes
        }

        cursor.use { c ->
            val uniqueIdIdx = c.getColumnIndexOrThrow("uniqueId")
            val titleIdx = c.getColumnIndexOrThrow("title")
            val typeIdx = c.getColumnIndexOrThrow("type")
            val createdIdx = c.getColumnIndexOrThrow("createdAt")
            val updatedIdx = c.getColumnIndexOrThrow("updatedAt")
            val pageCountIdx = c.getColumnIndexOrThrow("pageCount")
            val pageNamesIdx = c.getColumnIndexOrThrow("pageNameList")
            val syncIdx = c.getColumnIndexOrThrow("noteSyncStatus")
            val favoriteIdx = c.getColumnIndexOrThrow("favorite")
            val sourceIdx = c.getColumnIndexOrThrow("source")

            while (c.moveToNext()) {
                fun getLongOrNull(index: Int): Long? = if (c.isNull(index)) null else c.getLong(index)
                fun getIntOrNull(index: Int): Int? = if (c.isNull(index)) null else c.getInt(index)
                fun getStringOrNull(index: Int): String? = if (c.isNull(index)) null else c.getString(index)

                notes.add(
                    NoteReference(
                        uniqueId = c.getString(uniqueIdIdx),
                        title = c.getString(titleIdx),
                        type = c.getInt(typeIdx),
                        createdAt = getLongOrNull(createdIdx),
                        updatedAt = getLongOrNull(updatedIdx),
                        pageCount = getIntOrNull(pageCountIdx),
                        pageNameList = getStringOrNull(pageNamesIdx),
                        noteSyncStatus = getIntOrNull(syncIdx),
                        favorite = getIntOrNull(favoriteIdx),
                        source = getStringOrNull(sourceIdx),
                    )
                )
            }
        }

        Log.d(TAG, "Found ${notes.size} notes from content provider")
        return notes
    }

    /**
     * Get notes for a specific date by title matching.
     */
    fun getNotesForDate(date: LocalDate): List<NoteReference> {
        val dateStr = date.format(dateFormatter)
        val projection = arrayOf("uniqueId", "title", "type")
        val notes = mutableListOf<NoteReference>()

        val cursor = contentResolver.query(
            noteModelUri, projection, "title = ?", arrayOf(dateStr), null
        )

        cursor?.use { c ->
            val uniqueIdIdx = c.getColumnIndexOrThrow("uniqueId")
            val titleIdx = c.getColumnIndexOrThrow("title")
            val typeIdx = c.getColumnIndexOrThrow("type")
            while (c.moveToNext()) {
                notes.add(
                    NoteReference(
                        uniqueId = c.getString(uniqueIdIdx),
                        title = c.getString(titleIdx),
                        type = c.getInt(typeIdx),
                        createdAt = null, updatedAt = null, pageCount = null,
                        pageNameList = null, noteSyncStatus = null, favorite = null, source = null
                    )
                )
            }
        }

        return notes
    }

    /**
     * Get all dates that have notes (for calendar badges).
     */
    fun getDatesWithNotes(): Set<LocalDate> {
        val projection = arrayOf("title")
        val dates = mutableSetOf<LocalDate>()

        val cursor = contentResolver.query(noteModelUri, projection, null, null, null)

        cursor?.use { c ->
            val titleIdx = c.getColumnIndexOrThrow("title")
            while (c.moveToNext()) {
                val title = c.getString(titleIdx)
                try {
                    val date = LocalDate.parse(title, dateFormatter)
                    dates.add(date)
                } catch (_: Exception) {
                    // Title is not a date format
                }
            }
        }
        return dates
    }

    // ========================================================================
    // Note Actions
    // ========================================================================

    /**
     * Open a note in the Boox Notes app.
     *
     * Uses the MIDDLEWARE intent which is the canonical Boox way to open notes.
     * Since we only index notes that exist in NOTE_TREE (registered notes),
     * MIDDLEWARE should always work.
     *
     * IMPORTANT: Uses Bundle + putExtras() pattern which is CRITICAL for Boox intents.
     * Do NOT use putExtra() directly - it will fail silently.
     *
     * @param uniqueId The note's uniqueId to open
     * @param title Optional title for logging (not used for opening)
     */
    fun openNote(uniqueId: String, title: String? = null) {
        Log.i(TAG, "Opening note via MIDDLEWARE: $uniqueId (title: $title)")

        // Build the middleware intent with note identifier
        // CRITICAL: Must use Bundle + putExtras(), NOT individual putExtra() calls
        val bundle = Bundle().apply {
            putString("note_document_id", uniqueId)
            putString("type", "note")
        }

        val intent = Intent("com.onyx.action.MIDDLEWARE").apply {
            putExtras(bundle)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
            Log.i(TAG, "✓ MIDDLEWARE intent sent successfully")
        } catch (e: Exception) {
            Log.e(TAG, "✗ Failed to open note: ${e.message}", e)
            Toast.makeText(
                context,
                "Could not open note in Boox Notes.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Launch the Boox Notes application.
     * Attempts to launch via package name "com.onyx.android.note".
     */
    fun launchBooxNotesApp() {
        val packageName = "com.onyx.android.note"
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)

        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            Log.e(TAG, "Boox Notes app not found (package: $packageName)")
        }
    }

    /**
     * Create a new quick note in Boox Notes app.
     * Opens the quick note creation screen ready for writing.
     * Note: Boox Notes does not support pre-filling the title via intent.
     */
    fun createQuickNote() {
        Log.d(TAG, "Creating quick note via QUICK_NOTE action")
        val intent = Intent("com.onyx.intent.action.QUICK_NOTE").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create quick note: ${e.message}", e)
            // Fallback to just launching the app
            launchBooxNotesApp()
        }
    }

    // ========================================================================
    // Original Date Extraction (Sync-Safe)
    // ========================================================================

    /**
     * Get comprehensive date information for a note.
     * Extracts dates from multiple sources to determine the TRUE original date.
     */
    fun getNoteDateInfo(note: NoteReference): NoteDateInfo {
        val (earliestTs, latestTs) = getShapeFileTimestamps(note.uniqueId)
        // Derive reference year from shape timestamp for partial-date titles (e.g. "April 24")
        val referenceYear = earliestTs?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).year
        }
        val titleDate = parseTitleAsDate(note.title, referenceYear)
        val dbCreated = note.createdAt?.let { msToLocalDateTime(it) }
        val dbUpdated = note.updatedAt?.let { msToLocalDateTime(it) }

        val (originalDate, source) = when {
            titleDate != null -> titleDate to "title"
            earliestTs != null -> msToLocalDate(earliestTs) to "shape_file"
            note.createdAt != null -> msToLocalDate(note.createdAt) to "database"
            else -> null to "unknown"
        }

        return NoteDateInfo(
            originalDate = originalDate,
            titleDate = titleDate,
            databaseCreatedAt = dbCreated,
            databaseUpdatedAt = dbUpdated,
            earliestShapeTimestamp = earliestTs,
            latestShapeTimestamp = latestTs,
            dateSource = source
        )
    }

    /**
     * Parse a note title as a date. Supports multiple formats.
     * @param referenceYear Year to use for partial dates (Month DD only).
     *   If null, falls back to current year.
     */
    fun parseTitleAsDate(title: String, referenceYear: Int? = null): LocalDate? {
        val patterns = listOf(
            "yyyyMMdd", "MMM dd yyyy", "MMM d yyyy",
            "MMMM dd, yyyy", "MMMM d, yyyy", "MMMM dd yyyy"
        )

        for (pattern in patterns) {
            try {
                return LocalDate.parse(title, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
            } catch (_: Exception) { }
        }

        // Try partial date formats (Month DD) - use referenceYear from shape file timestamp
        // if available, otherwise fall back to current year
        val year = referenceYear ?: LocalDate.now().year
        val monthDayPatterns = listOf("MMMM dd", "MMM dd")
        for (pattern in monthDayPatterns) {
            try {
                val parsed = java.time.MonthDay.parse(title, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
                return parsed.atYear(year)
            } catch (_: Exception) { }
        }

        return null
    }

    /**
     * Extract timestamps from shape file filenames (sync-safe).
     */
    fun getShapeFileTimestamps(uniqueId: String): Pair<Long?, Long?> {
        val shapeDir = File("${BooxNative.KSYNC_BASE_PATH}/document/$uniqueId/shape")

        if (!shapeDir.exists() || !shapeDir.isDirectory) {
            return null to null
        }

        val timestamps = shapeDir.listFiles()
            ?.filter { it.name.endsWith(".zip") }
            ?.mapNotNull { file ->
                val parts = file.name.removeSuffix(".zip").split("#")
                parts.lastOrNull()?.toLongOrNull()
            }
            ?.filter { ts -> ts in 1420070400000L..2051222400000L }
            ?: emptyList()

        if (timestamps.isEmpty()) return null to null

        return timestamps.minOrNull() to timestamps.maxOrNull()
    }

    private fun msToLocalDateTime(ms: Long): LocalDateTime =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDateTime()

    private fun msToLocalDate(ms: Long): LocalDate =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
}

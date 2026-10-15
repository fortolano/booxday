package com.boox.day.data

import android.content.Context
import android.util.Log
import com.boox.day.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Calculates storage usage for the BooxDay app.
 *
 * Storage is organized into user-friendly categories matching LocalBackupCard:
 * - Memos & Drawings: Pages, strokes, images
 * - Tasks: Task items
 * - Events & Calendars: Local events + calendars
 * - Notebooks & Folders: User organization
 * - OCR & Semantic Data: Indexed content
 * - Daily Summaries: Cached summaries
 */
object StorageCalculator {

    private const val TAG = "StorageCalculator"

    /**
     * Complete storage breakdown for the app.
     */
    data class StorageInfo(
        val totalBytes: Long,
        val databaseBytes: Long,
        val imagesBytes: Long,
        // Content counts (user-friendly, matching LocalBackupCard)
        val eventsCount: Int,
        val calendarsCount: Int,
        val tasksCount: Int,
        val memoPagesCount: Int,
        val strokesCount: Int,
        val notebooksCount: Int,
        val foldersCount: Int,
        val ocrIndexedCount: Int,
        val summariesCount: Int
    ) {
        companion object {
            val EMPTY = StorageInfo(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        }
    }

    /**
     * Calculate total storage used by the app.
     * Runs on IO dispatcher for file system access.
     */
    suspend fun calculateStorage(context: Context): StorageInfo = withContext(Dispatchers.IO) {
        val dbDir = getDbDir(context)

        // Database files (main storage)
        val dbFile = File(dbDir, "app_database")
        val walFile = File(dbDir, "app_database-wal")
        val shmFile = File(dbDir, "app_database-shm")
        val databaseBytes = dbFile.lengthSafe() + walFile.lengthSafe() + shmFile.lengthSafe()

        // Images folder (user-added content)
        val imagesDir = File(dbDir, "images")
        val imagesBytes = calculateDirectorySize(imagesDir)

        // Internal cache (backgrounds + previews) - included in total but not shown
        val backgroundsDir = File(dbDir, "backgrounds")
        val previewsDir = File(context.filesDir, "pages/previews")
        val cacheBytes = calculateDirectorySize(backgroundsDir) + calculateDirectorySize(previewsDir)

        val totalBytes = databaseBytes + imagesBytes + cacheBytes

        // Get content counts from database - each query is independent
        val db = AppDatabase.getDatabase(context)
        val eventsCount = countEvents(db)
        val calendarsCount = countCalendars(db)
        val tasksCount = countTasks(db)
        val memoPagesCount = countMemoPages(db)
        val strokesCount = countStrokes(db)
        val notebooksCount = countNotebooks(db)
        val foldersCount = countFolders(db)
        val ocrIndexedCount = countOcrIndexed(db)
        val summariesCount = countSummaries(db)

        Log.i(TAG, "Storage calculated: total=${formatBytes(totalBytes)}, " +
                "events=$eventsCount, calendars=$calendarsCount, tasks=$tasksCount, " +
                "memos=$memoPagesCount, strokes=$strokesCount, notebooks=$notebooksCount, " +
                "folders=$foldersCount, ocrIndexed=$ocrIndexedCount, summaries=$summariesCount")

        StorageInfo(
            totalBytes = totalBytes,
            databaseBytes = databaseBytes,
            imagesBytes = imagesBytes,
            eventsCount = eventsCount,
            calendarsCount = calendarsCount,
            tasksCount = tasksCount,
            memoPagesCount = memoPagesCount,
            strokesCount = strokesCount,
            notebooksCount = notebooksCount,
            foldersCount = foldersCount,
            ocrIndexedCount = ocrIndexedCount,
            summariesCount = summariesCount
        )
    }

    /**
     * Count events - all local events except pending delete.
     */
    private fun countEvents(db: AppDatabase): Int {
        return try {
            val cursor = db.openHelper.readableDatabase
            cursor.query("SELECT COUNT(*) FROM local_events WHERE syncStatus != 'PENDING_DELETE'").use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to count events: ${e.message}")
            // Fallback: try counting all events
            try {
                val cursor = db.openHelper.readableDatabase
                cursor.query("SELECT COUNT(*) FROM local_events").use { c ->
                    if (c.moveToFirst()) c.getInt(0) else 0
                }
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to count events (fallback): ${e2.message}")
                0
            }
        }
    }

    /**
     * Count tasks - ALL tasks (not just completed/typed).
     * A task exists means the user created a task slot.
     */
    private fun countTasks(db: AppDatabase): Int {
        return try {
            val cursor = db.openHelper.readableDatabase
            cursor.query("SELECT COUNT(*) FROM task").use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to count tasks: ${e.message}")
            0
        }
    }

    /**
     * Count memo pages - count distinct dates in DayPage table.
     * Each date with notes = one "memo day".
     */
    private fun countMemoPages(db: AppDatabase): Int {
        return try {
            val cursor = db.openHelper.readableDatabase
            // Count DayPage entries (each represents a day with memo pages)
            cursor.query("SELECT COUNT(*) FROM daypage").use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to count daypage: ${e.message}")
            // Fallback: count all pages
            try {
                val cursor = db.openHelper.readableDatabase
                cursor.query("SELECT COUNT(*) FROM page").use { c ->
                    if (c.moveToFirst()) c.getInt(0) else 0
                }
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to count pages (fallback): ${e2.message}")
                0
            }
        }
    }

    /**
     * Count strokes - all handwriting strokes.
     */
    private fun countStrokes(db: AppDatabase): Int {
        return try {
            val cursor = db.openHelper.readableDatabase
            cursor.query("SELECT COUNT(*) FROM stroke").use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to count strokes: ${e.message}")
            0
        }
    }

    /**
     * Count local calendars.
     */
    private fun countCalendars(db: AppDatabase): Int {
        return try {
            val cursor = db.openHelper.readableDatabase
            cursor.query("SELECT COUNT(*) FROM local_calendars").use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to count calendars: ${e.message}")
            0
        }
    }

    /**
     * Count notebooks.
     */
    private fun countNotebooks(db: AppDatabase): Int {
        return try {
            val cursor = db.openHelper.readableDatabase
            cursor.query("SELECT COUNT(*) FROM Notebook").use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to count notebooks: ${e.message}")
            0
        }
    }

    /**
     * Count folders.
     */
    private fun countFolders(db: AppDatabase): Int {
        return try {
            val cursor = db.openHelper.readableDatabase
            cursor.query("SELECT COUNT(*) FROM Folder").use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to count folders: ${e.message}")
            0
        }
    }

    /**
     * Count OCR indexed pages (pages with semantic data).
     */
    private fun countOcrIndexed(db: AppDatabase): Int {
        return try {
            val cursor = db.openHelper.readableDatabase
            cursor.query("SELECT COUNT(*) FROM unified_ocr_text").use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to count OCR indexed: ${e.message}")
            0
        }
    }

    /**
     * Count daily summaries.
     */
    private fun countSummaries(db: AppDatabase): Int {
        return try {
            val cursor = db.openHelper.readableDatabase
            cursor.query("SELECT COUNT(*) FROM summary_cache").use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to count summaries: ${e.message}")
            0
        }
    }

    /**
     * Calculate total size of a directory recursively.
     */
    private fun calculateDirectorySize(directory: File): Long {
        if (!directory.exists() || !directory.isDirectory) return 0

        var size = 0L
        directory.walkTopDown().forEach { file ->
            if (file.isFile) {
                size += file.lengthSafe()
            }
        }
        return size
    }

    /**
     * Safe file length that returns 0 if file doesn't exist or can't be read.
     */
    private fun File.lengthSafe(): Long {
        return try {
            if (exists() && isFile) length() else 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Format bytes into human-readable string.
     */
    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}

package com.boox.day.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Service for local backup operations using Storage Access Framework (SAF).
 *
 * Handles:
 * - Generating backup file names with timestamps
 * - Writing backup JSON to user-selected URI
 * - Reading backup JSON from user-selected URI
 * - Preview before restore
 *
 * Backup files are streamed in both directions (never read into one String), so large
 * handwriting histories don't run the app out of memory.
 */
class LocalBackupService(private val context: Context) {

    companion object {
        private const val TAG = "LocalBackupService"
        private const val BACKUP_FILE_PREFIX = "booxday_backup_"
        private const val BACKUP_FILE_EXTENSION = ".json"
    }

    /**
     * Result of save operation.
     */
    sealed class SaveResult {
        data class Success(
            val fileName: String,
            val bytesWritten: Long,
            val metadata: BackupMetadata
        ) : SaveResult()
        data class Error(val message: String) : SaveResult()
    }

    /**
     * Result of preview operation.
     */
    sealed class PreviewResult {
        data class Success(val preview: ImportService.BackupPreview) : PreviewResult()
        data class Error(val message: String) : PreviewResult()
    }

    /**
     * Result of restore operation.
     */
    sealed class RestoreResult {
        data class Success(val result: ImportService.ImportResult.Success) : RestoreResult()
        data class Error(val message: String) : RestoreResult()
    }

    /**
     * Generate a backup file name with timestamp.
     * Format: booxday_backup_2026-01-20_153045.json
     */
    fun generateBackupFileName(): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
        return "$BACKUP_FILE_PREFIX$timestamp$BACKUP_FILE_EXTENSION"
    }

    /**
     * Create a backup and save it to the provided URI.
     *
     * @param uri The SAF URI selected by the user
     * @return SaveResult indicating success or failure
     */
    suspend fun saveBackupToUri(uri: Uri): SaveResult = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Creating backup for SAF URI: $uri")

            // Create the backup file using ExportService, then copy it to the chosen location
            val exportService = ExportService(context)
            when (val exportResult = exportService.createBackup()) {
                is ExportService.ExportResult.Success -> {
                    val bytesWritten = context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        exportResult.backupFile.inputStream().use { it.copyTo(outputStream) }
                    } ?: throw Exception("Failed to open output stream for URI")

                    Log.i(TAG, "Backup saved successfully: $bytesWritten bytes")

                    SaveResult.Success(
                        fileName = uri.lastPathSegment ?: generateBackupFileName(),
                        bytesWritten = bytesWritten,
                        metadata = exportResult.metadata
                    )
                }
                is ExportService.ExportResult.Error -> {
                    Log.e(TAG, "Export failed: ${exportResult.message}")
                    SaveResult.Error(exportResult.message)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save backup to URI: ${e.message}", e)
            SaveResult.Error(e.message ?: "Unknown error while saving backup")
        }
    }

    /**
     * Preview a backup file from the provided URI without restoring.
     *
     * @param uri The SAF URI selected by the user
     * @return PreviewResult with backup contents summary
     */
    suspend fun previewBackupFromUri(uri: Uri): PreviewResult = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Previewing backup from URI: $uri")
            preview { openUri(uri) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to preview backup from URI: ${e.message}", e)
            PreviewResult.Error(e.message ?: "Unknown error while reading backup")
        }
    }

    /**
     * Restore a backup from the provided URI.
     *
     * @param uri The SAF URI selected by the user
     * @param clearExisting If true, clears the tables the backup covers before restore (default: true)
     * @return RestoreResult indicating success or failure
     */
    suspend fun restoreBackupFromUri(
        uri: Uri,
        clearExisting: Boolean = true
    ): RestoreResult = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Restoring backup from URI: $uri")
            restore(clearExisting) { openUri(uri) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore backup from URI: ${e.message}", e)
            RestoreResult.Error(e.message ?: "Unknown error while restoring backup")
        }
    }

    /**
     * Preview a backup from a local file without restoring.
     */
    suspend fun previewBackupFromFile(file: File): PreviewResult = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Previewing backup from file: ${file.absolutePath}")
            preview { file.inputStream() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to preview backup from file: ${e.message}", e)
            PreviewResult.Error(e.message ?: "Unknown error while reading backup")
        }
    }

    /**
     * Restore a backup from a local file.
     */
    suspend fun restoreBackupFromFile(
        file: File,
        clearExisting: Boolean = true
    ): RestoreResult = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Restoring backup from file: ${file.absolutePath}")
            restore(clearExisting) { file.inputStream() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore backup from file: ${e.message}", e)
            RestoreResult.Error(e.message ?: "Unknown error while restoring backup")
        }
    }

    private fun openUri(uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri) ?: throw Exception("Failed to open input stream for URI")

    private suspend fun preview(source: () -> InputStream): PreviewResult =
        ImportService(context).previewBackup(source).fold(
            onSuccess = { preview ->
                Log.i(TAG, "Preview successful: ${preview.eventCount} events, ${preview.taskCount} tasks")
                PreviewResult.Success(preview)
            },
            onFailure = { error ->
                Log.e(TAG, "Preview failed: ${error.message}")
                PreviewResult.Error(error.message ?: "Failed to parse backup file")
            }
        )

    private suspend fun restore(clearExisting: Boolean, source: () -> InputStream): RestoreResult =
        when (val importResult = ImportService(context).restoreBackup(source, clearExisting)) {
            is ImportService.ImportResult.Success -> {
                Log.i(TAG, "Restore successful: ${importResult.eventsRestored} events, ${importResult.tasksRestored} tasks")
                RestoreResult.Success(importResult)
            }
            is ImportService.ImportResult.Error -> {
                Log.e(TAG, "Restore failed: ${importResult.message}")
                RestoreResult.Error(importResult.message)
            }
        }

    /**
     * Get the file size of a backup at the given URI.
     *
     * @param uri The SAF URI to check
     * @return File size in bytes, or -1 if unknown
     */
    fun getBackupFileSize(uri: Uri): Long {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.available().toLong()
            } ?: -1L
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get file size for URI: ${e.message}")
            -1L
        }
    }
}

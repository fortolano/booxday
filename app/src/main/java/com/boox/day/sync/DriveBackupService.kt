package com.boox.day.sync

import android.content.Context
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import java.io.ByteArrayOutputStream

/**
 * Google Drive Backup Service for BooxDay.
 *
 * Handles cloud backup operations:
 * - Creating/finding the BooxDay backup folder
 * - Uploading backup files
 * - Downloading backup files for restore
 * - Listing available backups
 *
 * Uses DRIVE_FILE scope (app-specific files only).
 */
class DriveBackupService(private val context: Context) {

    private val authManager = GoogleAuthManager(context)

    /**
     * Get or create the Drive service instance.
     * Returns null if not signed in or credentials unavailable.
     */
    private fun getDriveService(): Drive? {
        val credential = authManager.getDriveCredential() ?: return null

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("BooxDay")
            .build()
    }

    /**
     * Result of folder operations.
     */
    sealed class FolderResult {
        data class Success(val folderId: String, val wasCreated: Boolean) : FolderResult()
        data class Error(val message: String) : FolderResult()
    }

    /**
     * Ensure the backup folder exists in Google Drive.
     * Creates it if it doesn't exist, or returns existing folder ID.
     *
     * Should be called immediately after sign-in to validate Drive access.
     */
    suspend fun ensureBackupFolder(): FolderResult = withContext(Dispatchers.IO) {
        try {
            val drive = getDriveService()
            if (drive == null) {
                Log.e(TAG, "Drive service unavailable - not signed in")
                return@withContext FolderResult.Error("Not signed in to Google")
            }
            findOrCreateBackupFolder(drive, authManager.getBackupFolderId(), authManager::setBackupFolderId)
        } catch (e: Exception) {
            Log.e(TAG, "Error ensuring backup folder: ${e.message}", e)
            FolderResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Check if Drive backup is available (signed in with Drive scope).
     */
    fun isAvailable(): Boolean {
        return authManager.isSignedIn() && authManager.hasDriveScope()
    }

    // ========== Upload Operations ==========

    /**
     * Result of upload operation.
     */
    sealed class UploadResult {
        data class Success(val fileId: String, val fileName: String) : UploadResult()
        data class Error(val message: String) : UploadResult()
    }

    /**
     * Upload a backup file to Google Drive.
     *
     * @param localFile The local backup file to upload
     * @return UploadResult indicating success or failure
     */
    suspend fun uploadBackup(localFile: java.io.File): UploadResult = withContext(Dispatchers.IO) {
        try {
            val drive = getDriveService()
            if (drive == null) {
                return@withContext UploadResult.Error("Not signed in to Google")
            }

            // Found or made here (Oct 2026): only some setup steps in the backup dialog made the
            // folder, so Backup Now failed with "Backup folder not configured" after the others
            val folderId = when (val folder = findOrCreateBackupFolder(drive, authManager.getBackupFolderId(), authManager::setBackupFolderId)) {
                is FolderResult.Success -> folder.folderId
                is FolderResult.Error -> return@withContext UploadResult.Error(folder.message)
            }

            Log.i(TAG, "Uploading backup: ${localFile.name} to folder: $folderId")

            // Create file metadata
            val fileMetadata = File().apply {
                name = localFile.name
                parents = listOf(folderId)
                mimeType = "application/json"
                description = "BooxDay backup - ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())}"
            }

            // Upload the file
            val mediaContent = FileContent("application/json", localFile)
            val uploadedFile = drive.files().create(fileMetadata, mediaContent)
                .setFields("id, name, size, createdTime")
                .execute()

            if (uploadedFile?.id != null) {
                Log.i(TAG, "Backup uploaded successfully: ${uploadedFile.id} (${uploadedFile.getSize()} bytes)")
                authManager.setLastBackupTime(System.currentTimeMillis())
                UploadResult.Success(uploadedFile.id, uploadedFile.name)
            } else {
                UploadResult.Error("Upload returned no file ID")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed: ${e.message}", e)
            UploadResult.Error(e.message ?: "Unknown upload error")
        }
    }

    // ========== List Operations ==========

    /**
     * Info about a backup file in Google Drive.
     */
    data class BackupInfo(
        val fileId: String,
        val fileName: String,
        val createdTime: Long,
        val sizeBytes: Long
    )

    /**
     * Result of list operation.
     */
    sealed class ListResult {
        data class Success(val backups: List<BackupInfo>) : ListResult()
        data class Error(val message: String) : ListResult()
    }

    /**
     * List all backup files in the Google Drive backup folder.
     *
     * @return ListResult with available backups sorted by date (newest first)
     */
    suspend fun listBackups(): ListResult = withContext(Dispatchers.IO) {
        try {
            val drive = getDriveService()
            if (drive == null) {
                return@withContext ListResult.Error("Not signed in to Google")
            }

            // No folder yet means no backups yet, not an error (Oct 2026)
            val folderId = findBackupFolder(drive, authManager.getBackupFolderId(), authManager::setBackupFolderId)
                ?: return@withContext ListResult.Success(emptyList())

            Log.i(TAG, "Listing backups in folder: $folderId")

            // Query for JSON files in the backup folder
            val query = "'$folderId' in parents and mimeType='application/json' and trashed=false"
            val result = drive.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name, createdTime, size)")
                .setOrderBy("createdTime desc")
                .setPageSize(50)
                .execute()

            val backups = result.files?.mapNotNull { file ->
                try {
                    BackupInfo(
                        fileId = file.id,
                        fileName = file.name,
                        createdTime = file.createdTime?.value ?: 0L,
                        sizeBytes = file.getSize() ?: 0L
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping invalid backup entry: ${file.name}")
                    null
                }
            } ?: emptyList()

            Log.i(TAG, "Found ${backups.size} backup(s)")
            ListResult.Success(backups)
        } catch (e: Exception) {
            Log.e(TAG, "List backups failed: ${e.message}", e)
            ListResult.Error(e.message ?: "Unknown error listing backups")
        }
    }

    // ========== Download Operations ==========

    /**
     * Result of download operation.
     */
    sealed class DownloadResult {
        data class Success(val content: String) : DownloadResult()
        data class Error(val message: String) : DownloadResult()
    }

    /**
     * Download a backup file from Google Drive.
     *
     * @param fileId The Google Drive file ID of the backup to download
     * @return DownloadResult with the JSON content or error
     */
    suspend fun downloadBackup(fileId: String): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val drive = getDriveService()
            if (drive == null) {
                return@withContext DownloadResult.Error("Not signed in to Google")
            }

            Log.i(TAG, "Downloading backup: $fileId")

            val outputStream = ByteArrayOutputStream()
            drive.files().get(fileId)
                .executeMediaAndDownloadTo(outputStream)

            val content = outputStream.toString(Charsets.UTF_8.name())
            Log.i(TAG, "Downloaded backup: ${content.length} characters")

            DownloadResult.Success(content)
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}", e)
            DownloadResult.Error(e.message ?: "Unknown download error")
        }
    }

    /**
     * Delete a backup file from Google Drive.
     *
     * @param fileId The Google Drive file ID to delete
     * @return true if deletion succeeded
     */
    suspend fun deleteBackup(fileId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val drive = getDriveService()
            if (drive == null) {
                Log.e(TAG, "Delete failed: Not signed in")
                return@withContext false
            }

            drive.files().delete(fileId).execute()
            Log.i(TAG, "Deleted backup: $fileId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Delete failed: ${e.message}", e)
            false
        }
    }

    // ========== Smart Restore Detection (Phase 0) ==========

    /**
     * Summary of existing backups for UI display.
     * Used when detecting state for new device restore flow.
     */
    data class ExistingBackupSummary(
        val newestBackupDate: Long,
        val totalBackupCount: Int,
        val newestBackupType: BackupType,
        val memoPageCount: Int?,
        val taskCount: Int?,
        val ocrPageCount: Int?
    )

    /**
     * Check if any backup files exist without downloading them.
     * Used for quick state detection on dialog open.
     * Searches directly by folder name to work even without stored folder ID.
     */
    suspend fun hasExistingBackups(): Boolean = withContext(Dispatchers.IO) {
        try {
            val drive = getDriveService()
            if (drive == null) {
                Log.d(TAG, "hasExistingBackups: Not signed in")
                return@withContext false
            }

            // First, try stored folder ID for fast check
            var folderId = authManager.getBackupFolderId()

            // If no stored folder ID, search for folder by name
            if (folderId == null) {
                folderId = findExistingFolder(drive)
                if (folderId != null) {
                    authManager.setBackupFolderId(folderId)
                }
            }

            if (folderId == null) {
                Log.d(TAG, "hasExistingBackups: No backup folder found")
                return@withContext false
            }

            // Quick check: query for any file in the folder (pageSize=1 for speed)
            val query = "'$folderId' in parents and trashed=false"
            val result = drive.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id)")
                .setPageSize(1)
                .execute()

            val hasBackups = !result.files.isNullOrEmpty()
            Log.i(TAG, "hasExistingBackups: $hasBackups")
            hasBackups
        } catch (e: Exception) {
            Log.e(TAG, "hasExistingBackups check failed: ${e.message}", e)
            false
        }
    }

    /**
     * Get summary of existing backups for UI display.
     * Downloads only metadata files, not full backups.
     * Returns null if no backups exist or not signed in.
     */
    suspend fun getExistingBackupSummary(): ExistingBackupSummary? = withContext(Dispatchers.IO) {
        try {
            val drive = getDriveService()
            if (drive == null) {
                return@withContext null
            }

            // First, try stored folder ID
            var folderId = authManager.getBackupFolderId()

            // If no stored folder ID, search for folder by name
            if (folderId == null) {
                folderId = findExistingFolder(drive)
                if (folderId != null) {
                    authManager.setBackupFolderId(folderId)
                }
            }

            if (folderId == null) {
                return@withContext null
            }

            // Check for full database backups first (metadata files)
            val fullBackupQuery = "'$folderId' in parents and name contains '_meta.json' and trashed=false"
            val fullBackupResult = drive.files().list()
                .setQ(fullBackupQuery)
                .setSpaces("drive")
                .setFields("files(id, name, createdTime, size)")
                .setOrderBy("createdTime desc")
                .setPageSize(1)
                .execute()

            val newestFullBackup = fullBackupResult.files?.firstOrNull()

            // Check for JSON backups
            val jsonBackupQuery = "'$folderId' in parents and mimeType='application/json' and not name contains '_meta.json' and trashed=false"
            val jsonBackupResult = drive.files().list()
                .setQ(jsonBackupQuery)
                .setSpaces("drive")
                .setFields("files(id, name, createdTime, size)")
                .setOrderBy("createdTime desc")
                .setPageSize(50)
                .execute()

            val newestJsonBackup = jsonBackupResult.files?.firstOrNull()
            val jsonBackupCount = jsonBackupResult.files?.size ?: 0

            // Count full backups
            val fullBackupCountQuery = "'$folderId' in parents and name contains '_meta.json' and trashed=false"
            val fullBackupCountResult = drive.files().list()
                .setQ(fullBackupCountQuery)
                .setSpaces("drive")
                .setFields("files(id)")
                .setPageSize(50)
                .execute()
            val fullBackupCount = fullBackupCountResult.files?.size ?: 0

            // No backups at all
            if (newestFullBackup == null && newestJsonBackup == null) {
                return@withContext null
            }

            // Determine newest backup
            val fullBackupTime = newestFullBackup?.createdTime?.value ?: 0L
            val jsonBackupTime = newestJsonBackup?.createdTime?.value ?: 0L

            val isFullBackupNewer = fullBackupTime >= jsonBackupTime && newestFullBackup != null
            val newestBackupDate = if (isFullBackupNewer) fullBackupTime else jsonBackupTime
            val newestBackupType = if (isFullBackupNewer) BackupType.FULL_DATABASE else BackupType.JSON

            // Try to parse metadata from full backup if it's the newest
            var memoPageCount: Int? = null
            var taskCount: Int? = null
            var ocrPageCount: Int? = null

            if (isFullBackupNewer && newestFullBackup != null) {
                try {
                    val outputStream = ByteArrayOutputStream()
                    drive.files().get(newestFullBackup.id)
                        .executeMediaAndDownloadTo(outputStream)
                    val metadataJson = outputStream.toString(Charsets.UTF_8.name())
                    val metadata = kotlinx.serialization.json.Json {
                        ignoreUnknownKeys = true
                    }.decodeFromString<FullDatabaseBackupMetadata>(metadataJson)

                    memoPageCount = metadata.tableCounts["Page"]
                    taskCount = metadata.tableCounts["Task"]
                    ocrPageCount = metadata.ocrPagesIndexed
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse full backup metadata: ${e.message}")
                }
            }

            ExistingBackupSummary(
                newestBackupDate = newestBackupDate,
                totalBackupCount = jsonBackupCount + fullBackupCount,
                newestBackupType = newestBackupType,
                memoPageCount = memoPageCount,
                taskCount = taskCount,
                ocrPageCount = ocrPageCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "getExistingBackupSummary failed: ${e.message}", e)
            null
        }
    }

    /**
     * Delete ALL backup files (JSON and Full Database).
     * Called before enabling new backup when user confirms overwrite.
     *
     * @return Result with count of files deleted, or error
     */
    suspend fun deleteAllBackups(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val drive = getDriveService()
            if (drive == null) {
                return@withContext Result.failure(Exception("Not signed in to Google"))
            }

            val folderId = authManager.getBackupFolderId()
            if (folderId == null) {
                // No folder = no backups to delete
                return@withContext Result.success(0)
            }

            Log.i(TAG, "Deleting all backups in folder: $folderId")

            // List all files in the backup folder
            val query = "'$folderId' in parents and trashed=false"
            val result = drive.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .setPageSize(100)
                .execute()

            val files = result.files ?: emptyList()
            var deletedCount = 0

            for (file in files) {
                try {
                    drive.files().delete(file.id).execute()
                    deletedCount++
                    Log.d(TAG, "Deleted backup file: ${file.name}")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to delete ${file.name}: ${e.message}")
                }
            }

            Log.i(TAG, "Deleted $deletedCount of ${files.size} backup files")
            Result.success(deletedCount)
        } catch (e: Exception) {
            Log.e(TAG, "deleteAllBackups failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ========== Full Database Backup Operations (Phase 2) ==========

    /**
     * Info about a full database backup in Google Drive.
     */
    data class FullBackupInfo(
        val databaseFileId: String,
        val metadataFileId: String,
        val fileName: String,
        val createdTime: Long,
        val databaseSizeBytes: Long,
        val metadata: FullDatabaseBackupMetadata?
    )

    /**
     * Result of full backup upload operation.
     */
    sealed class FullUploadResult {
        data class Success(
            val databaseFileId: String,
            val metadataFileId: String,
            val fileName: String
        ) : FullUploadResult()
        data class Error(val message: String) : FullUploadResult()
    }

    /**
     * Upload both database and metadata files for a full backup.
     *
     * @param databaseFile The SQLite database file
     * @param metadataFile The JSON metadata file
     * @return FullUploadResult indicating success or failure
     */
    suspend fun uploadFullBackup(
        databaseFile: java.io.File,
        metadataFile: java.io.File
    ): FullUploadResult = withContext(Dispatchers.IO) {
        try {
            val drive = getDriveService()
            if (drive == null) {
                return@withContext FullUploadResult.Error("Not signed in to Google")
            }

            val folderId = when (val folder = findOrCreateBackupFolder(drive, authManager.getBackupFolderId(), authManager::setBackupFolderId)) {
                is FolderResult.Success -> folder.folderId
                is FolderResult.Error -> return@withContext FullUploadResult.Error(folder.message)
            }

            Log.i(TAG, "Uploading full backup: ${databaseFile.name} + ${metadataFile.name}")

            // Upload database file
            val dbMetadata = File().apply {
                name = databaseFile.name
                parents = listOf(folderId)
                mimeType = MIME_TYPE_SQLITE
                description = "BooxDay full database backup"
            }
            val dbMediaContent = FileContent(MIME_TYPE_SQLITE, databaseFile)
            val uploadedDb = drive.files().create(dbMetadata, dbMediaContent)
                .setFields("id, name, size, createdTime")
                .execute()

            if (uploadedDb?.id == null) {
                return@withContext FullUploadResult.Error("Database upload returned no file ID")
            }

            // Upload metadata file
            val metaMetadata = File().apply {
                name = metadataFile.name
                parents = listOf(folderId)
                mimeType = "application/json"
                description = "BooxDay full backup metadata"
            }
            val metaMediaContent = FileContent("application/json", metadataFile)
            val uploadedMeta = drive.files().create(metaMetadata, metaMediaContent)
                .setFields("id, name, size, createdTime")
                .execute()

            if (uploadedMeta?.id == null) {
                // Cleanup: delete the database file if metadata upload failed
                try { drive.files().delete(uploadedDb.id).execute() } catch (_: Exception) {}
                return@withContext FullUploadResult.Error("Metadata upload returned no file ID")
            }

            Log.i(TAG, "Full backup uploaded: db=${uploadedDb.id} (${uploadedDb.getSize()} bytes), meta=${uploadedMeta.id}")
            authManager.setLastBackupTime(System.currentTimeMillis())

            FullUploadResult.Success(
                databaseFileId = uploadedDb.id,
                metadataFileId = uploadedMeta.id,
                fileName = databaseFile.name.removeSuffix("_db.sqlite3")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Full backup upload failed: ${e.message}", e)
            FullUploadResult.Error(e.message ?: "Unknown upload error")
        }
    }

    /**
     * List all full database backups.
     * Finds _meta.json files and matches them with corresponding _db.sqlite3 files.
     *
     * @return Result with list of full backups sorted by date (newest first)
     */
    suspend fun listFullBackups(): Result<List<FullBackupInfo>> = withContext(Dispatchers.IO) {
        try {
            val drive = getDriveService()
            if (drive == null) {
                return@withContext Result.failure(Exception("Not signed in to Google"))
            }

            val folderId = authManager.getBackupFolderId()
            if (folderId == null) {
                return@withContext Result.success(emptyList())
            }

            Log.i(TAG, "Listing full backups in folder: $folderId")

            // Query for metadata files
            val metaQuery = "'$folderId' in parents and name contains '_meta.json' and trashed=false"
            val metaResult = drive.files().list()
                .setQ(metaQuery)
                .setSpaces("drive")
                .setFields("files(id, name, createdTime, size)")
                .setOrderBy("createdTime desc")
                .setPageSize(50)
                .execute()

            // Query for database files
            val dbQuery = "'$folderId' in parents and name contains '_db.sqlite3' and trashed=false"
            val dbResult = drive.files().list()
                .setQ(dbQuery)
                .setSpaces("drive")
                .setFields("files(id, name, createdTime, size)")
                .setPageSize(50)
                .execute()

            // Map database files by their base name
            val dbFiles = dbResult.files?.associateBy { file ->
                file.name.removeSuffix("_db.sqlite3")
            } ?: emptyMap()

            // Match metadata files with database files
            val fullBackups = metaResult.files?.mapNotNull { metaFile ->
                try {
                    val baseName = metaFile.name.removeSuffix("_meta.json")
                    val dbFile = dbFiles[baseName]

                    if (dbFile == null) {
                        Log.w(TAG, "No matching database file for ${metaFile.name}")
                        return@mapNotNull null
                    }

                    // Try to download and parse metadata
                    val metadata = try {
                        val outputStream = ByteArrayOutputStream()
                        drive.files().get(metaFile.id)
                            .executeMediaAndDownloadTo(outputStream)
                        val metadataJson = outputStream.toString(Charsets.UTF_8.name())
                        kotlinx.serialization.json.Json {
                            ignoreUnknownKeys = true
                        }.decodeFromString<FullDatabaseBackupMetadata>(metadataJson)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse metadata for ${metaFile.name}: ${e.message}")
                        null
                    }

                    FullBackupInfo(
                        databaseFileId = dbFile.id,
                        metadataFileId = metaFile.id,
                        fileName = baseName,
                        createdTime = metaFile.createdTime?.value ?: 0L,
                        databaseSizeBytes = dbFile.getSize() ?: 0L,
                        metadata = metadata
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping invalid full backup: ${metaFile.name}")
                    null
                }
            } ?: emptyList()

            Log.i(TAG, "Found ${fullBackups.size} full backup(s)")
            Result.success(fullBackups)
        } catch (e: Exception) {
            Log.e(TAG, "List full backups failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Download both database and metadata files for a full backup.
     *
     * @param backup The full backup info
     * @param downloadDir Directory to save downloaded files
     * @return Result with Pair of (databaseFile, metadataFile)
     */
    suspend fun downloadFullBackup(
        backup: FullBackupInfo,
        downloadDir: java.io.File
    ): Result<Pair<java.io.File, java.io.File>> = withContext(Dispatchers.IO) {
        try {
            val drive = getDriveService()
            if (drive == null) {
                return@withContext Result.failure(Exception("Not signed in to Google"))
            }

            Log.i(TAG, "Downloading full backup: ${backup.fileName}")

            // Download database file
            val dbFile = java.io.File(downloadDir, "${backup.fileName}_db.sqlite3")
            val dbOutputStream = java.io.FileOutputStream(dbFile)
            drive.files().get(backup.databaseFileId)
                .executeMediaAndDownloadTo(dbOutputStream)
            dbOutputStream.close()
            Log.i(TAG, "Downloaded database: ${dbFile.length()} bytes")

            // Download metadata file
            val metaFile = java.io.File(downloadDir, "${backup.fileName}_meta.json")
            val metaOutputStream = java.io.FileOutputStream(metaFile)
            drive.files().get(backup.metadataFileId)
                .executeMediaAndDownloadTo(metaOutputStream)
            metaOutputStream.close()
            Log.i(TAG, "Downloaded metadata: ${metaFile.length()} bytes")

            Result.success(Pair(dbFile, metaFile))
        } catch (e: Exception) {
            Log.e(TAG, "Full backup download failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "DriveBackupService"
        private const val BACKUP_FOLDER_NAME = "BooxDay Backups"
        private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
        private const val MIME_TYPE_SQLITE = "application/x-sqlite3"

        /**
         * The backup folder, made if there is none: the saved one if Drive still has it, else one
         * found by name. Saves what it uses through [saveFolderId].
         */
        internal fun findOrCreateBackupFolder(
            drive: Drive,
            storedFolderId: String?,
            saveFolderId: (String?) -> Unit
        ): FolderResult {
            findBackupFolder(drive, storedFolderId, saveFolderId)?.let {
                return FolderResult.Success(it, wasCreated = false)
            }
            val newFolderId = createBackupFolder(drive)
                ?: return FolderResult.Error("Failed to create backup folder")
            Log.i(TAG, "Created new backup folder: $newFolderId")
            saveFolderId(newFolderId)
            return FolderResult.Success(newFolderId, wasCreated = true)
        }

        /**
         * The saved backup folder if Drive still has it, else one found by name, else null.
         * A saved ID Drive doesn't know is dropped: drive.file only sees files made by the same
         * Google Cloud project, so an ID saved before BooxDay's move to its own project (27 Oct
         * 2026) answers 404.
         */
        internal fun findBackupFolder(
            drive: Drive,
            storedFolderId: String?,
            saveFolderId: (String?) -> Unit
        ): String? {
            if (storedFolderId != null) {
                if (verifyFolderExists(drive, storedFolderId)) {
                    Log.i(TAG, "Using existing backup folder: $storedFolderId")
                    return storedFolderId
                }
                Log.w(TAG, "Stored folder ID no longer valid, looking for the folder again")
                saveFolderId(null)
            }

            val existingFolderId = findExistingFolder(drive) ?: return null
            Log.i(TAG, "Found existing backup folder: $existingFolderId")
            saveFolderId(existingFolderId)
            return existingFolderId
        }

        /**
         * True if the folder is there and not in the trash, false if Drive answers 404. Other
         * errors (no network, sign-in) are thrown, so a good saved ID isn't dropped over them.
         */
        private fun verifyFolderExists(drive: Drive, folderId: String): Boolean {
            return try {
                val file = drive.files().get(folderId)
                    .setFields("id, name, trashed")
                    .execute()
                file != null && file.trashed != true
            } catch (e: GoogleJsonResponseException) {
                if (e.statusCode != 404) throw e
                Log.w(TAG, "Folder verification failed: ${e.message}")
                false
            }
        }

        /**
         * Search for an existing backup folder by name.
         */
        private fun findExistingFolder(drive: Drive): String? {
            return try {
                val query = "mimeType='$FOLDER_MIME_TYPE' and name='$BACKUP_FOLDER_NAME' and trashed=false"
                val result = drive.files().list()
                    .setQ(query)
                    .setSpaces("drive")
                    .setFields("files(id, name)")
                    .setPageSize(1)
                    .execute()

                result.files?.firstOrNull()?.id
            } catch (e: Exception) {
                Log.w(TAG, "Error searching for folder: ${e.message}")
                null
            }
        }

        /**
         * Create a new backup folder in Google Drive.
         */
        private fun createBackupFolder(drive: Drive): String? {
            return try {
                val folderMetadata = File().apply {
                    name = BACKUP_FOLDER_NAME
                    mimeType = FOLDER_MIME_TYPE
                    description = "Backup folder for BooxDay app data"
                }

                val folder = drive.files().create(folderMetadata)
                    .setFields("id")
                    .execute()

                folder?.id
            } catch (e: Exception) {
                Log.e(TAG, "Error creating folder: ${e.message}", e)
                null
            }
        }
    }
}

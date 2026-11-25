package com.boox.day.sync

import java.io.File

/** A cloud that keeps BooxDay's backups (owner decisions 85-88, Nov 2026). */
enum class BackupCloud(val key: String, val label: String) {
    GOOGLE_DRIVE("google", "Google Drive"),
    ONEDRIVE("onedrive", "OneDrive");

    companion object {
        fun ofKey(key: String?): BackupCloud? = entries.firstOrNull { it.key == key }
    }
}

/**
 * What the backup screen and automatic backup do with either cloud. The results are the ones
 * Google Drive backup already had, so both clouds are handled the same way.
 */
interface CloudBackupService {
    /** Uploads [localFile] as a new backup; every earlier backup is kept (owner decision 88). */
    suspend fun uploadBackup(localFile: File): DriveBackupService.UploadResult

    /** The backups there, newest first. */
    suspend fun listBackups(): DriveBackupService.ListResult

    /** The backup [fileId] in a temporary file the caller deletes. */
    suspend fun downloadBackupToFile(fileId: String): DriveBackupService.FileDownloadResult

    /** True when at least one backup is there; false when none or it couldn't be checked. */
    suspend fun hasExistingBackups(): Boolean
}

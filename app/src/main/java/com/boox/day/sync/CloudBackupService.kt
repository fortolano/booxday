package com.boox.day.sync

import android.content.Context
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
    /**
     * Uploads [localFile] as a new backup; every earlier backup is kept (owner decision 88). Asks
     * [stillWanted] right before the upload starts (and between OneDrive's pieces): false stops it
     * with [DriveBackupService.UploadResult.Stopped], nothing finished in the cloud. A switch to the
     * other cloud or account while the backup was being made must never land in the old one.
     */
    suspend fun uploadBackup(localFile: File, stillWanted: () -> Boolean = { true }): DriveBackupService.UploadResult

    /** The backups there, newest first; an error when they couldn't be listed, never an empty list. */
    suspend fun listBackups(): DriveBackupService.ListResult

    /** The backup [fileId] in a temporary file the caller deletes. */
    suspend fun downloadBackupToFile(fileId: String): DriveBackupService.FileDownloadResult
}

/**
 * Whether signing in to a cloud from the backup screen turns Auto Backup on: only when no cloud is
 * chosen yet and its backups were listed and there are none (backups there are restored first).
 * Never when they couldn't be listed ([lookup] an error, review 25 Nov 2026: that counted as "no
 * backups"), and never on the way to restoring ([forRestore]; [lookup] is null there). The same
 * rule for Google Drive and OneDrive (owner, 25 Nov 2026).
 */
internal fun turnsOnAutoBackupAfterSignIn(
    forRestore: Boolean,
    destination: BackupCloud?,
    lookup: DriveBackupService.ListResult?
): Boolean = !forRestore && destination == null &&
    lookup is DriveBackupService.ListResult.Success && lookup.backups.isEmpty()

/** The backup service for [cloud]. */
internal fun cloudBackupService(context: Context, cloud: BackupCloud): CloudBackupService = when (cloud) {
    BackupCloud.GOOGLE_DRIVE -> DriveBackupService(context)
    BackupCloud.ONEDRIVE -> OneDriveBackupService.create(context)
}

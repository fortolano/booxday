package com.boox.day.ui.dialogs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.boox.day.sync.DriveBackupService
import com.boox.day.sync.ImportService
import com.boox.day.sync.LocalBackupService
import java.io.File

/**
 * A backup waiting for "Restore from Backup?" (owner decision 87, Nov 2026). A device file always
 * waited there; a cloud backup's Restore replaced all data in one tap. Now a cloud backup is
 * downloaded and previewed first, and nothing is replaced until [confirm]. A downloaded copy is
 * deleted once it is restored or turned down; a device file is never deleted.
 */
internal class PendingRestore {
    class Waiting(val preview: ImportService.BackupPreview, val file: File, val isDownload: Boolean)

    var waiting by mutableStateOf<Waiting?>(null)
        private set

    /** A device file the user picked, already previewed. */
    fun offerFile(preview: ImportService.BackupPreview, file: File) {
        waiting = Waiting(preview, file, isDownload = false)
    }

    /** Downloads a cloud backup and previews it. Null when it waits for the user, else what went wrong. */
    suspend fun offerDownload(
        download: suspend () -> DriveBackupService.FileDownloadResult,
        preview: suspend (File) -> LocalBackupService.PreviewResult
    ): String? = when (val got = download()) {
        is DriveBackupService.FileDownloadResult.Error -> "Download failed: ${got.message}"
        is DriveBackupService.FileDownloadResult.Success -> when (val seen = preview(got.file)) {
            is LocalBackupService.PreviewResult.Success -> {
                waiting = Waiting(seen.preview, got.file, isDownload = true)
                null
            }
            is LocalBackupService.PreviewResult.Error -> {
                got.file.delete()
                "Cannot open backup: ${seen.message}"
            }
        }
    }

    /** The user said Restore: replaces the data with the waiting backup. Null when none waits. */
    suspend fun confirm(restore: suspend (File) -> LocalBackupService.RestoreResult): LocalBackupService.RestoreResult? {
        val backup = waiting ?: return null
        waiting = null
        return try {
            restore(backup.file)
        } finally {
            if (backup.isDownload) backup.file.delete()
        }
    }

    /** The user said Cancel, or the screen closed. */
    fun cancel() {
        waiting?.let { if (it.isDownload) it.file.delete() }
        waiting = null
    }
}

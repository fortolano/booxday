package com.boox.day.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import android.util.Log
import java.util.concurrent.TimeUnit

/**
 * Automatic Google Drive backup (plan Phase 2, Oct 2026).
 *
 * The backup dialog offered an automatic frequency (daily/weekly) but nothing ever ran it.
 * This periodic check runs a few times a day while the device is online; it backs up only when
 * [GoogleAuthManager.shouldAutoBackup] says so (Drive backup on, signed in with the Drive scope,
 * the chosen interval elapsed) — the same steps as "Back up now": streaming v3 export, then
 * upload (which records the backup time).
 */
class DriveAutoBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val authManager = GoogleAuthManager(applicationContext)
        if (!authManager.shouldAutoBackup()) {
            return Result.success()
        }

        Log.i(TAG, "Automatic Drive backup due - exporting")
        val export = ExportService(applicationContext).createBackup()
        if (export !is ExportService.ExportResult.Success) {
            Log.w(TAG, "Automatic backup: export failed (${(export as? ExportService.ExportResult.Error)?.message})")
            return Result.retry()
        }

        return try {
            when (val upload = DriveBackupService(applicationContext).uploadBackup(export.backupFile)) {
                is DriveBackupService.UploadResult.Success -> {
                    Log.i(TAG, "Automatic backup uploaded: ${upload.fileName}")
                    Result.success()
                }
                is DriveBackupService.UploadResult.Error -> {
                    Log.w(TAG, "Automatic backup: upload failed (${upload.message})")
                    Result.retry()
                }
            }
        } finally {
            // The copy lives on Drive; don't pile up a local file per automatic run
            export.backupFile.delete()
        }
    }

    companion object {
        private const val TAG = "DriveAutoBackupWorker"
        private const val WORK_NAME = "drive_auto_backup"
        private const val CHECK_INTERVAL_HOURS = 6L

        /** Keeps the periodic check enqueued (safe to call on every app start). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DriveAutoBackupWorker>(CHECK_INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}

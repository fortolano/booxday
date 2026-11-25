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
 * Automatic backup (plan Phase 2, Oct 2026), to Google Drive or OneDrive since Nov 2026.
 *
 * The backup dialog offered an automatic frequency (daily/weekly) but nothing ever ran it.
 * This periodic check runs a few times a day while the device is online; it backs up only when
 * [GoogleAuthManager.dueAutoBackupCloud] names a cloud (the chosen destination, connected, the
 * interval elapsed since its own last backup) — the same steps as "Back up now": streaming v3
 * export, then upload (which records the backup time). Only the chosen cloud is backed up (owner
 * decision 85). The class and work name stay from the Google-only version, so an upgrade keeps
 * the one queued job instead of adding a second.
 */
class DriveAutoBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val cloud = GoogleAuthManager(applicationContext).dueAutoBackupCloud() ?: return Result.success()

        Log.i(TAG, "Automatic ${cloud.key} backup due - exporting")
        val export = ExportService(applicationContext).createBackup()
        if (export !is ExportService.ExportResult.Success) {
            Log.w(TAG, "Automatic backup: export failed (${(export as? ExportService.ExportResult.Error)?.message})")
            return Result.retry()
        }

        return try {
            when (val upload = cloudBackupService(applicationContext, cloud).uploadBackup(export.backupFile)) {
                is DriveBackupService.UploadResult.Success -> {
                    Log.i(TAG, "Automatic backup uploaded: ${upload.fileName}")
                    Result.success()
                }
                is DriveBackupService.UploadResult.Error -> {
                    Log.w(TAG, "Automatic backup: upload failed (retryable=${upload.retryable}, ${upload.message})")
                    if (upload.retryable) Result.retry() else Result.failure()
                }
            }
        } finally {
            // The copy lives in the cloud; don't pile up a local file per automatic run
            export.backupFile.delete()
        }
    }

    companion object {
        private const val TAG = "DriveAutoBackupWorker"
        private const val WORK_NAME = "drive_auto_backup"
        private const val CHECK_INTERVAL_HOURS = 6L

        /** Keeps the periodic check enqueued while a backup destination is chosen, or cancels it. */
        fun syncSchedule(context: Context, enabled: Boolean) {
            if (enabled) schedule(context) else cancel(context)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
        }

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

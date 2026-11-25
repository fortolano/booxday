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
import kotlinx.coroutines.CancellationException
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
        val outcome = try {
            val settings = GoogleAuthManager(applicationContext)
            val cloud = settings.dueAutoBackupCloud() ?: return Result.success()
            backUp(
                target = BackupTarget(cloud, settings.backupAccountOf(cloud)),
                currentTarget = settings::currentBackupTarget,
                export = { ExportService(applicationContext).createBackup() },
                serviceFor = { cloudBackupService(applicationContext, it) }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Review, Nov 2026: nothing may escape as a crash; tried again later. Never the message,
            // it can hold content
            Log.w(TAG, "Automatic backup failed: ${e.javaClass.simpleName}")
            Outcome.RETRY
        }
        return when (outcome) {
            Outcome.DONE, Outcome.STOPPED -> Result.success()
            Outcome.RETRY -> Result.retry()
            Outcome.FAILED -> Result.failure()
        }
    }

    internal enum class Outcome { DONE, STOPPED, RETRY, FAILED }

    companion object {
        /**
         * One automatic backup for [target]. The export takes a while: the upload goes ahead only
         * while [currentTarget] is still [target] (checked before it starts, and between OneDrive's
         * pieces), so a switch to the other cloud or account meanwhile sends nothing to the old one.
         */
        internal suspend fun backUp(
            target: BackupTarget,
            currentTarget: () -> BackupTarget?,
            export: suspend () -> ExportService.ExportResult,
            serviceFor: (BackupCloud) -> CloudBackupService
        ): Outcome {
            Log.i(TAG, "Automatic ${target.cloud.key} backup due - exporting")
            val made = export()
            if (made !is ExportService.ExportResult.Success) {
                Log.w(TAG, "Automatic backup: export failed (${(made as? ExportService.ExportResult.Error)?.message})")
                return Outcome.RETRY
            }
            return try {
                when (val upload = serviceFor(target.cloud).uploadBackup(made.backupFile) { currentTarget() == target }) {
                    is DriveBackupService.UploadResult.Success -> {
                        Log.i(TAG, "Automatic backup uploaded: ${upload.fileName}")
                        Outcome.DONE
                    }
                    is DriveBackupService.UploadResult.Stopped -> {
                        Log.i(TAG, "Automatic backup stopped: another cloud or account was chosen")
                        Outcome.STOPPED
                    }
                    is DriveBackupService.UploadResult.Error -> {
                        Log.w(TAG, "Automatic backup: upload failed (retryable=${upload.retryable}, ${upload.message})")
                        if (upload.retryable) Outcome.RETRY else Outcome.FAILED
                    }
                }
            } finally {
                // The copy lives in the cloud; don't pile up a local file per automatic run
                made.backupFile.delete()
            }
        }

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

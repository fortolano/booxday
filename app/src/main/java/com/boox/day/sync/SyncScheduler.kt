package com.boox.day.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * SyncScheduler - Manages WorkManager jobs for calendar synchronization.
 *
 * Provides:
 * - Periodic sync scheduling (based on user-configured frequency)
 * - Immediate sync requests (on-demand)
 * - Sync job cancellation (on sign-out)
 * - Sync status observation
 */
class SyncScheduler(private val context: Context) {

    companion object {
        private const val TAG = "SyncScheduler"

        // Default sync parameters: 2 months past + present + 3 months future = 6 months total
        const val DEFAULT_MONTHS_BEFORE = 2
        const val DEFAULT_MONTHS_AFTER = 3

        private const val WORK_NAME_PENDING_PUSH = "calendar_sync_pending_push"
    }

    private val workManager = WorkManager.getInstance(context)

    // Network constraint - require network for sync
    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    // Periodic sync on "Sync frequency" is BackgroundSyncWorker (Oct 2026). The periodic SyncWorker
    // scheduler that stood here was never called, and its legacy pull covered one Google account only.

    /**
     * Request immediate sync (one-time).
     *
     * @param syncType Type of sync (full, push_only, pull_only)
     * @param monthsBefore Months to sync before current date
     * @param monthsAfter Months to sync after current date
     */
    fun requestImmediateSync(
        syncType: String = SyncWorker.SYNC_TYPE_FULL,
        monthsBefore: Int = DEFAULT_MONTHS_BEFORE,
        monthsAfter: Int = DEFAULT_MONTHS_AFTER
    ) {
        Log.d(TAG, "Requesting immediate sync: type=$syncType")

        val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraint)
            .setInputData(
                workDataOf(
                    SyncWorker.KEY_SYNC_TYPE to syncType,
                    SyncWorker.KEY_MONTHS_BEFORE to monthsBefore,
                    SyncWorker.KEY_MONTHS_AFTER to monthsAfter
                )
            )
            .addTag(SyncWorker.WORK_NAME_IMMEDIATE)
            .build()

        // Use REPLACE to cancel any pending immediate sync and start fresh
        workManager.enqueueUniqueWork(
            SyncWorker.WORK_NAME_IMMEDIATE,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

        Log.i(TAG, "Immediate sync requested")
    }

    /**
     * Request immediate push of pending changes only.
     */
    fun requestPushSync() {
        requestImmediateSync(syncType = SyncWorker.SYNC_TYPE_PUSH_ONLY)
    }

    /**
     * Push pending local changes (offline create/update/delete) as soon as a network is available
     * (plan Phase 2, Oct 2026 — the app promised "will sync when online" but never enqueued a push).
     * Appends to a running push instead of replacing it: cancelling a push mid-upload could
     * create an event remotely and then create it again on the next run.
     */
    fun requestPendingPush() {
        val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraint)
            .setInputData(workDataOf(SyncWorker.KEY_SYNC_TYPE to SyncWorker.SYNC_TYPE_PUSH_ONLY))
            .addTag(WORK_NAME_PENDING_PUSH)
            .build()
        workManager.enqueueUniqueWork(
            WORK_NAME_PENDING_PUSH,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            workRequest
        )
        Log.i(TAG, "Pending-change push requested (runs when online)")
    }

    /**
     * Request immediate pull from Google only.
     */
    fun requestPullSync(monthsBefore: Int = DEFAULT_MONTHS_BEFORE, monthsAfter: Int = DEFAULT_MONTHS_AFTER) {
        requestImmediateSync(
            syncType = SyncWorker.SYNC_TYPE_PULL_ONLY,
            monthsBefore = monthsBefore,
            monthsAfter = monthsAfter
        )
    }

    /**
     * Cancel all sync jobs (call on sign-out).
     */
    fun cancelAllSync() {
        Log.d(TAG, "Cancelling all sync jobs")
        workManager.cancelUniqueWork(SyncWorker.WORK_NAME_PERIODIC)
        workManager.cancelUniqueWork(SyncWorker.WORK_NAME_IMMEDIATE)
        Log.i(TAG, "All sync jobs cancelled")
    }

    /**
     * Cancel only periodic sync (keep immediate sync).
     */
    fun cancelPeriodicSync() {
        Log.d(TAG, "Cancelling periodic sync")
        workManager.cancelUniqueWork(SyncWorker.WORK_NAME_PERIODIC)
    }

    /**
     * Observe periodic sync status.
     */
    fun observePeriodicSyncStatus(): Flow<SyncJobStatus> {
        return workManager.getWorkInfosForUniqueWorkFlow(SyncWorker.WORK_NAME_PERIODIC)
            .map { workInfos ->
                workInfos.firstOrNull()?.toSyncJobStatus() ?: SyncJobStatus.NotScheduled
            }
    }

    /**
     * Observe immediate sync status.
     */
    fun observeImmediateSyncStatus(): Flow<SyncJobStatus> {
        return workManager.getWorkInfosForUniqueWorkFlow(SyncWorker.WORK_NAME_IMMEDIATE)
            .map { workInfos ->
                workInfos.firstOrNull()?.toSyncJobStatus() ?: SyncJobStatus.NotScheduled
            }
    }

    /**
     * Check if periodic sync is scheduled.
     */
    suspend fun isPeriodicSyncScheduled(): Boolean {
        val workInfos = workManager.getWorkInfosForUniqueWork(SyncWorker.WORK_NAME_PERIODIC).get()
        return workInfos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
    }

    /**
     * Check if immediate sync is running.
     */
    suspend fun isImmediateSyncRunning(): Boolean {
        val workInfos = workManager.getWorkInfosForUniqueWork(SyncWorker.WORK_NAME_IMMEDIATE).get()
        return workInfos.any { it.state == WorkInfo.State.RUNNING }
    }

    /**
     * Get last sync result from immediate sync.
     */
    suspend fun getLastImmediateSyncResult(): SyncJobResult? {
        val workInfos = workManager.getWorkInfosForUniqueWork(SyncWorker.WORK_NAME_IMMEDIATE).get()
        val workInfo = workInfos.firstOrNull { it.state == WorkInfo.State.SUCCEEDED } ?: return null

        return SyncJobResult(
            pushed = workInfo.outputData.getInt(SyncWorker.KEY_RESULT_PUSHED, 0),
            pulled = workInfo.outputData.getInt(SyncWorker.KEY_RESULT_PULLED, 0),
            conflicts = workInfo.outputData.getInt(SyncWorker.KEY_RESULT_CONFLICTS, 0),
            errors = workInfo.outputData.getInt(SyncWorker.KEY_RESULT_ERRORS, 0),
            message = workInfo.outputData.getString(SyncWorker.KEY_RESULT_MESSAGE) ?: ""
        )
    }

    /**
     * Convert WorkInfo to SyncJobStatus.
     */
    private fun WorkInfo.toSyncJobStatus(): SyncJobStatus {
        return when (state) {
            WorkInfo.State.ENQUEUED -> SyncJobStatus.Scheduled
            WorkInfo.State.RUNNING -> SyncJobStatus.Running
            WorkInfo.State.SUCCEEDED -> SyncJobStatus.Completed(
                SyncJobResult(
                    pushed = outputData.getInt(SyncWorker.KEY_RESULT_PUSHED, 0),
                    pulled = outputData.getInt(SyncWorker.KEY_RESULT_PULLED, 0),
                    conflicts = outputData.getInt(SyncWorker.KEY_RESULT_CONFLICTS, 0),
                    errors = outputData.getInt(SyncWorker.KEY_RESULT_ERRORS, 0),
                    message = outputData.getString(SyncWorker.KEY_RESULT_MESSAGE) ?: ""
                )
            )
            WorkInfo.State.FAILED -> SyncJobStatus.Failed(
                outputData.getString(SyncWorker.KEY_RESULT_MESSAGE) ?: "Unknown error"
            )
            WorkInfo.State.BLOCKED -> SyncJobStatus.Blocked
            WorkInfo.State.CANCELLED -> SyncJobStatus.Cancelled
        }
    }
}

// =============================================================================
// Data Classes
// =============================================================================

/**
 * Status of a sync job.
 */
sealed class SyncJobStatus {
    object NotScheduled : SyncJobStatus()
    object Scheduled : SyncJobStatus()
    object Running : SyncJobStatus()
    object Blocked : SyncJobStatus()
    object Cancelled : SyncJobStatus()
    data class Completed(val result: SyncJobResult) : SyncJobStatus()
    data class Failed(val error: String) : SyncJobStatus()
}

/**
 * Result of a completed sync job.
 */
data class SyncJobResult(
    val pushed: Int,
    val pulled: Int,
    val conflicts: Int,
    val errors: Int,
    val message: String
) {
    val hasErrors: Boolean get() = errors > 0
    val hasConflicts: Boolean get() = conflicts > 0
    val isSuccess: Boolean get() = !hasErrors

    fun toDisplayMessage(): String {
        return when {
            hasErrors -> "Sync completed with $errors error(s)"
            hasConflicts -> "Synced, $conflicts conflict(s) need resolution"
            pushed == 0 && pulled == 0 -> "Already up to date"
            else -> "Synced: $pushed uploaded, $pulled downloaded"
        }
    }
}

package com.boox.day.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.CalendarEventRepository
import com.boox.day.data.db.LocalEventRepository
import com.boox.day.sync.outlook.OutlookAuthManager
import com.boox.day.sync.outlook.OutlookCalendarService
import com.boox.day.sync.provider.GoogleCalendarProvider
import com.boox.day.sync.provider.OutlookCalendarProvider
import android.util.Log
import java.time.LocalDate

/**
 * SyncWorker - Background worker for calendar synchronization.
 *
 * Handles:
 * 1. Pushing pending local changes to cloud providers
 * 2. Pulling new/updated events from Google Calendar
 * 3. Conflict detection and marking
 *
 * Works in HYBRID mode only (skips in STANDALONE mode).
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SyncWorker"

        // Work request tags
        const val WORK_NAME_PERIODIC = "calendar_sync_periodic"
        const val WORK_NAME_IMMEDIATE = "calendar_sync_immediate"

        // Input data keys
        const val KEY_SYNC_TYPE = "sync_type"
        const val KEY_MONTHS_BEFORE = "months_before"
        const val KEY_MONTHS_AFTER = "months_after"

        // Output data keys
        const val KEY_RESULT_PUSHED = "pushed_count"
        const val KEY_RESULT_PULLED = "pulled_count"
        const val KEY_RESULT_CONFLICTS = "conflict_count"
        const val KEY_RESULT_ERRORS = "error_count"
        const val KEY_RESULT_MESSAGE = "result_message"

        // Sync types
        const val SYNC_TYPE_FULL = "full"           // Push + Pull
        const val SYNC_TYPE_PUSH_ONLY = "push_only" // Only push pending changes
        const val SYNC_TYPE_PULL_ONLY = "pull_only" // Only pull from Google
    }

    // Repositories - initialized lazily
    private val localEventRepository by lazy { LocalEventRepository(applicationContext) }
    private val calendarEventRepository by lazy { CalendarEventRepository(applicationContext) }
    private val authManager by lazy { GoogleAuthManager(applicationContext) }
    private val appDatabase by lazy { AppDatabase.getDatabase(applicationContext) }
    private val accountDao by lazy { appDatabase.connectedAccountDao() }
    private val selectionDao by lazy { appDatabase.calendarSelectionDao() }
    private val outlookAuthManager by lazy { OutlookAuthManager(applicationContext) }
    private val outlookCalendarService by lazy { OutlookCalendarService(outlookAuthManager) }
    private val calendarSyncManager by lazy {
        CalendarSyncManager(applicationContext, authManager, calendarEventRepository)
    }
    private val googleProvider by lazy {
        GoogleCalendarProvider(applicationContext, authManager, calendarSyncManager, accountDao, calendarEventRepository)
    }
    private val outlookProvider by lazy {
        OutlookCalendarProvider(applicationContext, outlookAuthManager, outlookCalendarService, accountDao)
    }
    private val multiAccountSyncManager by lazy {
        MultiAccountSyncManager(applicationContext, googleProvider, outlookProvider, accountDao, selectionDao, calendarEventRepository)
    }
    private val localEventService by lazy {
        LocalEventService(applicationContext, localEventRepository, calendarSyncManager, multiAccountSyncManager, authManager)
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "SyncWorker started")

        // Check if we're in hybrid mode. The saved settings: with no screen open, GlobalAppSettings
        // holds defaults (Private Mode), and the push skipped until BooxDay was opened (Oct 2026).
        if (workerAppSettings(applicationContext).syncMode != AppSettings.SyncMode.HYBRID) {
            Log.d(TAG, "Not in HYBRID mode, skipping sync")
            return Result.success(workDataOf(KEY_RESULT_MESSAGE to "Skipped: standalone mode"))
        }

        // Check if any accounts are connected
        if (!multiAccountSyncManager.hasAnyAccounts()) {
            Log.d(TAG, "No connected accounts, skipping sync")
            return Result.success(workDataOf(KEY_RESULT_MESSAGE to "Skipped: no connected accounts"))
        }

        // Get sync type from input
        val syncType = inputData.getString(KEY_SYNC_TYPE) ?: SYNC_TYPE_FULL
        val monthsBefore = inputData.getInt(KEY_MONTHS_BEFORE, SyncScheduler.DEFAULT_MONTHS_BEFORE)
        val monthsAfter = inputData.getInt(KEY_MONTHS_AFTER, SyncScheduler.DEFAULT_MONTHS_AFTER)

        Log.d(TAG, "Starting sync: type=$syncType, range=$monthsBefore-$monthsAfter months")

        var pushedCount = 0
        var pulledCount = 0
        var conflictCount = 0
        var errorCount = 0
        val errors = mutableListOf<String>()
        var retryDueToNetwork = false
        var retryMessage: String? = null

        try {
            // Step 1: Push pending local changes
            if (syncType == SYNC_TYPE_FULL || syncType == SYNC_TYPE_PUSH_ONLY) {
                Log.d(TAG, "Pushing pending changes...")
                val pushResult = localEventService.syncPendingChanges()
                pushedCount = pushResult.totalSynced
                errorCount += pushResult.errors.size
                errors.addAll(pushResult.errors)
                if (pushResult.errors.any(::isNetworkRelatedSyncError)) {
                    retryDueToNetwork = true
                    retryMessage = "Sync delayed: no internet connection while uploading changes"
                }
                Log.d(TAG, "Push complete: $pushedCount synced, ${pushResult.errors.size} errors")
            }

            // Step 2: Pull from Google Calendar (if signed in)
            if (syncType == SYNC_TYPE_FULL || syncType == SYNC_TYPE_PULL_ONLY) {
                if (authManager.isSignedIn()) {
                    Log.d(TAG, "Pulling from Google Calendar...")
                    val now = LocalDate.now()
                    val startDate = now.minusMonths(monthsBefore.toLong())
                    val endDate = now.plusMonths(monthsAfter.toLong())

                    val pullResult = localEventService.pullFromGoogle(startDate, endDate)
                    when (pullResult) {
                        is SyncResult.Success -> {
                            pulledCount = pullResult.eventCount
                            Log.d(TAG, "Pull complete: $pulledCount events")
                        }
                        is SyncResult.Error -> {
                            errorCount++
                            errors.add("Pull failed: ${pullResult.message}")
                            Log.e(TAG, "Pull failed: ${pullResult.message}")
                        }
                        is SyncResult.NotSignedIn -> {
                            Log.w(TAG, "Pull skipped: not signed in")
                        }
                        is SyncResult.InProgress -> {
                            Log.d(TAG, "Pull already in progress")
                        }
                        is SyncResult.NoNetwork -> {
                            retryDueToNetwork = true
                            retryMessage = pullResult.toDisplayMessage()
                            Log.w(TAG, "Pull skipped: no network (${pullResult.pendingChanges} pending)")
                        }
                        is SyncResult.PartialSuccess -> {
                            pulledCount = pullResult.successCount
                            errorCount += pullResult.failureCount
                            errors.addAll(pullResult.errors)
                            Log.w(TAG, "Pull partial: ${pullResult.successCount} ok, ${pullResult.failureCount} failed")
                        }
                    }
                } else {
                    Log.d(TAG, "Skipping Google pull: not signed in")
                }
            }

            // Step 3: Check for conflicts
            if (localEventService.hasConflicts()) {
                val conflicts = localEventService.getConflictEvents()
                conflictCount = conflicts.size
                Log.w(TAG, "Found $conflictCount unresolved conflicts")
            }

            // A network-blocked run should retry instead of pretending sync succeeded.
            if (retryDueToNetwork) {
                val message = retryMessage ?: "Sync delayed: no internet connection"
                Log.w(TAG, "$message - requesting retry")
                return Result.retry()
            }

            // Update last sync time
            authManager.setLastSyncTime(System.currentTimeMillis())

            // Build result message
            val message = buildString {
                append("Sync complete: ")
                if (pushedCount > 0) append("$pushedCount pushed, ")
                if (pulledCount > 0) append("$pulledCount pulled, ")
                if (conflictCount > 0) append("$conflictCount conflicts, ")
                if (errorCount > 0) append("$errorCount errors")
                if (isEmpty() || endsWith(": ")) append("no changes")
            }

            Log.i(TAG, message)

            return Result.success(
                workDataOf(
                    KEY_RESULT_PUSHED to pushedCount,
                    KEY_RESULT_PULLED to pulledCount,
                    KEY_RESULT_CONFLICTS to conflictCount,
                    KEY_RESULT_ERRORS to errorCount,
                    KEY_RESULT_MESSAGE to message
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "Sync failed with exception", e)
            return Result.failure(
                workDataOf(
                    KEY_RESULT_MESSAGE to "Sync failed: ${e.message}",
                    KEY_RESULT_ERRORS to 1
                )
            )
        }
    }

    private fun isNetworkRelatedSyncError(message: String): Boolean {
        return message.contains("no network", ignoreCase = true) ||
            message.contains("no internet", ignoreCase = true) ||
            message.contains("network error", ignoreCase = true)
    }
}

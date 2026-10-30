package com.boox.day.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.boox.day.APP_SETTINGS_KEY
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.KvProxy
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Background sync (task sync Groundwork, Oct 2026): every connected Google and Outlook account,
 * on the "Sync frequency" setting (4 h / 8 h / Daily) while "Background auto-sync" is on.
 * Until now nothing ran it: SyncScheduler.schedulePeriodicSync had no callers, so calendars synced
 * only while BooxDay was open (owner decision 12). Same dates as the calendar screen (2 months
 * back, 3 ahead). Task sync joins it in a later stage.
 *
 * Kept scheduled only while an account is connected ([schedule] runs at app start, when the
 * auto-sync switch or the frequency changes, and when an account is added or removed); each run
 * also checks Cloud Sync mode. BOOX may hold background work back while the tablet sleeps: opening
 * BooxDay still syncs as before.
 */
class BackgroundSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // A worker may run with no screen open: then MainActivity hasn't loaded the settings
        val settings = if (GlobalAppSettings.loaded) {
            GlobalAppSettings.current
        } else {
            runCatching { KvProxy(applicationContext).get(APP_SETTINGS_KEY, AppSettings.serializer()) }.getOrNull()
                ?: AppSettings(version = 1)
        }
        if (settings.syncMode != AppSettings.SyncMode.HYBRID) {
            Log.d(TAG, "Private Mode: no background sync")
            return Result.success()
        }
        val manager = MultiAccountSyncManager.create(applicationContext)
        if (!manager.hasAnyAccounts()) return Result.success()

        val today = LocalDate.now()
        val results = manager.syncAllAccounts(
            today.minusMonths(SyncScheduler.DEFAULT_MONTHS_BEFORE.toLong()),
            today.plusMonths(SyncScheduler.DEFAULT_MONTHS_AFTER.toLong())
        )
        Log.i(TAG, "Background sync: " + results.values.groupingBy { it::class.simpleName }.eachCount())
        // Offline or a failed account: the next period tries again (no retry backoff on top)
        return Result.success()
    }

    companion object {
        private const val TAG = "BackgroundSyncWorker"
        const val WORK_NAME = "background_sync"

        /** Brings the periodic work in line with the settings: enqueued, updated or cancelled. */
        suspend fun schedule(context: Context) {
            val auth = GoogleAuthManager(context.applicationContext)
            val hasAccounts = AppDatabase.getDatabase(context).connectedAccountDao().countActiveAccounts() > 0
            applySchedule(context, intervalHours(auth.isSyncEnabled(), hasAccounts, auth.getSyncFrequency()))
        }

        /** Hours between runs, or null when there is nothing to sync in the background. */
        internal fun intervalHours(autoSyncOn: Boolean, hasAccounts: Boolean, frequency: String): Long? {
            if (!autoSyncOn || !hasAccounts) return null
            return when (frequency) {
                GoogleAuthManager.FREQUENCY_1_HOUR -> 1L
                GoogleAuthManager.FREQUENCY_2_HOURS -> 2L
                GoogleAuthManager.FREQUENCY_8_HOURS -> 8L
                GoogleAuthManager.FREQUENCY_DAILY -> 24L
                else -> 4L
            }
        }

        internal fun applySchedule(context: Context, intervalHours: Long?) {
            val workManager = WorkManager.getInstance(context)
            if (intervalHours == null) {
                workManager.cancelUniqueWork(WORK_NAME)
                Log.i(TAG, "Background sync off")
                return
            }
            val request = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(intervalHours, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            // UPDATE keeps the current schedule when nothing changed and takes a new interval when it did
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
            Log.i(TAG, "Background sync every $intervalHours h")
        }
    }
}

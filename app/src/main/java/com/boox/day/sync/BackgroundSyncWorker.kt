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
 * The saved settings, for a worker. A worker often runs with no screen open: then MainActivity
 * hasn't loaded them and GlobalAppSettings holds the defaults (Private Mode), so they come from Kv.
 */
internal fun workerAppSettings(context: Context): AppSettings =
    if (GlobalAppSettings.loaded) {
        GlobalAppSettings.current
    } else {
        runCatching { KvProxy(context).get(APP_SETTINGS_KEY, AppSettings.serializer()) }.getOrNull()
            ?: AppSettings(version = 1)
    }

/**
 * Background sync (task sync Groundwork, Oct 2026): every connected Google and Outlook account,
 * on the "Sync frequency" setting (4 h / 8 h / Daily) while "Background auto-sync" is on.
 * Until now nothing ran it: SyncScheduler.schedulePeriodicSync had no callers, so calendars synced
 * only while BooxDay was open (owner decision 12). Same dates as the calendar screen (2 months
 * back, 3 ahead). Then tasks, when task sync is on: the same switch and frequency (owner decision 33).
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
        if (workerAppSettings(applicationContext).syncMode != AppSettings.SyncMode.HYBRID) {
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
        // Tasks too, when task sync is on (plan step 2.7); it looks at Google before sending
        if (com.boox.day.tasksync.TaskSync.isOn(applicationContext)) {
            Log.i(TAG, "Task sync: " + com.boox.day.tasksync.TaskSyncRun.run(applicationContext))
        }
        // Offline or a failed account: the next period tries again (no retry backoff on top)
        return Result.success()
    }

    companion object {
        private const val TAG = "BackgroundSyncWorker"
        const val WORK_NAME = "background_sync"

        /**
         * Brings the periodic work in line with the settings: enqueued, kept or cancelled.
         * [settingsChanged] (the auto-sync switch or the frequency): the work is replaced, so a new
         * interval takes effect. Otherwise (app start, an account added or removed) existing work
         * is left as it is: replacing it re-registered the system job at every start (seen on the
         * Go 10.3, 30 Oct 2026).
         */
        suspend fun schedule(context: Context, settingsChanged: Boolean = false) {
            val auth = GoogleAuthManager(context.applicationContext)
            val hasAccounts = AppDatabase.getDatabase(context).connectedAccountDao().countActiveAccounts() > 0
            applySchedule(context, intervalHours(auth.isSyncEnabled(), hasAccounts, auth.getSyncFrequency()), replace = settingsChanged)
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

        internal fun applySchedule(context: Context, intervalHours: Long?, replace: Boolean = true) {
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
            // UPDATE takes a new interval (same work, its timing kept); KEEP leaves existing work alone
            val policy = if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP
            workManager.enqueueUniquePeriodicWork(WORK_NAME, policy, request)
            Log.i(TAG, "Background sync every $intervalHours h (${policy.name})")
        }
    }
}

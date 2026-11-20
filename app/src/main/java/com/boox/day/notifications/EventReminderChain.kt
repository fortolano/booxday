package com.boox.day.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.boox.day.APP_SETTINGS_KEY
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.KvProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Event reminders where alarms can't be used (BOOX drops them): one queued job for all of them
 * (Nov 2026). The job sleeps until the earliest reminder, shows every reminder that has come due,
 * and queues itself for the next one.
 *
 * Before, each event had its own delayed job. A month of a busy calendar is hundreds, and
 * WorkManager hands the system only its 20 oldest jobs and runs in the app only its 200 oldest, so
 * nothing queued later ran: the sleep screen wasn't redrawn (Note Air 6C, 18 Nov 2026).
 *
 * "Handled until" is the time up to which reminders have been shown. A run shows the reminders
 * between it and now, so one cut short loses nothing and none is shown twice.
 */
object EventReminderChain {
    private const val TAG = "EventReminderChain"
    private const val WORK_NAME = "event_reminder_chain"
    private const val PREFS = "event_reminder_chain"
    private const val KEY_WAKE_AT = "wake_at"
    private const val KEY_HANDLED_UNTIL = "handled_until"
    private const val KEY_OLD_JOBS_RETIRED = "old_jobs_retired"
    private const val MINUTE_MS = 60_000L
    /** A reminder is still shown this long after the event started. */
    private const val GRACE_MS = 5 * MINUTE_MS
    private const val LOOK_AHEAD_MS = 30L * 24 * 60 * MINUTE_MS

    data class Reminder(val eventId: String, val title: String, val dayId: String, val startMs: Long)

    /** The reminders to show now: their time came after [handledUntil], and the event isn't long started. */
    internal fun due(reminders: List<Reminder>, minutesBefore: Int, handledUntil: Long, now: Long): List<Reminder> =
        reminders.filter {
            val at = it.startMs - minutesBefore * MINUTE_MS
            at > handledUntil && at <= now && it.startMs + GRACE_MS > now
        }

    /** When the earliest reminder still to come is due, or null when there is none. */
    internal fun nextWake(reminders: List<Reminder>, minutesBefore: Int, now: Long): Long? =
        reminders.map { it.startMs - minutesBefore * MINUTE_MS }.filter { it > now }.minOrNull()

    /** When the queued job runs, or null when none is queued. */
    fun wakeAt(context: Context): Long? = prefs(context).getLong(KEY_WAKE_AT, 0L).takeIf { it > 0L }

    /** Makes sure the job runs no later than [triggerMs]. A job already queued for earlier stays. */
    @Synchronized
    fun wakeBy(context: Context, triggerMs: Long, now: Long = System.currentTimeMillis()) {
        val app = context.applicationContext
        val prefs = prefs(app)
        val queued = wakeAt(app)
        if (queued != null && queued > now && queued <= triggerMs) return
        val request = OneTimeWorkRequestBuilder<EventReminderWorker>()
            .setInitialDelay((triggerMs - now).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        val edit = prefs.edit().putLong(KEY_WAKE_AT, triggerMs)
        if (!prefs.contains(KEY_HANDLED_UNTIL)) edit.putLong(KEY_HANDLED_UNTIL, now)  // a new chain shows reminders from now on
        edit.apply()
    }

    /**
     * The queued job's work: shows what came due and queues the next reminder. With event
     * notifications off, or where alarms do the job, nothing is shown and the chain ends.
     */
    suspend fun run(context: Context, now: Long = System.currentTimeMillis()) {
        val app = context.applicationContext
        prefs(app).edit().remove(KEY_WAKE_AT).apply()  // the queued job is this run
        // The saved settings: GlobalAppSettings holds defaults in a process started for this job
        val settings = runCatching { KvProxy(app).get(APP_SETTINGS_KEY, AppSettings.serializer()) }.getOrNull()
        if (settings?.eventNotificationsEnabled != true || !EventNotificationScheduler(app).usesQueuedJob()) {
            prefs(app).edit().remove(KEY_HANDLED_UNTIL).apply()
            return
        }

        val minutes = settings.defaultEventReminderMinutes
        val reminders = load(app, now, minutes)
        val handledUntil = prefs(app).getLong(KEY_HANDLED_UNTIL, now)
        if (NotificationPermissionHelper.hasNotificationPermission(app)) {
            val helper = NotificationHelper(app)
            due(reminders, minutes, handledUntil, now).forEach {
                helper.showEventReminderNotification(it.eventId, it.title, it.dayId, minutes)
            }
        }
        prefs(app).edit().putLong(KEY_HANDLED_UNTIL, now).apply()
        // Last: queueing the next job replaces, and so cancels, the one this may be running in
        nextWake(reminders, minutes, now)?.let { wakeBy(app, it, now) }
    }

    /** Events that start from a little before [now] to a month ahead, from both tables. */
    private suspend fun load(context: Context, now: Long, minutesBefore: Int): List<Reminder> {
        val db = AppDatabase.getDatabase(context)
        val from = now - GRACE_MS
        val to = now + LOOK_AHEAD_MS + minutesBefore * MINUTE_MS
        return db.localEventDao().getUpcomingEventsBetween(from, to).map { Reminder(it.id, it.title, it.dayId, it.startTime) } +
            db.calendarEventDao().getUpcomingEventsBetween(from, to).map { Reminder(it.id, it.title, it.dayId, it.startTime) }
    }

    /** Ends the chain: no job queued, nothing remembered. */
    @Synchronized
    fun stop(context: Context) {
        val app = context.applicationContext
        WorkManager.getInstance(app).cancelUniqueWork(WORK_NAME)
        prefs(app).edit().remove(KEY_WAKE_AT).remove(KEY_HANDLED_UNTIL).apply()
    }

    /** Cancels the jobs versions up to 1.0.64 queued, one per event. */
    fun retireOldJobs(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(EventNotificationWorker::class.java.name).result.get()
    }

    /**
     * BooxDayApp.onCreate: once after the update, the old jobs go and the chain starts in their place
     * (the first run finds the next reminder). Off the main thread.
     */
    fun onAppStart(context: Context, appScope: CoroutineScope) {
        val app = context.applicationContext
        if (prefs(app).getBoolean(KEY_OLD_JOBS_RETIRED, false)) return
        appScope.launch(Dispatchers.IO) {
            runCatching {
                retireOldJobs(app)
                wakeBy(app, System.currentTimeMillis())
                prefs(app).edit().putBoolean(KEY_OLD_JOBS_RETIRED, true).apply()
            }.onFailure { Log.w(TAG, "Old reminder jobs not cancelled: ${it.javaClass.simpleName}") }
        }
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** The one queued job of [EventReminderChain]. */
class EventReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        EventReminderChain.run(applicationContext)
        Result.success()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e("EventReminderWorker", "Event reminders not shown: ${e.javaClass.simpleName}")
        Result.retry()
    }
}

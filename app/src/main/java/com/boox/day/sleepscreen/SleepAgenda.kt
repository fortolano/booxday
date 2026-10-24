package com.boox.day.sleepscreen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.boox.day.APP_SETTINGS_KEY
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.KvProxy
import com.boox.day.today.DayDataWatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * "Today on sleep screen" (Oct 2026): keeps today's agenda on the BOOX sleep screen while the
 * setting is on ([AppSettings.sleepScreenAgendaEnabled], Preferences). The image is drawn and handed
 * over by [SleepScreenPublisher], always in a [SleepAgendaWorker] so it also happens with the app closed.
 *
 * When it is redrawn:
 * - a few seconds after the day's data changes ([DayDataWatcher]; a minute after writing ink),
 * - when BooxDay goes to the background (the tablet is often put to sleep right after),
 * - just after midnight (a daily job), and on unlock if the image shows another day,
 * - right away when the setting is turned on.
 */
object SleepAgenda {
    private const val TAG = "SleepAgenda"
    private const val WORK_REFRESH = "sleep_agenda_refresh"
    private const val WORK_DAILY = "sleep_agenda_daily"

    private val watcher = DayDataWatcher { requestRefresh(appContext ?: return@DayDataWatcher) }
    private var appContext: Context? = null
    private var scope: CoroutineScope? = null
    private var unlockReceiver: BroadcastReceiver? = null

    /** The BOOX sleep screen is only there on Onyx tablets. */
    val isSupported: Boolean
        get() = listOf(Build.MANUFACTURER, Build.BRAND, Build.PRODUCT).any { it?.lowercase(Locale.ROOT)?.contains("onyx") == true }

    /**
     * The saved settings: right in a cold process too (a worker starting before the app has loaded
     * GlobalAppSettings, which then holds defaults). Falls back to the in-memory ones.
     */
    fun settings(context: Context): AppSettings =
        runCatching { KvProxy(context.applicationContext).get(APP_SETTINGS_KEY, AppSettings.serializer()) }.getOrNull()
            ?: GlobalAppSettings.current

    fun isEnabled(context: Context): Boolean = isSupported && settings(context).sleepScreenAgendaEnabled

    /** BooxDayApp.onCreate: follow the day's changes while the setting is on. */
    fun onAppStart(context: Context, appScope: CoroutineScope) {
        val app = context.applicationContext
        appContext = app
        scope = appScope
        if (!isSupported) return
        // The saved settings, off the main thread (GlobalAppSettings is loaded later, by MainActivity)
        appScope.launch(Dispatchers.IO) {
            if (isEnabled(app)) withContext(Dispatchers.Main) { startFollowing(app) }
        }
    }

    /** MainActivity.onStop: the tablet may go to sleep now. */
    fun onAppBackground(context: Context) {
        if (isSupported && GlobalAppSettings.current.sleepScreenAgendaEnabled) requestRefresh(context, delaySeconds = 0)
    }

    /** Preferences switch. On: draw now and keep it current. Off: stop, and take the image away. */
    fun setEnabled(context: Context, enabled: Boolean) {
        val app = context.applicationContext
        if (enabled) {
            startFollowing(app)
            requestRefresh(app, delaySeconds = 0, force = true)
        } else {
            stopFollowing(app)
            WorkManager.getInstance(app).cancelUniqueWork(WORK_REFRESH)
            SleepScreenPublisher.withdraw(app)
        }
    }

    fun requestRefresh(context: Context, delaySeconds: Long = 0, force: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<SleepAgendaWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setInputData(androidx.work.workDataOf(SleepAgendaWorker.KEY_FORCE to force))
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(WORK_REFRESH, ExistingWorkPolicy.REPLACE, request)
    }

    private fun startFollowing(context: Context) {
        val app = context.applicationContext
        appContext = app
        scope?.let { watcher.start(app, it) }
        scheduleDaily(app)
        if (unlockReceiver == null) {
            // Back from sleep with yesterday's image (the tablet slept through midnight): redraw
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    if (SleepScreenPublisher.shownDate(c) != LocalDate.now().toString()) requestRefresh(c)
                }
            }
            runCatching {
                androidx.core.content.ContextCompat.registerReceiver(
                    app, receiver, IntentFilter(Intent.ACTION_USER_PRESENT), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
                )
            }
                .onSuccess { unlockReceiver = receiver }
                .onFailure { Log.w(TAG, "Unlock receiver not registered", it) }
        }
    }

    private fun stopFollowing(context: Context) {
        watcher.stop()
        WorkManager.getInstance(context).cancelUniqueWork(WORK_DAILY)
        unlockReceiver?.let { runCatching { context.applicationContext.unregisterReceiver(it) } }
        unlockReceiver = null
    }

    /** A job a minute after midnight every day (WorkManager: BOOX drops AlarmManager alarms). */
    private fun scheduleDaily(context: Context) {
        val now = LocalDateTime.now()
        val next = now.toLocalDate().plusDays(1).atTime(LocalTime.of(0, 1))
        val request = PeriodicWorkRequestBuilder<SleepAgendaWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_DAILY, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

/** Draws and hands over the sleep-screen agenda, when the setting is on. */
class SleepAgendaWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Forced = just turned on (the saved settings may not say so yet); turning off cancels this work
        val force = inputData.getBoolean(KEY_FORCE, false)
        if (!SleepAgenda.isSupported || !force && !SleepAgenda.isEnabled(applicationContext)) return Result.success()
        SleepScreenPublisher.publish(applicationContext, force = force)
        return Result.success()
    }

    companion object {
        const val KEY_FORCE = "force"
    }
}

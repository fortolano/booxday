package com.boox.day.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.boox.day.APP_SETTINGS_KEY
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.KvProxy
import com.boox.day.today.ShownEvents
import kotlinx.coroutines.CancellationException

/**
 * Unique WorkManager worker that performs bulk reminder rescheduling off the broadcast main
 * thread after boot or timezone changes.
 */
class NotificationRescheduleWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "NotifRescheduleWorker"
        const val WORK_NAME = "notification_reschedule"
        const val KEY_REASON = "reason"
        const val REASON_BOOT = "boot"
        const val REASON_TIMEZONE = "timezone"

        fun enqueue(context: Context, reason: String) {
            val request = OneTimeWorkRequestBuilder<NotificationRescheduleWorker>()
                .setInputData(Data.Builder().putString(KEY_REASON, reason).build())
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }

    override suspend fun doWork(): Result {
        val reason = inputData.getString(KEY_REASON) ?: "unknown"
        return try {
            reschedule(applicationContext, reason)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Reschedule failed ($reason): ${e.javaClass.simpleName}")
            Result.retry()
        }
    }

    internal suspend fun reschedule(
        context: Context,
        reason: String,
        loadSettings: suspend (Context) -> AppSettings? = {
            KvProxy(it).get(APP_SETTINGS_KEY, AppSettings.serializer())
        }
    ) {
        val app = context.applicationContext
        val settings = loadSettings(app) ?: AppSettings(version = 1)
        val taskScheduler = TaskNotificationScheduler(app)
        val eventScheduler = EventNotificationScheduler(app)
        val db = AppDatabase.getDatabase(app)

        if (settings.taskNotificationsEnabled) {
            taskScheduler.scheduleDailyScan()
            val tasks = db.taskDao().getIncompleteTasWithDueDates()
            var count = 0
            tasks.forEach { task ->
                taskScheduler.cancelTaskAlarm(task.id)
                val result = taskScheduler.scheduleTaskAlarm(
                    task,
                    settings.defaultTaskReminderHour,
                    settings.defaultTaskReminderMinute
                )
                if (result is TaskNotificationScheduler.ScheduleResult.ExactAlarm ||
                    result is TaskNotificationScheduler.ScheduleResult.WorkManagerFallback
                ) {
                    count++
                }
            }
            Log.i(TAG, "Rescheduled $count task alarms ($reason)")
        } else {
            taskScheduler.cancelDailyScan()
        }

        if (settings.eventNotificationsEnabled) {
            val now = System.currentTimeMillis()
            val thirtyDaysFromNow = now + (30L * 24 * 60 * 60 * 1000L)
            val localEvents = db.localEventDao().getUpcomingEventsBetween(now, thirtyDaysFromNow)
            val calendarEvents = db.calendarEventDao().getUpcomingEventsBetween(now, thirtyDaysFromNow)
            val tabletOnly = ShownEvents.tabletOnly(calendarEvents, localEvents)
            val tabletOnlyIds = tabletOnly.mapTo(HashSet()) { it.id }

            if (eventScheduler.usesQueuedJob()) {
                EventReminderChain.resetWake(app)
            }
            localEvents.forEach { event ->
                if (event.id !in tabletOnlyIds) {
                    eventScheduler.cancelEventAlarm(event.id)
                }
            }

            var count = 0
            tabletOnly.forEach { event ->
                eventScheduler.cancelEventAlarm(event.id)
                val result = eventScheduler.scheduleEventAlarm(event, settings.defaultEventReminderMinutes)
                if (result is EventNotificationScheduler.ScheduleResult.ExactAlarm ||
                    result is EventNotificationScheduler.ScheduleResult.WorkManagerFallback
                ) {
                    count++
                }
            }
            calendarEvents.forEach { event ->
                eventScheduler.cancelEventAlarm(event.id)
                val result = eventScheduler.scheduleCalendarEventAlarm(event, settings.defaultEventReminderMinutes)
                if (result is EventNotificationScheduler.ScheduleResult.ExactAlarm ||
                    result is EventNotificationScheduler.ScheduleResult.WorkManagerFallback
                ) {
                    count++
                }
            }
            Log.i(TAG, "Rescheduled $count event alarms ($reason: ${tabletOnly.size} tablet-only + ${calendarEvents.size} synced)")
        } else {
            EventReminderChain.stop(app)
        }
    }
}

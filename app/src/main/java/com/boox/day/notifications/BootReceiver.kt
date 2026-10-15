package com.boox.day.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.boox.day.APP_SETTINGS_KEY
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.KvProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver for device boot completion.
 *
 * When device reboots, all AlarmManager alarms are lost. This receiver:
 * 1. Re-schedules the daily scan WorkManager job
 * 2. Re-schedules individual task alarms for tasks with due dates and reminders
 * 3. Re-schedules event reminder alarms
 *
 * Requires RECEIVE_BOOT_COMPLETED permission in AndroidManifest.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        Log.i(TAG, "Device booted - rescheduling notifications")

        // Use goAsync() to allow longer processing
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = KvProxy(context).get(APP_SETTINGS_KEY, AppSettings.serializer())
                    ?: AppSettings(version = 1)

                // Check if either task or event notifications are enabled
                if (!settings.taskNotificationsEnabled && !settings.eventNotificationsEnabled) {
                    Log.d(TAG, "Both task and event notifications disabled, skipping reschedule")
                    return@launch
                }
                rescheduleAllAlarms(context, settings)
            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling alarms: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Reschedule all notification alarms after boot.
     */
    private suspend fun rescheduleAllAlarms(
        context: Context,
        settings: AppSettings
    ) {
        val scheduler = TaskNotificationScheduler(context)

        // 1. Reschedule daily scan WorkManager job
        scheduler.scheduleDailyScan()
        Log.d(TAG, "Rescheduled daily scan worker")

        // 2. Reschedule task alarms
        rescheduleTaskAlarms(context, scheduler, settings)

        // 3. Reschedule event alarms
        rescheduleEventAlarms(context, settings)
    }

    /**
     * Reschedule alarms for all tasks with due dates.
     */
    private suspend fun rescheduleTaskAlarms(
        context: Context,
        scheduler: TaskNotificationScheduler,
        settings: com.boox.day.data.datastore.AppSettings
    ) {
        if (!settings.taskNotificationsEnabled) {
            Log.d(TAG, "Task notifications disabled, skipping task alarm reschedule")
            return
        }

        val taskDao = AppDatabase.getDatabase(context).taskDao()

        // Get all incomplete tasks with due dates
        val tasksWithDueDates = taskDao.getIncompleteTasWithDueDates()

        var scheduledCount = 0
        tasksWithDueDates.forEach { task ->
            val result = scheduler.scheduleTaskAlarm(
                task,
                settings.defaultTaskReminderHour,
                settings.defaultTaskReminderMinute
            )
            if (result is TaskNotificationScheduler.ScheduleResult.ExactAlarm ||
                result is TaskNotificationScheduler.ScheduleResult.WorkManagerFallback) {
                scheduledCount++
            }
        }

        Log.i(TAG, "Rescheduled $scheduledCount task alarms")
    }

    /**
     * Reschedule alarms for events with reminders.
     * Queries BOTH tables: LocalEvent (standalone) and CalendarEvent (synced Google/Outlook).
     */
    private suspend fun rescheduleEventAlarms(context: Context, settings: AppSettings) {
        if (!settings.eventNotificationsEnabled) {
            Log.d(TAG, "Event notifications disabled, skipping event alarm reschedule")
            return
        }

        val scheduler = EventNotificationScheduler(context)
        val db = AppDatabase.getDatabase(context)
        val localEventDao = db.localEventDao()
        val calendarEventDao = db.calendarEventDao()

        // Get upcoming events within the next 30 days from BOTH tables
        val now = System.currentTimeMillis()
        val thirtyDaysFromNow = now + (30 * 24 * 60 * 60 * 1000L)

        val localEvents = localEventDao.getUpcomingEventsBetween(now, thirtyDaysFromNow)
        val calendarEvents = calendarEventDao.getUpcomingEventsBetween(now, thirtyDaysFromNow)

        var scheduledCount = 0

        // Schedule LocalEvent notifications
        localEvents.forEach { event ->
            val result = scheduler.scheduleEventAlarm(event, settings.defaultEventReminderMinutes)
            if (result is EventNotificationScheduler.ScheduleResult.ExactAlarm ||
                result is EventNotificationScheduler.ScheduleResult.WorkManagerFallback) {
                scheduledCount++
            }
        }

        // Schedule CalendarEvent notifications (synced Google/Outlook)
        calendarEvents.forEach { event ->
            val result = scheduler.scheduleCalendarEventAlarm(event, settings.defaultEventReminderMinutes)
            if (result is EventNotificationScheduler.ScheduleResult.ExactAlarm ||
                result is EventNotificationScheduler.ScheduleResult.WorkManagerFallback) {
                scheduledCount++
            }
        }

        Log.i(TAG, "Rescheduled $scheduledCount event alarms (${localEvents.size} local + ${calendarEvents.size} synced)")
    }
}

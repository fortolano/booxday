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
 * BroadcastReceiver for timezone changes.
 *
 * When device timezone changes, scheduled alarms fire at the wrong local time.
 * This receiver reschedules all alarms with the new timezone offset.
 *
 * Example: User in NYC (EST) has alarm at 9 AM EST. They travel to LA (PST).
 * Without rescheduling, alarm would fire at 6 AM PST (still 9 AM EST).
 * After rescheduling, alarm fires at 9 AM PST as expected.
 */
class TimeZoneChangeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TimeZoneChangeReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIMEZONE_CHANGED) {
            return
        }

        Log.i(TAG, "Timezone changed - rescheduling all notifications")

        // CRITICAL: Load settings from persistent storage, not in-memory cache
        // GlobalAppSettings.current is uninitialized when receiver starts in cold process
        val settings = try {
            KvProxy(context).get(APP_SETTINGS_KEY, AppSettings.serializer())
                ?: AppSettings(version = 1)  // Use defaults if not found
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load settings from storage: ${e.message ?: e.javaClass.simpleName}, using defaults", e)
            AppSettings(version = 1)
        }

        if (!settings.taskNotificationsEnabled && !settings.eventNotificationsEnabled) {
            Log.d(TAG, "Both task and event notifications disabled (loaded from storage), skipping reschedule")
            return
        }

        // Use goAsync() to allow longer processing
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                rescheduleTaskAlarms(context, settings)
                rescheduleEventAlarms(context, settings)
            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling alarms: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Reschedule all task alarms with new timezone.
     */
    private suspend fun rescheduleTaskAlarms(
        context: Context,
        settings: com.boox.day.data.datastore.AppSettings
    ) {
        if (!settings.taskNotificationsEnabled) {
            Log.d(TAG, "Task notifications disabled, skipping task alarm reschedule")
            return
        }

        val scheduler = TaskNotificationScheduler(context)
        val taskDao = AppDatabase.getDatabase(context).taskDao()

        // Get all incomplete tasks with due dates
        val tasksWithDueDates = taskDao.getIncompleteTasWithDueDates()

        var scheduledCount = 0
        tasksWithDueDates.forEach { task ->
            // Cancel existing alarm first
            scheduler.cancelTaskAlarm(task.id)

            // Schedule with new timezone
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

        Log.i(TAG, "Rescheduled $scheduledCount task alarms for new timezone")
    }

    /**
     * Reschedule all event alarms with new timezone.
     * Queries BOTH tables: LocalEvent (standalone) and CalendarEvent (synced Google/Outlook).
     */
    private suspend fun rescheduleEventAlarms(
        context: Context,
        settings: com.boox.day.data.datastore.AppSettings
    ) {
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

        // Reschedule LocalEvent notifications
        localEvents.forEach { event ->
            // Cancel existing alarm first
            scheduler.cancelEventAlarm(event.id)

            // Schedule with new timezone
            val result = scheduler.scheduleEventAlarm(event, settings.defaultEventReminderMinutes)
            if (result is EventNotificationScheduler.ScheduleResult.ExactAlarm ||
                result is EventNotificationScheduler.ScheduleResult.WorkManagerFallback) {
                scheduledCount++
            }
        }

        // Reschedule CalendarEvent notifications (synced Google/Outlook)
        calendarEvents.forEach { event ->
            // Cancel existing alarm first
            scheduler.cancelEventAlarm(event.id)

            // Schedule with new timezone
            val result = scheduler.scheduleCalendarEventAlarm(event, settings.defaultEventReminderMinutes)
            if (result is EventNotificationScheduler.ScheduleResult.ExactAlarm ||
                result is EventNotificationScheduler.ScheduleResult.WorkManagerFallback) {
                scheduledCount++
            }
        }

        Log.i(TAG, "Rescheduled $scheduledCount event alarms for new timezone (${localEvents.size} local + ${calendarEvents.size} synced)")
    }
}

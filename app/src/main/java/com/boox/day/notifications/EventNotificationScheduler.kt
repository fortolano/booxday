package com.boox.day.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.WorkManager
import com.boox.day.data.db.CalendarEvent
import com.boox.day.data.db.LocalEvent
import java.util.concurrent.TimeUnit

/**
 * Schedules notifications for event reminders using AlarmManager.
 *
 * Architecture:
 * - Uses setExactAndAllowWhileIdle() for precise notifications (Doze-aware)
 * - Falls back to WorkManager if exact alarm permission not granted (Android 12+)
 * - Falls back to WorkManager on Boox devices (firmware restrictions)
 * - Each event gets its own alarm keyed by eventId
 *
 * Key difference from TaskNotificationScheduler:
 * - Tasks: Reminder at fixed time on due date (e.g., 9 AM)
 * - Events: Reminder N minutes BEFORE event start time
 *
 * Alarm lifecycle:
 * - Schedule: When event is created or updated (with valid reminder time)
 * - Cancel: When event is deleted
 * - Reschedule: When event start time changes
 *
 * @see EventAlarmReceiver for handling fired alarms
 * @see BootReceiver for rescheduling after device restart
 */
class EventNotificationScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    companion object {
        private const val TAG = "EventNotifScheduler"

        // Intent extras for alarm receiver
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_EVENT_DAY_ID = "event_day_id"
        const val EXTRA_MINUTES_BEFORE = "minutes_before"

        // Request code prefix to ensure unique PendingIntents per event
        // Different from tasks (10000) to avoid collisions
        private const val REQUEST_CODE_PREFIX = 20000

        // WorkManager tags
        const val WORK_TAG_EVENT_FALLBACK = "event_notification_fallback"

        // Flag to log device detection only once per session
        private var deviceTypeLogged = false

        /**
         * Check if running on a Boox/Onyx device.
         * These devices have firmware-level AlarmManager restrictions that silently
         * drop alarms even when all permissions are granted. Use WorkManager instead.
         */
        private fun isBooxDevice(): Boolean {
            val manufacturer = Build.MANUFACTURER?.lowercase() ?: ""
            val brand = Build.BRAND?.lowercase() ?: ""
            val device = Build.DEVICE?.lowercase() ?: ""
            val model = Build.MODEL?.lowercase() ?: ""
            val product = Build.PRODUCT?.lowercase() ?: ""
            val isBoox = manufacturer.contains("onyx") || brand.contains("onyx") ||
                         device.contains("onyx") || model.contains("onyx") || product.contains("onyx") ||
                         manufacturer.contains("boox") || brand.contains("boox") ||
                         device.contains("boox") || model.contains("boox") || product.contains("boox")

            // Log once for debugging
            if (!deviceTypeLogged) {
                Log.i(
                    TAG,
                    "Device detection: manufacturer=$manufacturer, brand=$brand, device=$device, " +
                        "model=$model, product=$product, isBoox=$isBoox"
                )
                deviceTypeLogged = true
            }

            return isBoox
        }
    }

    /**
     * Check if WorkManager should be forced for notifications.
     * Returns true if either:
     * - SharedPreferences override flag is set (persisted on Boox device startup)
     * - Runtime device detection identifies Boox device
     *
     * This provides a hard failsafe - even if runtime detection fails,
     * the flag persists from app startup.
     */
    private fun shouldForceWorkManager(): Boolean {
        val prefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
        val override = prefs.getBoolean("force_workmanager_notifications", false)
        return override || isBooxDevice()
    }

    /**
     * Result of scheduling attempt.
     * Callers should handle degraded modes appropriately in UI.
     */
    sealed class ScheduleResult {
        /** Alarm scheduled successfully with exact timing */
        object ExactAlarm : ScheduleResult()

        /** Exact alarms not available, using WorkManager (may be less precise) */
        object WorkManagerFallback : ScheduleResult()

        /** Notification permission not granted - cannot show notification */
        object NoNotificationPermission : ScheduleResult()

        /** Notification channel disabled by user */
        object ChannelDisabled : ScheduleResult()

        /** Event reminder time has already passed */
        object AlreadyPast : ScheduleResult()

        /** Event is invalid (null, deleted, or missing start time) */
        object InvalidEvent : ScheduleResult()
    }

    /**
     * Schedule notification alarm for an event.
     *
     * Prerequisites:
     * - Event must have a startTime set
     * - Event must be in the future (considering reminder time)
     *
     * @param event The event to schedule notification for
     * @param minutesBefore Minutes before event to send reminder (5, 15, 30, 60, 1440)
     * @return ScheduleResult indicating success or failure mode
     */
    fun scheduleEventAlarm(
        event: LocalEvent,
        minutesBefore: Int
    ): ScheduleResult {
        // Validate event
        if (event.startTime <= 0) {
            Log.d(TAG, "Event ${event.id} has no start time, not scheduling")
            return ScheduleResult.InvalidEvent
        }

        // Check notification permission
        if (!NotificationPermissionHelper.hasNotificationPermission(context)) {
            Log.w(TAG, "No notification permission, cannot schedule event ${event.id}")
            return ScheduleResult.NoNotificationPermission
        }
        if (!NotificationChannelManager.isChannelEnabled(context, NotificationChannelManager.CHANNEL_ID_EVENT_REMINDERS)) {
            Log.w(TAG, "Event notification channel disabled, cannot schedule event ${event.id}")
            return ScheduleResult.ChannelDisabled
        }

        // Calculate trigger time: event start - reminder minutes
        val triggerMillis = event.startTime - (minutesBefore * 60 * 1000L)

        // Don't schedule if already past
        if (triggerMillis <= System.currentTimeMillis()) {
            Log.d(TAG, "Event ${event.id} reminder time already passed")
            return ScheduleResult.AlreadyPast
        }

        // Create pending intent for alarm
        val pendingIntent = createPendingIntent(event.id, event.dayId, minutesBefore)
        if (pendingIntent == null) {
            Log.e(TAG, "Failed to create PendingIntent for event ${event.id}")
            return ScheduleResult.InvalidEvent
        }

        // Check exact alarm permission (Android 12+)
        if (!NotificationPermissionHelper.canScheduleExactAlarms(context)) {
            Log.w(TAG, "No exact alarm permission, using WorkManager fallback for event ${event.id}")
            scheduleWithWorkManager(event.id, event.dayId, minutesBefore, triggerMillis)
            return ScheduleResult.WorkManagerFallback
        }

        // Boox/Onyx devices silently drop AlarmManager alarms at firmware level
        // even when all permissions are granted. Use WorkManager instead.
        // Also check SharedPreferences override flag for hard failsafe.
        if (shouldForceWorkManager()) {
            Log.d(TAG, "Using WorkManager for event ${event.id} (forceWorkManager=true, isBoox=${isBooxDevice()})")
            scheduleWithWorkManager(event.id, event.dayId, minutesBefore, triggerMillis)
            return ScheduleResult.WorkManagerFallback
        }

        // Schedule exact alarm
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled exact alarm for event ${event.id} (${event.title}) $minutesBefore min before start")
            return ScheduleResult.ExactAlarm
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling alarm for event ${event.id}: ${e.message}")
            scheduleWithWorkManager(event.id, event.dayId, minutesBefore, triggerMillis)
            return ScheduleResult.WorkManagerFallback
        }
    }

    /**
     * Schedule notification alarm for a CalendarEvent (synced Google/Outlook event).
     * Same logic as scheduleEventAlarm but for CalendarEvent type.
     *
     * @param event The calendar event to schedule notification for
     * @param minutesBefore Minutes before event to send reminder
     * @return ScheduleResult indicating success or failure mode
     */
    fun scheduleCalendarEventAlarm(
        event: CalendarEvent,
        minutesBefore: Int
    ): ScheduleResult {
        // Validate event
        if (event.startTime <= 0) {
            Log.d(TAG, "CalendarEvent ${event.id} has no start time, not scheduling")
            return ScheduleResult.InvalidEvent
        }

        // Check notification permission
        if (!NotificationPermissionHelper.hasNotificationPermission(context)) {
            Log.w(TAG, "No notification permission, cannot schedule CalendarEvent ${event.id}")
            return ScheduleResult.NoNotificationPermission
        }
        if (!NotificationChannelManager.isChannelEnabled(context, NotificationChannelManager.CHANNEL_ID_EVENT_REMINDERS)) {
            Log.w(TAG, "Event notification channel disabled, cannot schedule CalendarEvent ${event.id}")
            return ScheduleResult.ChannelDisabled
        }

        // Calculate trigger time: event start - reminder minutes
        val triggerMillis = event.startTime - (minutesBefore * 60 * 1000L)

        // Don't schedule if already past
        if (triggerMillis <= System.currentTimeMillis()) {
            Log.d(TAG, "CalendarEvent ${event.id} reminder time already passed")
            return ScheduleResult.AlreadyPast
        }

        // Create pending intent for alarm
        val pendingIntent = createPendingIntent(event.id, event.dayId, minutesBefore)
        if (pendingIntent == null) {
            Log.e(TAG, "Failed to create PendingIntent for CalendarEvent ${event.id}")
            return ScheduleResult.InvalidEvent
        }

        // Check exact alarm permission (Android 12+)
        if (!NotificationPermissionHelper.canScheduleExactAlarms(context)) {
            Log.w(TAG, "No exact alarm permission, using WorkManager fallback for CalendarEvent ${event.id}")
            scheduleWithWorkManager(event.id, event.dayId, minutesBefore, triggerMillis)
            return ScheduleResult.WorkManagerFallback
        }

        // Boox/Onyx devices silently drop AlarmManager alarms at firmware level
        // Also check SharedPreferences override flag for hard failsafe.
        if (shouldForceWorkManager()) {
            Log.d(TAG, "Using WorkManager for CalendarEvent ${event.id} (forceWorkManager=true, isBoox=${isBooxDevice()})")
            scheduleWithWorkManager(event.id, event.dayId, minutesBefore, triggerMillis)
            return ScheduleResult.WorkManagerFallback
        }

        // Schedule exact alarm
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled exact alarm for CalendarEvent ${event.id} (${event.title}) $minutesBefore min before start")
            return ScheduleResult.ExactAlarm
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling alarm for CalendarEvent ${event.id}: ${e.message}")
            scheduleWithWorkManager(event.id, event.dayId, minutesBefore, triggerMillis)
            return ScheduleResult.WorkManagerFallback
        }
    }

    /**
     * Cancel alarm for a specific event.
     * Safe to call even if no alarm was scheduled.
     *
     * @param eventId The event ID to cancel alarm for
     */
    fun cancelEventAlarm(eventId: String) {
        val pendingIntent = createPendingIntent(eventId, "", 0, PendingIntent.FLAG_NO_CREATE)
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled alarm for event $eventId")
        }

        // Also cancel any WorkManager fallback
        WorkManager.getInstance(context).cancelAllWorkByTag("$WORK_TAG_EVENT_FALLBACK:$eventId")
    }

    /**
     * Create PendingIntent for event alarm.
     *
     * @param eventId The event ID
     * @param dayId The event's day ID for navigation
     * @param minutesBefore Minutes before event for notification
     * @param additionalFlags Additional PendingIntent flags
     * @return PendingIntent for the alarm
     */
    private fun createPendingIntent(
        eventId: String,
        dayId: String,
        minutesBefore: Int,
        additionalFlags: Int = 0
    ): PendingIntent? {
        val intent = Intent(context, EventAlarmReceiver::class.java).apply {
            action = "com.boox.day.EVENT_REMINDER"
            putExtra(EXTRA_EVENT_ID, eventId)
            putExtra(EXTRA_EVENT_DAY_ID, dayId)
            putExtra(EXTRA_MINUTES_BEFORE, minutesBefore)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE or
                additionalFlags

        // Use event ID hash as request code for uniqueness
        val requestCode = REQUEST_CODE_PREFIX + eventId.hashCode()

        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    /**
     * Fallback: Schedule notification using WorkManager.
     * Used when exact alarm permission is not granted or on Boox devices.
     * Less precise but still works.
     */
    private fun scheduleWithWorkManager(
        eventId: String,
        dayId: String,
        minutesBefore: Int,
        triggerMillis: Long
    ) {
        val delayMillis = triggerMillis - System.currentTimeMillis()
        if (delayMillis <= 0) {
            Log.d(TAG, "WorkManager fallback: time already passed for event $eventId")
            return
        }

        val workRequest = androidx.work.OneTimeWorkRequestBuilder<EventNotificationWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .addTag("$WORK_TAG_EVENT_FALLBACK:$eventId")
            .setInputData(
                androidx.work.Data.Builder()
                    .putString(EXTRA_EVENT_ID, eventId)
                    .putString(EXTRA_EVENT_DAY_ID, dayId)
                    .putInt(EXTRA_MINUTES_BEFORE, minutesBefore)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
        Log.d(TAG, "Scheduled WorkManager fallback for event $eventId in ${delayMillis}ms")
    }
}

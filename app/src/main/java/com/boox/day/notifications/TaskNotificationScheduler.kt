package com.boox.day.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import com.boox.day.data.db.Task
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Schedules notifications for task due dates using AlarmManager.
 *
 * Architecture:
 * - Uses setExactAndAllowWhileIdle() for precise notifications (Doze-aware)
 * - Falls back to WorkManager if exact alarm permission not granted (Android 12+)
 * - Each task with a due date gets its own alarm keyed by taskId
 *
 * Alarm lifecycle:
 * - Schedule: When task due date is set (with valid reminder time)
 * - Cancel: When task is completed, deleted, or due date is cleared
 * - Reschedule: When due date or reminder time changes
 *
 * @see DueDateAlarmReceiver for handling fired alarms
 * @see BootReceiver for rescheduling after device restart
 */
class TaskNotificationScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    companion object {
        private const val TAG = "TaskNotifScheduler"

        // Intent extras for alarm receiver
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TASK_DAY_DATE = "task_day_date"

        // Request code prefix to ensure unique PendingIntents per task
        private const val REQUEST_CODE_PREFIX = 10000

        // WorkManager tags
        const val WORK_NAME_DAILY_SCAN = "due_date_daily_scan"
        const val WORK_TAG_TASK_FALLBACK = "task_notification_fallback"

        /**
         * Check if running on a Boox/Onyx device.
         * These devices have firmware-level AlarmManager restrictions that silently
         * drop alarms even when all permissions are granted. Use WorkManager instead.
         */
        private fun isBooxDevice(): Boolean = NotificationPermissionHelper.isBooxDevice()
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

        /** Due date/reminder time has already passed */
        object AlreadyPast : ScheduleResult()

        /** Task is invalid (null, completed, or missing due date) */
        object InvalidTask : ScheduleResult()
    }

    /**
     * Schedule notification alarm for a task.
     *
     * Prerequisites:
     * - Task must not be completed
     * - Task must have a dueDate set
     * - Reminder time is taken from settings (hour:minute on due date)
     *
     * @param task The task to schedule notification for
     * @param reminderHour Hour of day to notify (0-23)
     * @param reminderMinute Minute of hour to notify (0-59)
     * @return ScheduleResult indicating success or failure mode
     */
    fun scheduleTaskAlarm(
        task: Task,
        reminderHour: Int,
        reminderMinute: Int
    ): ScheduleResult {
        // Validate task state
        if (task.isCompleted) {
            Log.d(TAG, "Task ${task.id} is completed, not scheduling")
            return ScheduleResult.InvalidTask
        }

        // Due day (v27): a local calendar day, so the reminder fires on that day in any zone
        val dueDateLocal = com.boox.day.data.db.TaskDueDates.toLocalDate(task.dueDay)
        if (dueDateLocal == null) {
            Log.d(TAG, "Task ${task.id} has no due date, not scheduling")
            return ScheduleResult.InvalidTask
        }

        // Check notification permission
        if (!NotificationPermissionHelper.hasNotificationPermission(context)) {
            Log.w(TAG, "No notification permission, cannot schedule task ${task.id}")
            return ScheduleResult.NoNotificationPermission
        }
        if (!NotificationChannelManager.isChannelEnabled(context, NotificationChannelManager.CHANNEL_ID_DUE_TASKS)) {
            Log.w(TAG, "Task notification channel disabled, cannot schedule task ${task.id}")
            return ScheduleResult.ChannelDisabled
        }

        // Calculate trigger time: due date + reminder time (the task's own time when set, v28)
        val reminderTime = task.reminderMinuteOfDay?.let { LocalTime.of(it / 60, it % 60) }
            ?: LocalTime.of(reminderHour, reminderMinute)
        val triggerDateTime = LocalDateTime.of(dueDateLocal, reminderTime)
        val triggerMillis = triggerDateTime
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        // Don't schedule if already past
        if (triggerMillis <= System.currentTimeMillis()) {
            Log.d(TAG, "Task ${task.id} reminder time already passed")
            return ScheduleResult.AlreadyPast
        }

        // Create pending intent for alarm
        val pendingIntent = createPendingIntent(task.id, task.dayDate)
        if (pendingIntent == null) {
            Log.e(TAG, "Failed to create PendingIntent for task ${task.id}")
            return ScheduleResult.InvalidTask
        }

        // Check exact alarm permission (Android 12+)
        if (!NotificationPermissionHelper.canScheduleExactAlarms(context)) {
            Log.w(TAG, "No exact alarm permission, using WorkManager fallback for task ${task.id}")
            scheduleWithWorkManager(task.id, triggerMillis)
            return ScheduleResult.WorkManagerFallback
        }

        // Boox/Onyx devices silently drop AlarmManager alarms at firmware level
        // even when all permissions are granted. Use WorkManager instead.
        // Also check SharedPreferences override flag for hard failsafe.
        if (shouldForceWorkManager()) {
            Log.d(TAG, "Using WorkManager for task ${task.id} (forceWorkManager=true, isBoox=${isBooxDevice()})")
            scheduleWithWorkManager(task.id, triggerMillis)
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
            Log.d(TAG, "Scheduled exact alarm for task ${task.id} at ${triggerDateTime}")
            return ScheduleResult.ExactAlarm
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling alarm for task ${task.id}: ${e.message}")
            scheduleWithWorkManager(task.id, triggerMillis)
            return ScheduleResult.WorkManagerFallback
        }
    }

    /**
     * Cancel alarm for a specific task.
     * Safe to call even if no alarm was scheduled.
     *
     * @param taskId The task ID to cancel alarm for
     */
    fun cancelTaskAlarm(taskId: String) {
        val pendingIntent = createPendingIntent(taskId, "", PendingIntent.FLAG_NO_CREATE)
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled alarm for task $taskId")
        }

        // Also cancel any WorkManager fallback
        WorkManager.getInstance(context).cancelAllWorkByTag("$WORK_TAG_TASK_FALLBACK:$taskId")
    }

    /**
     * Schedule daily scan worker.
     * This runs every day at 6 AM to check for overdue tasks and send reminders.
     */
    fun scheduleDailyScan() {
        // Calculate delay to next 6 AM
        val now = LocalDateTime.now()
        val targetTime = LocalTime.of(6, 0)
        val todayTarget = now.toLocalDate().atTime(targetTime)
        val nextTarget = if (now.isAfter(todayTarget)) {
            todayTarget.plusDays(1)
        } else {
            todayTarget
        }
        val initialDelayMs = Duration.between(now, nextTarget).toMillis()

        val workRequest = PeriodicWorkRequestBuilder<DueDateNotificationWorker>(
            24, TimeUnit.HOURS
        )
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(false)  // Run even on low battery
                    .build()
            )
            .addTag(WORK_NAME_DAILY_SCAN)
            .build()

        // Use UPDATE to re-anchor existing work to 6 AM (handles timezone changes, upgrades)
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME_DAILY_SCAN,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
        Log.d(TAG, "Scheduled daily due date scan worker (next run at $nextTarget)")
    }

    /**
     * Cancel daily scan worker.
     */
    fun cancelDailyScan() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_DAILY_SCAN)
        Log.d(TAG, "Cancelled daily due date scan worker")
    }

    /**
     * Create PendingIntent for task alarm.
     *
     * @param taskId The task ID
     * @param dayDate The task's original day date for navigation
     * @param additionalFlags Additional PendingIntent flags
     * @return PendingIntent for the alarm
     */
    private fun createPendingIntent(
        taskId: String,
        dayDate: String,
        additionalFlags: Int = 0
    ): PendingIntent? {
        val intent = Intent(context, DueDateAlarmReceiver::class.java).apply {
            action = "com.boox.day.TASK_DUE_REMINDER"
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_DAY_DATE, dayDate)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE or
                additionalFlags

        // Use task ID hash as request code for uniqueness
        val requestCode = REQUEST_CODE_PREFIX + taskId.hashCode()

        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    /**
     * Fallback: Schedule notification using WorkManager.
     * Used when exact alarm permission is not granted.
     * Less precise but still works.
     */
    private fun scheduleWithWorkManager(taskId: String, triggerMillis: Long) {
        val delayMillis = triggerMillis - System.currentTimeMillis()
        if (delayMillis <= 0) {
            Log.d(TAG, "WorkManager fallback: time already passed for task $taskId")
            return
        }

        val workRequest = androidx.work.OneTimeWorkRequestBuilder<DueDateNotificationWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .addTag("$WORK_TAG_TASK_FALLBACK:$taskId")
            .setInputData(
                androidx.work.Data.Builder()
                    .putString(EXTRA_TASK_ID, taskId)
                    .build()
            )
            .build()

        // Use enqueueUniqueWork with REPLACE to prevent duplicates when due date changes
        WorkManager.getInstance(context).enqueueUniqueWork(
            "$WORK_TAG_TASK_FALLBACK:$taskId",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
        Log.d(TAG, "Scheduled WorkManager fallback for task $taskId in ${delayMillis}ms")
    }
}

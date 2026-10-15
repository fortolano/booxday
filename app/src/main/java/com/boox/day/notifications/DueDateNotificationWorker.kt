package com.boox.day.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.boox.day.APP_SETTINGS_KEY
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.KvProxy
import com.boox.day.data.db.Task
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * WorkManager worker for daily due date scanning.
 *
 * This worker runs periodically (typically at 6 AM) to:
 * 1. Find all incomplete tasks due today
 * 2. Find all tasks that became overdue yesterday (one-day nudge)
 * 3. Send grouped notifications for each category
 *
 * Also used as fallback when exact alarms are not available (Android 12+
 * without SCHEDULE_EXACT_ALARM permission).
 */
class DueDateNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "DueDateNotifWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting due date notification scan")

        // CRITICAL: Load settings from persistent storage, not in-memory cache
        // GlobalAppSettings.current is uninitialized when worker starts in cold process
        val settings = try {
            KvProxy(applicationContext).get(APP_SETTINGS_KEY, AppSettings.serializer())
                ?: AppSettings(version = 1)  // Use defaults if not found
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load settings from storage: ${e.message ?: e.javaClass.simpleName}, using defaults", e)
            AppSettings(version = 1)
        }

        if (!settings.taskNotificationsEnabled) {
            Log.d(TAG, "Task notifications disabled in settings (loaded from storage), skipping scan")
            return Result.success()
        }

        // Check notification permission
        if (!NotificationPermissionHelper.hasNotificationPermission(applicationContext)) {
            Log.w(TAG, "No notification permission, skipping scan")
            return Result.success()
        }

        try {
            // Check if this is a fallback single-task notification
            val singleTaskId = inputData.getString(TaskNotificationScheduler.EXTRA_TASK_ID)
            if (singleTaskId != null) {
                handleSingleTaskFallback(singleTaskId)
            } else {
                // Regular daily scan
                performDailyScan()
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during due date scan: ${e.message}")
            return Result.retry()
        }
    }

    /**
     * Handle single task notification (WorkManager fallback mode).
     * Called when exact alarm permission is not available.
     */
    private suspend fun handleSingleTaskFallback(taskId: String) {
        val taskDao = AppDatabase.getDatabase(applicationContext).taskDao()
        val task = taskDao.getById(taskId)

        if (task == null) {
            Log.d(TAG, "Fallback: Task $taskId no longer exists")
            return
        }
        if (task.isCompleted) {
            Log.d(TAG, "Fallback: Task $taskId is completed")
            return
        }
        if (task.dueDay == null) {
            Log.d(TAG, "Fallback: Task $taskId has no due date")
            return
        }

        NotificationHelper(applicationContext).showTaskReminderNotification(task)
    }

    /**
     * Perform regular daily scan for due tasks.
     */
    private suspend fun performDailyScan() {
        val taskDao = AppDatabase.getDatabase(applicationContext).taskDao()
        val notificationHelper = NotificationHelper(applicationContext)

        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val todayString = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val yesterdayString = yesterday.format(DateTimeFormatter.ISO_LOCAL_DATE)

        // Get tasks due today (incomplete only)
        val dueTodayTasks = getIncompleteTasdueOnDate(taskDao, todayString)
        Log.d(TAG, "Found ${dueTodayTasks.size} tasks due today")

        if (dueTodayTasks.isNotEmpty()) {
            notificationHelper.showDueTodayNotification(dueTodayTasks)
        }

        // Get tasks that became overdue yesterday (one-day nudge)
        // Only show if they weren't notified yesterday (check via shared prefs)
        val overdueYesterday = getIncompleteTasdueOnDate(taskDao, yesterdayString)
        val newlyOverdue = filterNewlyOverdue(overdueYesterday, yesterdayString)

        Log.d(TAG, "Found ${newlyOverdue.size} newly overdue tasks")
        if (newlyOverdue.isNotEmpty()) {
            notificationHelper.showOverdueNudgeNotification(newlyOverdue)
            markAsNotified(newlyOverdue, yesterdayString)
        }
    }

    /**
     * Get incomplete tasks due on a specific date.
     */
    private fun getIncompleteTasdueOnDate(
        taskDao: com.boox.day.data.db.TaskDao,
        dateString: String
    ): List<Task> {
        // Due days are stored as "yyyy-MM-dd" (v27)
        return taskDao.getTasksDueBetween(dateString, dateString)
    }

    /**
     * Filter out tasks that were already notified as overdue.
     * Uses SharedPreferences to track notified tasks.
     */
    private fun filterNewlyOverdue(tasks: List<Task>, dateString: String): List<Task> {
        val prefs = applicationContext.getSharedPreferences(
            "notification_tracking",
            Context.MODE_PRIVATE
        )
        val notifiedKey = "overdue_notified_$dateString"
        val notifiedIds = prefs.getStringSet(notifiedKey, emptySet()) ?: emptySet()

        return tasks.filter { it.id !in notifiedIds }
    }

    /**
     * Mark tasks as notified for overdue tracking.
     */
    private fun markAsNotified(tasks: List<Task>, dateString: String) {
        val prefs = applicationContext.getSharedPreferences(
            "notification_tracking",
            Context.MODE_PRIVATE
        )
        val notifiedKey = "overdue_notified_$dateString"
        val existingIds = prefs.getStringSet(notifiedKey, emptySet())?.toMutableSet() ?: mutableSetOf()
        existingIds.addAll(tasks.map { it.id })

        prefs.edit().putStringSet(notifiedKey, existingIds).apply()
    }
}

package com.boox.day.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.core.app.NotificationCompat
import com.boox.day.MainActivity
import com.boox.day.R
import com.boox.day.data.db.Task

/**
 * Helper for building and displaying notifications.
 *
 * Notification types:
 * - Due Today: Shows tasks due on the current day (grouped if multiple)
 * - Overdue Nudge: One-day reminder for newly overdue tasks
 * - Event Reminder: Upcoming calendar event reminder
 *
 * All notifications respect:
 * - E-ink display (no animations)
 * - Deep links to specific dates in app
 * - Grouping for multiple items
 */
class NotificationHelper(private val appContext: Context) {
    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)

    companion object {
        private const val TAG = "NotificationHelper"

        // Notification IDs
        const val NOTIFICATION_ID_DUE_TODAY = 1001
        const val NOTIFICATION_ID_OVERDUE = 1002
        const val NOTIFICATION_ID_EVENT_BASE = 2000  // Events use 2000 + eventId.hashCode()

        // Deep link extras
        const val EXTRA_NAVIGATE_TO_DATE = "navigate_to_date"
        const val EXTRA_OPEN_TAB = "open_tab"
        const val TAB_TASKS = "tasks"
        /** Task to open and highlight (plan T3, Oct 2026). */
        const val EXTRA_TASK_ID = "focus_task_id"
    }

    /**
     * Show notification for tasks due today.
     * Groups multiple tasks into a single notification with InboxStyle.
     *
     * @param tasks List of tasks due today (must be non-empty)
     */
    fun showDueTodayNotification(tasks: List<Task>) {
        if (tasks.isEmpty()) return
        if (!NotificationChannelManager.isChannelEnabled(appContext, NotificationChannelManager.CHANNEL_ID_DUE_TASKS)) {
            Log.w(TAG, "Task notification channel disabled, skipping due today notification")
            return
        }

        val notification = if (tasks.size == 1) {
            // Single task - show details
            buildSingleTaskNotification(tasks[0], "Task due today")
        } else {
            // Multiple tasks - grouped summary
            buildGroupedTaskNotification(tasks, "tasks due today")
        }

        notificationManager.notify(NOTIFICATION_ID_DUE_TODAY, notification)
        Log.d(TAG, "Showed due today notification for ${tasks.size} tasks")
    }

    /**
     * Show notification for overdue tasks (one-day nudge).
     * Only shown once when task becomes overdue.
     *
     * @param tasks List of newly overdue tasks (must be non-empty)
     */
    fun showOverdueNudgeNotification(tasks: List<Task>) {
        if (tasks.isEmpty()) return
        if (!NotificationChannelManager.isChannelEnabled(appContext, NotificationChannelManager.CHANNEL_ID_DUE_TASKS)) {
            Log.w(TAG, "Task notification channel disabled, skipping overdue notification")
            return
        }

        val notification = if (tasks.size == 1) {
            buildSingleTaskNotification(tasks[0], "Task overdue")
        } else {
            buildGroupedTaskNotification(tasks, "overdue tasks")
        }

        notificationManager.notify(NOTIFICATION_ID_OVERDUE, notification)
        Log.d(TAG, "Showed overdue notification for ${tasks.size} tasks")
    }

    /**
     * Show reminder notification for a single task.
     *
     * @param task The task to show reminder for
     */
    fun showTaskReminderNotification(task: Task) {
        if (!NotificationChannelManager.isChannelEnabled(appContext, NotificationChannelManager.CHANNEL_ID_DUE_TASKS)) {
            Log.w(TAG, "Task notification channel disabled, skipping reminder for task ${task.id}")
            return
        }
        val notification = buildSingleTaskNotification(task, "Task reminder")
        // Use unique ID per task so multiple reminders can coexist
        val notificationId = NOTIFICATION_ID_DUE_TODAY + task.id.hashCode()
        notificationManager.notify(notificationId, notification)
        Log.d(TAG, "Showed reminder notification for task ${task.id}")
    }

    /**
     * Cancel a specific task notification.
     *
     * @param taskId The task ID to cancel notification for
     */
    fun cancelTaskNotification(taskId: String) {
        val notificationId = NOTIFICATION_ID_DUE_TODAY + taskId.hashCode()
        notificationManager.cancel(notificationId)
    }

    /**
     * Cancel all task due date notifications.
     */
    fun cancelAllTaskNotifications() {
        notificationManager.cancel(NOTIFICATION_ID_DUE_TODAY)
        notificationManager.cancel(NOTIFICATION_ID_OVERDUE)
    }

    /**
     * Build notification for a single task.
     */
    private fun buildSingleTaskNotification(
        task: Task,
        title: String
    ): android.app.Notification {
        val taskText = task.typedText?.take(100)
            ?: task.convertedText?.take(100)
            ?: task.recognizedText?.take(100)
            ?: "Task"

        return NotificationCompat.Builder(appContext, NotificationChannelManager.CHANNEL_ID_DUE_TASKS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(taskText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(createTaskTapIntent(task.dayDate, task.id))
            .build()
    }

    /**
     * Build grouped notification for multiple tasks.
     */
    private fun buildGroupedTaskNotification(
        tasks: List<Task>,
        titleSuffix: String
    ): android.app.Notification {
        val inboxStyle = NotificationCompat.InboxStyle()

        // Add up to 5 task lines
        tasks.take(5).forEach { task ->
            val taskText = task.typedText?.take(50)
                ?: task.convertedText?.take(50)
                ?: task.recognizedText?.take(50)
                ?: "Task"
            inboxStyle.addLine(taskText)
        }

        // Add summary if more than 5
        if (tasks.size > 5) {
            inboxStyle.setSummaryText("+${tasks.size - 5} more")
        }

        // Use first task's date for navigation (most relevant)
        val navigateDate = tasks.firstOrNull()?.dayDate ?: ""

        return NotificationCompat.Builder(appContext, NotificationChannelManager.CHANNEL_ID_DUE_TASKS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${tasks.size} $titleSuffix")
            .setContentText(
                tasks.take(3).joinToString(", ") { task ->
                    task.typedText?.take(30)
                        ?: task.convertedText?.take(30)
                        ?: task.recognizedText?.take(30)
                        ?: "Task"
                }
            )
            .setStyle(inboxStyle)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(createTaskTapIntent(navigateDate))
            .build()
    }

    /**
     * Create PendingIntent for notification tap.
     * Opens app to the task's original date in Tasks tab.
     */
    @OptIn(ExperimentalAnimationApi::class, ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
    private fun createTaskTapIntent(dayDate: String, taskId: String? = null): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        intent.putExtra(EXTRA_NAVIGATE_TO_DATE, dayDate)
        intent.putExtra(EXTRA_OPEN_TAB, TAB_TASKS)
        taskId?.let { intent.putExtra(EXTRA_TASK_ID, it) }

        return PendingIntent.getActivity(
            appContext,
            // One PendingIntent per task (FLAG_UPDATE_CURRENT would let two reminders on the
            // same day share — and overwrite — each other's extras)
            (taskId ?: dayDate).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Event Notifications (Phase 5)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Show reminder notification for a calendar event.
     *
     * @param eventId The event ID
     * @param eventTitle The event title
     * @param eventDayId The event's day ID (yyyy-MM-dd)
     * @param minutesBefore Minutes until event starts
     */
    fun showEventReminderNotification(
        eventId: String,
        eventTitle: String,
        eventDayId: String,
        minutesBefore: Int
    ) {
        if (!NotificationChannelManager.isChannelEnabled(appContext, NotificationChannelManager.CHANNEL_ID_EVENT_REMINDERS)) {
            Log.w(TAG, "Event notification channel disabled, skipping reminder for event $eventId")
            return
        }
        val timeText = when {
            minutesBefore <= 0 -> "Starting now"
            minutesBefore == 1 -> "In 1 minute"
            minutesBefore < 60 -> "In $minutesBefore minutes"
            minutesBefore == 60 -> "In 1 hour"
            minutesBefore == 1440 -> "Tomorrow"
            else -> "In ${minutesBefore / 60} hours"
        }

        val notification = NotificationCompat.Builder(
            appContext,
            NotificationChannelManager.CHANNEL_ID_EVENT_REMINDERS
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(eventTitle)
            .setContentText(timeText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(createEventTapIntent(eventDayId))
            .build()

        val notificationId = NOTIFICATION_ID_EVENT_BASE + eventId.hashCode()
        notificationManager.notify(notificationId, notification)
        Log.d(TAG, "Showed event reminder: $eventTitle ($timeText)")
    }

    /**
     * Cancel event reminder notification.
     */
    fun cancelEventNotification(eventId: String) {
        val notificationId = NOTIFICATION_ID_EVENT_BASE + eventId.hashCode()
        notificationManager.cancel(notificationId)
    }

    /**
     * Create PendingIntent for event notification tap.
     * Opens app to the event's date.
     */
    @OptIn(ExperimentalAnimationApi::class, ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
    private fun createEventTapIntent(dayId: String): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        intent.putExtra(EXTRA_NAVIGATE_TO_DATE, dayId)

        return PendingIntent.getActivity(
            appContext,
            dayId.hashCode() + 1000,  // Offset to avoid collision with task intents
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

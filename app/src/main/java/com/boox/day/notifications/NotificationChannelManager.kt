package com.boox.day.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Manages notification channels for the Boox Day app.
 *
 * Channels (Android O+):
 * - CHANNEL_ID_DUE_TASKS: Task due date reminders
 * - CHANNEL_ID_EVENT_REMINDERS: Calendar event reminders
 * - CHANNEL_ID_APP_UPDATES: "Boox Day updated" after an in-app update
 *
 * Call createChannels() in Application.onCreate() to ensure channels exist.
 */
object NotificationChannelManager {
    private const val TAG = "NotificationChannelMgr"

    // Task due date notifications
    const val CHANNEL_ID_DUE_TASKS = "due_tasks"
    private const val CHANNEL_NAME_DUE_TASKS = "Task Due Dates"
    private const val CHANNEL_DESC_DUE_TASKS = "Reminders for tasks due today"

    // Event reminder notifications
    const val CHANNEL_ID_EVENT_REMINDERS = "event_reminders"
    private const val CHANNEL_NAME_EVENT_REMINDERS = "Event Reminders"
    private const val CHANNEL_DESC_EVENT_REMINDERS = "Reminders before calendar events"

    // "Boox Day updated, tap to open" after an in-app update (Oct 2026, update/AppUpdatedReceiver)
    const val CHANNEL_ID_APP_UPDATES = "app_updates"
    private const val CHANNEL_NAME_APP_UPDATES = "App Updates"
    private const val CHANNEL_DESC_APP_UPDATES = "Tells you when Boox Day has updated itself"

    /**
     * Create all notification channels.
     * Safe to call multiple times - Android ignores duplicate channel creation.
     */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)

            // Task due date channel
            val dueTasksChannel = NotificationChannel(
                CHANNEL_ID_DUE_TASKS,
                CHANNEL_NAME_DUE_TASKS,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESC_DUE_TASKS
                enableVibration(true)
                setShowBadge(true)
            }

            // Event reminder channel
            val eventReminderChannel = NotificationChannel(
                CHANNEL_ID_EVENT_REMINDERS,
                CHANNEL_NAME_EVENT_REMINDERS,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC_EVENT_REMINDERS
                enableVibration(true)
                setShowBadge(true)
            }

            val appUpdatesChannel = NotificationChannel(
                CHANNEL_ID_APP_UPDATES,
                CHANNEL_NAME_APP_UPDATES,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESC_APP_UPDATES
                setShowBadge(false)
            }

            notificationManager.createNotificationChannel(dueTasksChannel)
            notificationManager.createNotificationChannel(eventReminderChannel)
            notificationManager.createNotificationChannel(appUpdatesChannel)

            Log.d(TAG, "Notification channels created")
        }
    }

    /**
     * Check if a specific channel is enabled by the user.
     * Returns true if channel exists and is enabled, or if Android < O.
     */
    fun isChannelEnabled(context: Context, channelId: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            val channel = notificationManager.getNotificationChannel(channelId)
            return channel?.importance != NotificationManager.IMPORTANCE_NONE
        }
        return true
    }

    /**
     * Check if notifications are globally enabled for the app.
     */
    fun areNotificationsEnabled(context: Context): Boolean {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        return notificationManager.areNotificationsEnabled()
    }
}

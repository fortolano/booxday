package com.boox.day.notifications

import android.content.Context
import android.util.Log
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.LocalEventRepository
import com.boox.day.data.db.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Notifications were just allowed: schedule the reminders that couldn't be (Oct 2026; this lived in
 * MainScreen's permission launcher). MainActivity.onResume does the same after a grant in Settings.
 */
object ReminderScheduling {
    private const val TAG = "ReminderScheduling"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun scheduleAfterGrant(context: Context) {
        val app = context.applicationContext
        scope.launch {
            val settings = GlobalAppSettings.current
            if (settings.taskNotificationsEnabled) {
                runCatching { TaskRepository(app).scheduleNotificationsForExistingTasks() }
                    .onFailure { Log.e(TAG, "Task reminders not scheduled: ${it.javaClass.simpleName}") }
            }
            if (settings.eventNotificationsEnabled) {
                runCatching { LocalEventRepository(app).scheduleNotificationsForUpcomingEvents() }
                    .onFailure { Log.e(TAG, "Event reminders not scheduled: ${it.javaClass.simpleName}") }
            }
        }
    }
}

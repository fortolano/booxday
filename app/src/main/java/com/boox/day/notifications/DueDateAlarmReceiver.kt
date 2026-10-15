package com.boox.day.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.boox.day.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver for task due date alarm triggers.
 *
 * When AlarmManager fires at the scheduled reminder time, this receiver:
 * 1. Validates the task still exists
 * 2. Validates the task is still incomplete
 * 3. Validates the task still has a due date
 * 4. Shows the notification only if all validations pass
 *
 * Critical: This validation prevents stale notifications for tasks that were
 * completed, deleted, or had their due date removed after the alarm was scheduled.
 */
class DueDateAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DueDateAlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(TaskNotificationScheduler.EXTRA_TASK_ID)
        if (taskId == null) {
            Log.w(TAG, "Received alarm without task ID")
            return
        }

        Log.d(TAG, "Alarm fired for task: $taskId")

        // Use goAsync() to allow longer processing in receiver
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                validateAndShowNotification(context, taskId)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing alarm for task $taskId: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Validate task state and show notification if appropriate.
     *
     * Validation steps (CRITICAL - prevents stale notifications):
     * 1. Task must exist in database
     * 2. Task must not be completed
     * 3. Task must have a due date set
     */
    private suspend fun validateAndShowNotification(context: Context, taskId: String) {
        val taskDao = AppDatabase.getDatabase(context).taskDao()
        val task = taskDao.getById(taskId)

        when {
            task == null -> {
                Log.d(TAG, "Task $taskId no longer exists, skipping notification")
                Log.i(TAG, "NOTIF_SKIP: task_deleted taskId=$taskId")
                return
            }
            task.isCompleted -> {
                Log.d(TAG, "Task $taskId is completed, skipping notification")
                Log.i(TAG, "NOTIF_SKIP: task_completed taskId=$taskId")
                return
            }
            task.dueDay == null -> {
                Log.d(TAG, "Task $taskId has no due date, skipping notification")
                Log.i(TAG, "NOTIF_SKIP: no_due_date taskId=$taskId")
                return
            }
            else -> {
                // Valid - show notification
                Log.d(TAG, "Showing notification for valid task $taskId")
                Log.i(TAG, "NOTIF_SHOW: taskId=$taskId")
                NotificationHelper(context).showTaskReminderNotification(task)
            }
        }
    }
}

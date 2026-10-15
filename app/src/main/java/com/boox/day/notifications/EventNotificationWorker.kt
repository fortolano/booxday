package com.boox.day.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.boox.day.APP_SETTINGS_KEY
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.KvProxy
import com.boox.day.data.db.SyncStatus

/**
 * WorkManager worker for event reminder notifications.
 *
 * This worker is used as a fallback when:
 * - Exact alarm permission is not granted (Android 12+)
 * - Running on Boox/Onyx devices (firmware-level AlarmManager restrictions)
 *
 * It's scheduled by EventNotificationScheduler with the event ID and
 * reminder minutes passed via inputData.
 *
 * IMPORTANT: Events can be in either table:
 * - LocalEvent: standalone events created in-app
 * - CalendarEvent: synced events from Google/Outlook calendars
 */
class EventNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "EventNotifWorker"
        // 5-minute grace period - still show notification if event just started
        private const val GRACE_PERIOD_MS = 5 * 60 * 1000L
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting event notification worker")

        // CRITICAL: Load settings from persistent storage, not in-memory cache
        // GlobalAppSettings.current is uninitialized when worker starts in cold process
        val settings = try {
            KvProxy(applicationContext).get(APP_SETTINGS_KEY, AppSettings.serializer())
                ?: AppSettings(version = 1)  // Use defaults if not found
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load settings from storage: ${e.message ?: e.javaClass.simpleName}, using defaults", e)
            AppSettings(version = 1)
        }

        if (!settings.eventNotificationsEnabled) {
            Log.d(TAG, "Event notifications disabled in settings (loaded from storage), skipping")
            return Result.success()
        }

        // Check notification permission
        if (!NotificationPermissionHelper.hasNotificationPermission(applicationContext)) {
            Log.w(TAG, "No notification permission, skipping")
            return Result.success()
        }

        try {
            val eventId = inputData.getString(EventNotificationScheduler.EXTRA_EVENT_ID)
            val minutesBefore = inputData.getInt(EventNotificationScheduler.EXTRA_MINUTES_BEFORE, 15)

            if (eventId == null) {
                Log.e(TAG, "No event ID provided in worker input")
                return Result.failure()
            }

            handleEventNotification(eventId, minutesBefore)
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during event notification: ${e.message}")
            return Result.retry()
        }
    }

    /**
     * Handle event notification.
     * Looks up event in BOTH tables (LocalEvent first, then CalendarEvent).
     * Validates event still exists and sends notification.
     */
    private suspend fun handleEventNotification(eventId: String, minutesBefore: Int) {
        val db = AppDatabase.getDatabase(applicationContext)

        // Try LocalEvent table first
        val localEvent = db.localEventDao().getById(eventId)
        if (localEvent != null) {
            // Found in LocalEvent table - validate and show
            if (localEvent.syncStatus == SyncStatus.PENDING_DELETE) {
                Log.d(TAG, "LocalEvent $eventId is pending deletion, skipping")
                return
            }

            if (localEvent.startTime + GRACE_PERIOD_MS < System.currentTimeMillis()) {
                Log.d(TAG, "LocalEvent $eventId has already passed")
                return
            }

            Log.d(TAG, "Showing notification for LocalEvent $eventId: ${localEvent.title}")
            NotificationHelper(applicationContext).showEventReminderNotification(
                eventId = localEvent.id,
                eventTitle = localEvent.title,
                eventDayId = localEvent.dayId,
                minutesBefore = minutesBefore
            )
            return
        }

        // Not found in LocalEvent - try CalendarEvent table (synced Google/Outlook)
        val calendarEvent = db.calendarEventDao().getById(eventId)
        if (calendarEvent != null) {
            // Found in CalendarEvent table - validate and show
            if (calendarEvent.startTime + GRACE_PERIOD_MS < System.currentTimeMillis()) {
                Log.d(TAG, "CalendarEvent $eventId has already passed")
                return
            }

            Log.d(TAG, "Showing notification for CalendarEvent $eventId: ${calendarEvent.title}")
            NotificationHelper(applicationContext).showEventReminderNotification(
                eventId = calendarEvent.id,
                eventTitle = calendarEvent.title,
                eventDayId = calendarEvent.dayId,
                minutesBefore = minutesBefore
            )
            return
        }

        // Event not found in either table - was deleted
        Log.d(TAG, "Event $eventId not found in either table, skipping notification")
    }
}

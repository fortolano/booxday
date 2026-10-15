package com.boox.day.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.boox.day.APP_SETTINGS_KEY
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.KvProxy
import com.boox.day.data.db.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver for event reminder alarm triggers.
 *
 * When AlarmManager fires at the scheduled reminder time, this receiver:
 * 1. Validates the event still exists (in EITHER LocalEvent OR CalendarEvent table)
 * 2. Validates the event is not pending deletion (LocalEvent only)
 * 3. Validates the event is still in the future (with grace period)
 * 4. Shows the notification only if all validations pass
 *
 * Critical: This validation prevents stale notifications for events that were
 * deleted or moved after the alarm was scheduled.
 *
 * IMPORTANT: Events can be in either table:
 * - LocalEvent: standalone events created in-app
 * - CalendarEvent: synced events from Google/Outlook calendars
 */
class EventAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "EventAlarmReceiver"
        // 5-minute grace period - still show notification if event just started
        private const val GRACE_PERIOD_MS = 5 * 60 * 1000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra(EventNotificationScheduler.EXTRA_EVENT_ID)
        val minutesBefore = intent.getIntExtra(EventNotificationScheduler.EXTRA_MINUTES_BEFORE, 15)

        if (eventId == null) {
            Log.w(TAG, "Received alarm without event ID")
            return
        }

        Log.d(TAG, "Alarm fired for event: $eventId ($minutesBefore min before)")

        // Use goAsync() to allow longer processing in receiver
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                validateAndShowNotification(context, eventId, minutesBefore)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing alarm for event $eventId: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Validate event state and show notification if appropriate.
     *
     * Looks up event in BOTH tables (LocalEvent first, then CalendarEvent).
     *
     * Validation steps (CRITICAL - prevents stale notifications):
     * 1. Event notifications must be enabled in settings
     * 2. Event must exist in either LocalEvent OR CalendarEvent table
     * 3. Event must not be pending deletion (LocalEvent only)
     * 4. Event must still be in the future (with grace period)
     */
    private suspend fun validateAndShowNotification(
        context: Context,
        eventId: String,
        minutesBefore: Int
    ) {
        // CRITICAL: Load settings from persistent storage, not in-memory cache
        // GlobalAppSettings.current is uninitialized when receiver starts in cold process
        val settings = try {
            KvProxy(context).get(APP_SETTINGS_KEY, AppSettings.serializer())
                ?: AppSettings(version = 1)  // Use defaults if not found
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load settings from storage: ${e.message ?: e.javaClass.simpleName}, using defaults", e)
            AppSettings(version = 1)
        }

        if (!settings.eventNotificationsEnabled) {
            Log.d(TAG, "Event notifications disabled in settings (loaded from storage), skipping")
            return
        }

        val db = AppDatabase.getDatabase(context)

        // Try LocalEvent table first
        val localEvent = db.localEventDao().getById(eventId)
        if (localEvent != null) {
            // Found in LocalEvent table - validate and show
            when {
                localEvent.syncStatus == SyncStatus.PENDING_DELETE -> {
                    Log.d(TAG, "LocalEvent $eventId is pending deletion, skipping notification")
                    Log.i(TAG, "NOTIF_SKIP: event_pending_delete eventId=$eventId")
                    return
                }
                localEvent.startTime + GRACE_PERIOD_MS < System.currentTimeMillis() -> {
                    Log.d(TAG, "LocalEvent $eventId has already passed, skipping notification")
                    Log.i(TAG, "NOTIF_SKIP: event_passed eventId=$eventId")
                    return
                }
                else -> {
                    Log.d(TAG, "Showing notification for LocalEvent $eventId: ${localEvent.title}")
                    Log.i(TAG, "NOTIF_SHOW: eventId=$eventId")
                    NotificationHelper(context).showEventReminderNotification(
                        eventId = localEvent.id,
                        eventTitle = localEvent.title,
                        eventDayId = localEvent.dayId,
                        minutesBefore = minutesBefore
                    )
                    return
                }
            }
        }

        // Not found in LocalEvent - try CalendarEvent table (synced Google/Outlook)
        val calendarEvent = db.calendarEventDao().getById(eventId)
        if (calendarEvent != null) {
            // Found in CalendarEvent table - validate and show
            if (calendarEvent.startTime + GRACE_PERIOD_MS < System.currentTimeMillis()) {
                Log.d(TAG, "CalendarEvent $eventId has already passed, skipping notification")
                Log.i(TAG, "NOTIF_SKIP: event_passed eventId=$eventId")
                return
            }

            Log.d(TAG, "Showing notification for CalendarEvent $eventId: ${calendarEvent.title}")
            Log.i(TAG, "NOTIF_SHOW: eventId=$eventId")
            NotificationHelper(context).showEventReminderNotification(
                eventId = calendarEvent.id,
                eventTitle = calendarEvent.title,
                eventDayId = calendarEvent.dayId,
                minutesBefore = minutesBefore
            )
            return
        }

        // Event not found in either table - was deleted
        Log.d(TAG, "Event $eventId not found in either table, skipping notification")
        Log.i(TAG, "NOTIF_SKIP: event_deleted eventId=$eventId")
    }
}

package com.boox.day.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver for timezone changes.
 *
 * Offloads bulk reminder rescheduling to [NotificationRescheduleWorker] so Room queries
 * never run synchronously on the broadcast main thread.
 */
class TimeZoneChangeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TimeZoneChangeReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIMEZONE_CHANGED) {
            return
        }

        Log.i(TAG, "Timezone changed - enqueueing reminder reschedule worker")
        NotificationRescheduleWorker.enqueue(context, NotificationRescheduleWorker.REASON_TIMEZONE)
    }
}

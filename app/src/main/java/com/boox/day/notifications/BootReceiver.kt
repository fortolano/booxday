package com.boox.day.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver for device boot completion.
 *
 * Offloads bulk reminder rescheduling to [NotificationRescheduleWorker] so Room queries
 * never run on the broadcast main thread or risk goAsync() broadcast timeouts.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        Log.i(TAG, "Device booted - enqueueing reminder reschedule worker")
        NotificationRescheduleWorker.enqueue(context, NotificationRescheduleWorker.REASON_BOOT)
    }
}

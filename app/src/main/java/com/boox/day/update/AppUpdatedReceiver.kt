package com.boox.day.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.boox.day.BuildConfig
import com.boox.day.MainActivity
import com.boox.day.R
import com.boox.day.notifications.NotificationChannelManager

/**
 * Boox Day was just replaced by a newer version (MY_PACKAGE_REPLACED, Oct 2026). Android doesn't let
 * an app open itself from the background, so a notification brings the user back in one tap.
 * Nothing shows when notifications are off; the app then simply waits on the home screen.
 */
class AppUpdatedReceiver : BroadcastReceiver() {
    // MainActivity carries these opt-in markers; naming it here needs them too (as in NotificationHelper)
    @OptIn(ExperimentalAnimationApi::class, ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        try {
            NotificationChannelManager.createChannels(context)
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled() ||
                !NotificationChannelManager.isChannelEnabled(context, NotificationChannelManager.CHANNEL_ID_APP_UPDATES)
            ) return
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, NotificationChannelManager.CHANNEL_ID_APP_UPDATES)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Boox Day updated to ${BuildConfig.VERSION_NAME}")
                .setContentText("Tap to open.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification not allowed: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Updated notification failed: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "AppUpdatedReceiver"
        const val NOTIFICATION_ID = 7310
    }
}

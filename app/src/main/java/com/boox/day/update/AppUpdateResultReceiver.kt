package com.boox.day.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log

/**
 * The install session's result (Oct 2026, see [AppUpdateInstaller]). Android asking for the user's
 * confirmation arrives here as STATUS_PENDING_USER_ACTION with the screen to show; Boox Day is in
 * front, so it may open it. On success Android has already stopped the app.
 */
class AppUpdateResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        Log.i(TAG, "Install status $status ${detail.orEmpty()}")
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm == null) {
                    AppUpdateGate.fail("The update couldn't be installed.")
                    return
                }
                try {
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    Log.e(TAG, "Could not show the install confirmation: ${e.message}")
                    AppUpdateGate.fail("The update couldn't be installed.")
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> AppUpdateGate.fail(messageFor(status))
        }
    }

    private fun messageFor(status: Int): String = when (status) {
        PackageInstaller.STATUS_FAILURE_ABORTED -> "The update was cancelled."
        PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough storage to install the update. Free some space, then try again."
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "Installing updates is blocked on this tablet."
        PackageInstaller.STATUS_FAILURE_CONFLICT,
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
        PackageInstaller.STATUS_FAILURE_INVALID -> "This update can't be installed over your current version."
        else -> "The update couldn't be installed."
    }

    private companion object {
        const val TAG = "AppUpdateResult"
    }
}

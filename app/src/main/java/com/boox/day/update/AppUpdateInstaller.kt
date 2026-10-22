package com.boox.day.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Installs a checked update APK through a PackageInstaller session (Oct 2026). Android asks the
 * user once to allow Boox Day to install apps ("Install unknown apps"), and shows its own "Update
 * this app?" confirmation at least the first time; once Boox Day has installed itself, Android 12+
 * can update it without that confirmation. The result arrives in [AppUpdateResultReceiver]; on
 * success Android stops the app and replaces it.
 */
object AppUpdateInstaller {
    private const val TAG = "AppUpdateInstaller"
    const val ACTION_INSTALL_STATUS = "com.boox.day.update.INSTALL_STATUS"

    /** True when the user allowed Boox Day to install apps. */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Opens the "Install unknown apps" switch for Boox Day. */
    fun openInstallPermission(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Some firmware has no per-app page: open the general list instead
            Log.w(TAG, "No per-app install setting: ${e.message}")
            runCatching {
                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    /** Hands [apk] to Android. Returns false when the session could not be started (message logged). */
    suspend fun install(context: Context, apk: File): Boolean = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
            }
        }
        var sessionId = -1
        try {
            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("booxday.apk", 0, apk.length()).use { output ->
                        input.copyTo(output, 64 * 1024)
                        session.fsync(output)
                    }
                }
                val intent = Intent(context, AppUpdateResultReceiver::class.java).setAction(ACTION_INSTALL_STATUS)
                // Mutable: Android adds the status to the intent. Explicit, so that is allowed.
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(pending.intentSender)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Install session failed: ${e.message}", e)
            if (sessionId >= 0) runCatching { installer.abandonSession(sessionId) }
            false
        }
    }
}

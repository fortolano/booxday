package com.boox.day.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat

/**
 * Helper for checking and requesting notification-related permissions.
 *
 * Permission requirements by Android version:
 * - Android 13+ (API 33): POST_NOTIFICATIONS - runtime permission with system dialog
 * - Android 12+ (API 31): SCHEDULE_EXACT_ALARM - requires Settings intent (cannot request at runtime)
 * - Android 11 and below: No special permissions needed
 *
 * Usage pattern:
 * 1. Check hasNotificationPermission() before scheduling notifications
 * 2. Check canScheduleExactAlarms() before using AlarmManager exact methods
 * 3. Call requestNotificationPermission() to show system dialog (Android 13+)
 * 4. Call openExactAlarmSettings() to let user enable exact alarms (Android 12+)
 */
object NotificationPermissionHelper {

    /**
     * Check if app can show notifications.
     *
     * - Android 13+: Check POST_NOTIFICATIONS permission
     * - Android 12 and below: Check if notifications are enabled in system settings
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Check if notifications are enabled in system settings
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.areNotificationsEnabled()
        }
    }

    /**
     * Check if app can schedule exact alarms.
     *
     * - Android 12+: Check SCHEDULE_EXACT_ALARM permission
     * - Android 11 and below: Always true (exact alarms don't require special permission)
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /**
     * Check if all notification permissions are granted.
     * Both notification permission and exact alarm permission must be granted.
     */
    fun hasAllPermissions(context: Context): Boolean {
        return hasNotificationPermission(context) && canScheduleExactAlarms(context)
    }

    /**
     * Request POST_NOTIFICATIONS permission (Android 13+).
     * Call this with the activity's permission launcher.
     *
     * @param launcher ActivityResultLauncher from rememberLauncherForActivityResult
     */
    fun requestNotificationPermission(launcher: ActivityResultLauncher<String>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Open notification settings for the app.
     * Used when permission was denied and user wants to change it.
     */
    fun openNotificationSettings(context: Context) {
        val intent = Intent().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            } else {
                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                data = Uri.parse("package:${context.packageName}")
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Open exact alarm settings (Android 12+).
     * Required because SCHEDULE_EXACT_ALARM cannot be requested at runtime.
     * Users must manually enable this permission in Settings.
     */
    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                // Fallback to app settings if exact alarm settings not available
                openAppSettings(context)
            }
        }
    }

    /**
     * Open app settings page.
     * Fallback when more specific settings intents are not available.
     */
    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Check if we should show rationale for notification permission.
     * Used to determine if we should explain why we need the permission.
     *
     * @param activity The activity to check rationale for
     * @return true if we should show rationale
     */
    fun shouldShowNotificationRationale(context: Context): Boolean {
        // For Android 13+, we can check if the permission was previously denied
        // This is typically done via Activity.shouldShowRequestPermissionRationale()
        // but since we're in an object, we return true to always show explanation
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !hasNotificationPermission(context)
    }

    /**
     * Check if the app is exempt from battery optimization.
     *
     * Critical for Boox devices where background jobs (WorkManager) are killed
     * aggressively unless the app is whitelisted from battery optimization.
     *
     * @return true if exempt (or on older Android where not needed)
     */
    fun isBatteryOptimizationExempt(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(PowerManager::class.java)
            return pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        }
        return true  // Not needed on older Android versions
    }

    /**
     * Open battery optimization settings.
     *
     * On Boox devices, exempting the app from battery optimization is required
     * for reliable notification delivery via WorkManager.
     */
    fun openBatteryOptimizationSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                // Fallback to app settings if battery optimization settings not available
                openAppSettings(context)
            }
        }
    }

    /**
     * Request battery optimization exemption directly.
     * Uses ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS to show a system dialog.
     */
    fun requestBatteryExemption(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false
        }
        if (isBatteryOptimizationExempt(context)) {
            return false
        }
        return try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w("NotificationPermHelper", "Failed to request battery exemption: ${e.message}")
            openBatteryOptimizationSettings(context)
            true
        }
    }

    /**
     * Check if we should prompt for battery exemption.
     * Only on Boox devices that need WorkManager for notifications.
     */
    fun shouldPromptBatteryExemption(context: Context): Boolean {
        return isBooxDevice() && !isBatteryOptimizationExempt(context)
    }

    private fun isBooxDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER?.lowercase() ?: ""
        val brand = Build.BRAND?.lowercase() ?: ""
        val device = Build.DEVICE?.lowercase() ?: ""
        val model = Build.MODEL?.lowercase() ?: ""
        val product = Build.PRODUCT?.lowercase() ?: ""
        return manufacturer.contains("onyx") || brand.contains("onyx") ||
               device.contains("onyx") || model.contains("onyx") || product.contains("onyx") ||
               manufacturer.contains("boox") || brand.contains("boox") ||
               device.contains("boox") || model.contains("boox") || product.contains("boox")
    }
}

package com.boox.day.ui.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Reading the tablet's files for Insert Image and Link File (Oct 2026: one check and one way to ask,
 * instead of copies in the toolbar and two popups). Android 11 and later need "All files access";
 * Android 10 needs the storage permission, asked in the app. Boox Notes has its own rule
 * (booxnotes/BooxNotesAccess: Android 10 there also needs write access).
 */
object FileAccess {

    fun canBrowse(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

    /**
     * Android's All files access page for BooxDay, then the general one, then App info: the first that
     * opens. [launch] starts it (an activity-result launcher, so the caller hears when the user is back).
     */
    fun openAllFilesSettings(context: Context, launch: (Intent) -> Unit) {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")))
                add(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
            add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }
        for (intent in candidates) {
            if (runCatching { launch(intent) }.isSuccess) return
        }
    }
}

package com.boox.day.booxnotes

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat

/**
 * Can BooxDay read the Boox Notes folder? (Oct 2026: one check instead of copies.) Android 11 and
 * later need "All files access". Android 10 (requestLegacyExternalStorage is set) needs the storage
 * permission: the copies said yes there, while the scan required it and nothing asked for it, so
 * Boox Notes never scanned on Android 10 tablets.
 */
object BooxNotesAccess {
    /** The runtime permissions Android 10 asks for. */
    val LEGACY_PERMISSIONS = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    val usesRuntimePermission: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.R

    fun has(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            LEGACY_PERMISSIONS.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        }
}

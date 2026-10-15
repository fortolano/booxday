package com.boox.day.startup

import android.content.Context
import android.util.Log
import androidx.work.WorkManager

/**
 * Open-source cleanup (Oct 2026): removes what features taken out of the app left on a tablet that
 * ran an older build. Each step does nothing when its leftovers are already gone, so it runs on
 * every start.
 */
object RemovedFeaturesCleanup {
    private const val TAG = "RemovedFeaturesCleanup"

    /** NephAI: its background sync and its encrypted pairing and data. */
    private const val NEPHAI_SYNC_WORK = "nephai-sync"
    private val NEPHAI_PREFS = listOf("nephai_device_encrypted", "nephai_data_encrypted")

    /** The old paid version: subscription status, server feature switches and server settings. */
    private val COMMERCIAL_PREFS = listOf(
        "entitlement_prefs", "entitlement_encrypted", "feature_config_prefs", "remote_config_prefs",
        "device_info_prefs"
    )

    fun run(context: Context) {
        val app = context.applicationContext
        WorkManager.getInstance(app).cancelUniqueWork(NEPHAI_SYNC_WORK)
        deletePrefs(app, NEPHAI_PREFS + COMMERCIAL_PREFS)
    }

    private fun deletePrefs(context: Context, names: List<String>) {
        for (name in names) {
            if (context.deleteSharedPreferences(name)) Log.i(TAG, "Removed $name")
        }
    }
}

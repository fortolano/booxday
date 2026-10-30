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

    /**
     * AI Preferences (removed 29 Oct 2026, only three options kept): the other stored answers and the
     * questions' invite schedule. Once per install, off the main thread (it reads the database).
     */
    private const val DONE_PREFS = "removed_features"
    private const val AI_PREFERENCES_DONE = "ai_preferences_answers"

    fun run(context: Context) {
        val app = context.applicationContext
        WorkManager.getInstance(app).cancelUniqueWork(NEPHAI_SYNC_WORK)
        deletePrefs(app, NEPHAI_PREFS + COMMERCIAL_PREFS)
        removeOldAiPreferencesAnswers(app)
    }

    private fun removeOldAiPreferencesAnswers(context: Context) {
        val done = context.getSharedPreferences(DONE_PREFS, Context.MODE_PRIVATE)
        if (done.getBoolean(AI_PREFERENCES_DONE, false)) return
        Thread {
            runCatching { com.boox.day.data.ai.AiPreferencesRepository(context).removeOldAnswers() }
                .onSuccess {
                    done.edit().putBoolean(AI_PREFERENCES_DONE, true).apply()
                    Log.i(TAG, "Removed the old AI Preferences answers")
                }
                .onFailure { Log.w(TAG, "Old AI Preferences answers not removed: ${it.javaClass.simpleName}") }
        }.start()
    }

    private fun deletePrefs(context: Context, names: List<String>) {
        for (name in names) {
            if (context.deleteSharedPreferences(name)) Log.i(TAG, "Removed $name")
        }
    }
}

package com.boox.day.startup

import android.content.Context
import android.util.Log
import com.boox.day.APP_SETTINGS_KEY
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.KvProxy
import com.boox.day.security.UserApiKeyStore

/**
 * Loads saved app settings at startup while keeping OpenRouter key reads isolated from Room
 * settings reads (Phase 1 H11 / Decision D7).
 *
 * - Missing settings row (fresh install): returns [Outcome.Loaded] with [fromStorage] = false so
 *   the app proceeds with defaults.
 * - Valid stored settings row: merges any saved API key and returns [Outcome.Loaded] with
 *   [fromStorage] = true.
 * - Room read or JSON decode failure: returns [Outcome.Failed] WITHOUT marking
 *   [GlobalAppSettings.loaded] true, so callers route to [StartupFailureScreen] instead of
 *   overwriting the user's saved settings with defaults.
 */
object StartupSettingsLoader {
    private const val TAG = "StartupSettingsLoader"

    sealed interface Outcome {
        data class Loaded(val settings: AppSettings, val fromStorage: Boolean) : Outcome
        data class Failed(val error: Throwable) : Outcome
    }

    fun load(context: Context): Outcome {
        val appContext = context.applicationContext
        return loadWith(
            initialSettings = GlobalAppSettings.current,
            readApiKey = { UserApiKeyStore.getOpenRouterKey(appContext) },
            readStoredSettings = { KvProxy(appContext).get(APP_SETTINGS_KEY, AppSettings.serializer()) },
        )
    }

    internal fun loadWith(
        initialSettings: AppSettings,
        readApiKey: () -> String?,
        readStoredSettings: () -> AppSettings?,
    ): Outcome {
        val savedApiKey = runCatching { readApiKey() }
            .onFailure { Log.w(TAG, "OpenRouter key could not be read at startup (${it.javaClass.simpleName})") }
            .getOrNull()

        val stored = try {
            readStoredSettings()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to read saved app settings (${t.javaClass.simpleName})", t)
            return Outcome.Failed(t)
        }

        val merged = when {
            stored != null && savedApiKey != null -> stored.copy(userOpenRouterApiKey = savedApiKey)
            stored != null -> stored
            savedApiKey != null -> initialSettings.copy(userOpenRouterApiKey = savedApiKey)
            else -> initialSettings
        }
        return Outcome.Loaded(settings = merged, fromStorage = stored != null)
    }
}

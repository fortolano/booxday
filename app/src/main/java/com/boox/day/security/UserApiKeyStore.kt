package com.boox.day.security

import android.content.Context
import android.content.SharedPreferences

/**
 * The user's own OpenRouter key (Phase 3, Oct 2026): kept in encrypted preferences. Older versions
 * stored it in the plain "api_key_prefs" file; it moves over on first read. If encrypted storage is
 * unavailable on the device, the plain file keeps being used so the key isn't lost.
 *
 * Call off the main thread (the Android Keystore can be slow).
 */
object UserApiKeyStore {
    private const val TAG = "UserApiKeyStore"
    private const val ENCRYPTED_PREFS = "user_api_key_encrypted"
    private const val LEGACY_PREFS = "api_key_prefs"
    private const val KEY_OPENROUTER = "openrouter_api_key"

    @Volatile
    private var encrypted: LazyEncryptedPrefs? = null

    private fun securePrefs(context: Context): SharedPreferences? {
        val lazy = encrypted ?: synchronized(this) {
            encrypted ?: LazyEncryptedPrefs(context.applicationContext, ENCRYPTED_PREFS, TAG).also { encrypted = it }
        }
        return lazy.getOrNull()
    }

    private fun legacyPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)

    fun getOpenRouterKey(context: Context): String? {
        val legacy = legacyPrefs(context)
        val legacyKey = legacy.getString(KEY_OPENROUTER, null)
        val secure = securePrefs(context) ?: return legacyKey
        if (legacyKey != null) {
            // Move it over: write the encrypted copy first, then drop the plain one
            if (secure.edit().putString(KEY_OPENROUTER, legacyKey).commit()) {
                legacy.edit().remove(KEY_OPENROUTER).commit()
            }
            return legacyKey
        }
        return secure.getString(KEY_OPENROUTER, null)
    }

    fun setOpenRouterKey(context: Context, key: String?) {
        val legacy = legacyPrefs(context)
        val secure = securePrefs(context)
        if (secure == null) {
            legacy.edit().apply {
                if (key != null) putString(KEY_OPENROUTER, key) else remove(KEY_OPENROUTER)
            }.apply()
            return
        }
        secure.edit().apply {
            if (key != null) putString(KEY_OPENROUTER, key) else remove(KEY_OPENROUTER)
        }.apply()
        legacy.edit().remove(KEY_OPENROUTER).apply()
    }
}

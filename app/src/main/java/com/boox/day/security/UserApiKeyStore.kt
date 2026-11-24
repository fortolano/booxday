package com.boox.day.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.annotation.VisibleForTesting

/**
 * The user's own OpenRouter key (Phase 3, Oct 2026): kept in encrypted preferences. Older versions
 * stored it in the plain "api_key_prefs" file; it moves over on first read (which also supports
 * the developer adb setup script). New writes fail closed if encrypted storage is unavailable —
 * keys are never written to plaintext preferences. Deleting a key while encrypted storage is
 * unavailable records a tombstone so the old encrypted copy is purged once storage recovers.
 *
 * Call off the main thread (the Android Keystore can be slow).
 */
object UserApiKeyStore {
    private const val TAG = "UserApiKeyStore"
    private const val ENCRYPTED_PREFS = "user_api_key_encrypted"
    private const val LEGACY_PREFS = "api_key_prefs"
    private const val KEY_OPENROUTER = "openrouter_api_key"
    private const val KEY_DELETED_TOMBSTONE = "openrouter_key_deleted"

    @Volatile
    private var encrypted: LazyEncryptedPrefs? = null

    @VisibleForTesting
    @Volatile
    internal var securePrefsOverride: ((Context) -> SharedPreferences?)? = null

    private fun securePrefs(context: Context): SharedPreferences? {
        securePrefsOverride?.let { return it(context) }
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
        val tombstoned = legacy.getBoolean(KEY_DELETED_TOMBSTONE, false)
        val secure = securePrefs(context)
        if (legacyKey != null) {
            // Read-only migration from legacy/adb plaintext file into encrypted storage
            if (secure != null && secure.edit().putString(KEY_OPENROUTER, legacyKey).commit()) {
                legacy.edit()
                    .remove(KEY_OPENROUTER)
                    .remove(KEY_DELETED_TOMBSTONE)
                    .commit()
            }
            return legacyKey
        }
        if (tombstoned) {
            if (secure != null && secure.edit().remove(KEY_OPENROUTER).commit()) {
                legacy.edit().remove(KEY_DELETED_TOMBSTONE).commit()
            }
            return null
        }
        return secure?.getString(KEY_OPENROUTER, null)
    }

    /**
     * Persists [key] in encrypted storage, or removes it when [key] is null.
     * Returns false if saving a non-null key failed because encrypted storage is unavailable.
     */
    fun setOpenRouterKey(context: Context, key: String?): Boolean {
        val legacy = legacyPrefs(context)
        val secure = securePrefs(context)
        if (key == null) {
            legacy.edit().remove(KEY_OPENROUTER).commit()
            val removedSecure = secure?.edit()?.remove(KEY_OPENROUTER)?.commit() == true
            if (removedSecure) {
                legacy.edit().remove(KEY_DELETED_TOMBSTONE).commit()
            } else {
                // Encrypted storage unavailable or commit failed: tombstone so the key never resurrects
                legacy.edit().putBoolean(KEY_DELETED_TOMBSTONE, true).commit()
            }
            return true
        }
        if (secure == null) {
            Log.e(TAG, "Encrypted storage unavailable; refusing to save OpenRouter key in plaintext")
            return false
        }
        if (!secure.edit().putString(KEY_OPENROUTER, key).commit()) {
            Log.e(TAG, "Failed to commit OpenRouter key to encrypted storage")
            return false
        }
        legacy.edit()
            .remove(KEY_OPENROUTER)
            .remove(KEY_DELETED_TOMBSTONE)
            .commit()
        return true
    }
}

package com.boox.day.security

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The user's own OpenRouter key in memory (Oct 2026): the only key AI features use. Every request
 * and every AI entry point reads it here; [UserApiKeyStore] keeps it encrypted on disk.
 *
 * [init] loads it at app start; [ensureLoaded] covers callers that run before that load finished.
 * Compose state, so settings and gates update when it changes.
 */
object UserOpenRouterKey {
    @Volatile
    private var appContext: Context? = null

    @Volatile
    var isLoaded: Boolean = false
        private set

    /** The saved key, or null when the user has not added one. */
    var value: String? by mutableStateOf(null)
        private set

    val hasKey: Boolean get() = !value.isNullOrBlank()

    /** Call once from Application.onCreate; the load itself runs off the main thread ([ensureLoaded]). */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Reads the key from encrypted storage if that has not happened yet. Call off the main thread. */
    fun ensureLoaded() {
        if (isLoaded) return
        val context = appContext ?: return
        synchronized(this) {
            if (isLoaded) return
            value = UserApiKeyStore.getOpenRouterKey(context)?.trim()?.takeIf { it.isNotEmpty() }
            isLoaded = true
        }
    }

    /** Saves [key] (null removes it). Call off the main thread (the Android Keystore can be slow). */
    fun save(context: Context, key: String?) {
        val cleaned = key?.trim()?.takeIf { it.isNotEmpty() }
        UserApiKeyStore.setOpenRouterKey(context, cleaned)
        synchronized(this) {
            value = cleaned
            isLoaded = true
        }
        // The model list for this account (which models it allows), at once rather than at next start
        if (cleaned != null) com.boox.day.api.OpenRouterModelCatalog.onKeySaved()
    }
}

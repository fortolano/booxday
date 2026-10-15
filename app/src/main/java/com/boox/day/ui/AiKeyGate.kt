package com.boox.day.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.boox.day.security.UserOpenRouterKey

/**
 * AI features run only on the user's own OpenRouter key (Oct 2026). Every AI entry point calls
 * [requireKey] before it starts; without a saved key the "Add your OpenRouter key" popup opens
 * instead (ui/dialogs/AiKeyRequiredPopup.kt). AiClient opens it too when a request finds no key
 * or a rejected one. Compose state: MainScreen shows the popup and keeps raw drawing off while
 * it is open.
 */
object AiKeyGate {
    enum class Reason { NO_KEY, KEY_REJECTED }

    /** Why the popup is open, or null when it is closed. */
    var openReason by mutableStateOf<Reason?>(null)
        private set

    val isOpen: Boolean get() = openReason != null

    /**
     * Set when the user taps Add Key: MainScreen opens AI Settings ([consumeAddKey]), whose first
     * card is the key, with its paste field showing while no key is saved.
     */
    var addKeyPending by mutableStateOf(false)
        private set

    /** True when a key is saved; otherwise opens the popup and returns false. */
    fun requireKey(): Boolean {
        if (UserOpenRouterKey.hasKey) return true
        open(Reason.NO_KEY)
        return false
    }

    /** Opens the popup; safe from any thread. */
    fun open(reason: Reason) {
        onMain { openReason = reason }
    }

    fun close() {
        onMain { openReason = null }
    }

    /** Add Key: close the popup and ask MainScreen for AI Settings at the key field. */
    fun requestAddKey() {
        onMain {
            openReason = null
            addKeyPending = true
        }
    }

    /** MainScreen: true once per Add Key tap (then it opens AI Settings). */
    fun consumeAddKey(): Boolean {
        if (!addKeyPending) return false
        addKeyPending = false
        return true
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else Handler(Looper.getMainLooper()).post(block)
    }
}

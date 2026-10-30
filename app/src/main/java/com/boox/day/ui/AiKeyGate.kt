package com.boox.day.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.boox.day.api.ai.AiProblem
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.security.UserOpenRouterKey

/**
 * AI features run only on the user's own OpenRouter key (Oct 2026). Every AI entry point calls
 * [requireKey] before it starts; without a saved key the "Add your OpenRouter key" popup opens
 * instead (ui/dialogs/AiKeyRequiredPopup.kt). AiClient opens it too when a request finds no key
 * or a rejected one. Compose state: MainScreen shows the popup and keeps raw drawing off while
 * it is open.
 *
 * The popup explains OpenRouter before the browser opens (Oct 2026). [withKey] keeps the action that
 * asked for the key: once Connect OpenRouter has saved one, the "AI is on" popup
 * (ui/dialogs/AiKeyRequiredPopup.kt, [AiKeyGateHost][com.boox.day.ui.dialogs.AiKeyGateHost]) offers
 * to go on with it.
 */
object AiKeyGate {
    enum class Reason { NO_KEY, KEY_REJECTED }

    /** Why the popup is open, or null when it is closed. */
    var openReason by mutableStateOf<Reason?>(null)
        private set

    /**
     * The Connect OpenRouter result the host is showing ("AI is on", or what went wrong), or null.
     * Holds [com.boox.day.api.OpenRouterConnect.resultCount] of the result it shows.
     */
    var shownResult by mutableStateOf<Int?>(null)
        private set

    /** The last result the host showed or let the AI Settings card show: each result shows once. */
    var handledResult = 0
        private set

    val isOpen: Boolean get() = openReason != null || shownResult != null

    /** The feature that asked for the key: it goes on after "AI is on" (memory only). */
    private var pendingAction: (() -> Unit)? = null

    val hasPendingAction: Boolean get() = pendingAction != null

    /**
     * Set when the user taps Add Key: MainScreen opens AI Settings ([consumeAddKey]), whose first
     * card is the key, with its paste field showing while no key is saved.
     */
    var addKeyPending by mutableStateOf(false)
        private set

    /**
     * True when AI Features is on and a key is saved. With AI Features off (Oct 2026) nothing goes to
     * OpenRouter: a snackbar says so and it returns false; most AI buttons are greyed out then, but
     * the task card's Break Down and the selection bar's Convert to Tasks come here. Without a key
     * it opens the popup and returns false; [resume] then runs after Connect OpenRouter, when the
     * user taps Continue on "AI is on".
     */
    fun requireKey(resume: (() -> Unit)? = null): Boolean {
        if (!GlobalAppSettings.current.aiEnabled) {
            SnackState.globalSnackFlow.tryEmit(
                SnackConf(text = AiProblem.AI_OFF.message, duration = SnackConf.DURATION_STANDARD)
            )
            return false
        }
        if (UserOpenRouterKey.hasKey) return true
        pendingAction = resume
        open(Reason.NO_KEY)
        return false
    }

    /**
     * Runs [action] now when [requireKey] allows it. Without a key the popup opens and [action] waits
     * for Connect OpenRouter: "AI is on" then offers to go on with it. Main thread.
     */
    fun withKey(action: () -> Unit) {
        if (requireKey(resume = action)) action()
    }

    /** Opens the popup; safe from any thread. */
    fun open(reason: Reason) {
        onMain { openReason = reason }
    }

    /** Not Now: the popup closes and the waiting feature is dropped. */
    fun close() {
        onMain {
            openReason = null
            pendingAction = null
        }
    }

    /** Continue to OpenRouter: the popup closes, the feature waits for the result. */
    fun closeForConnect() {
        onMain { openReason = null }
    }

    /** Add Key: close the popup and ask MainScreen for AI Settings at the key field. */
    fun requestAddKey() {
        onMain {
            openReason = null
            pendingAction = null
            addKeyPending = true
            pasteRequested = true
        }
    }

    /** "I already have a key" (or no browser): the key card opens with its paste field showing. */
    private var pasteRequested = false

    /** The key card: true once after [requestAddKey]. */
    fun consumePasteRequest(): Boolean {
        val requested = pasteRequested
        pasteRequested = false
        return requested
    }

    /** The host shows result [count] (it is newer than [handledResult]). */
    fun showResult(count: Int) {
        handledResult = maxOf(handledResult, count)
        shownResult = count
    }

    /** A result the AI Settings card shows itself: the host leaves it. */
    fun markResultHandled(count: Int) {
        handledResult = maxOf(handledResult, count)
    }

    /**
     * Closes the result popup. [resume] runs the waiting feature (Continue after "AI is on"); without
     * it the feature is dropped.
     */
    fun closeResult(resume: Boolean) {
        shownResult = null
        val action = pendingAction
        pendingAction = null
        if (resume && UserOpenRouterKey.hasKey) action?.invoke()
    }

    /** Connect Again after a failed connection: the result popup closes, the feature keeps waiting. */
    fun closeResultKeepWaiting() {
        shownResult = null
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

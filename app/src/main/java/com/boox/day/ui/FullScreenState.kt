package com.boox.day.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Full screen (Oct 2026): the toolbar and canvas take the whole screen and the calendar section is
 * hidden; Memos and Tasks share it. It lasts for the session only: the app always opens in the
 * normal layout. Compose state like [AiKeyGate], so the toolbar, MainScreen and the canvas host read
 * it directly instead of passing it through MainScreen's root lambda (at the JVM method-size limit).
 *
 * The canvas grows downward from the same top-left corner, so Memos ink keeps its place; ink written
 * low on the page is below the normal view until full screen shows it again ([memoHasMoreBelow]).
 */
object FullScreenState {
    var isOn by mutableStateOf(false)
        private set

    /** Memos, normal view: the page has ink or images below the visible area (toolbar button badge). */
    var memoHasMoreBelow by mutableStateOf(false)

    /** Memo pages whose "more below" hint was shown this session (once per page). */
    val hintedPages = mutableSetOf<String>()

    /**
     * Run before every switch: closes a text box and puts down a lasso selection on the active
     * canvas, which the resize would otherwise drop. Set by MainScreen.
     */
    var beforeChange: (() -> Unit)? = null

    fun toggle() = set(!isOn)

    fun expand() = set(true)

    fun collapse() = set(false)

    private fun set(on: Boolean) {
        if (on == isOn) return
        beforeChange?.invoke()
        isOn = on
    }
}

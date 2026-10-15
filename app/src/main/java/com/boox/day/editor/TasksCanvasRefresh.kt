package com.boox.day.editor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Coalesces the full-canvas refreshes that task operations request (plan §13.4, Oct 2026).
 *
 * Every [DrawCanvas.forceUpdate] is a full redraw plus a close → 100 ms → reopen e-paper cycle,
 * and multi-row operations used to emit one per row (up to 11 for Delete Task Page). Requests
 * made within [WINDOW_MS] of each other become one emit. Call sites only: the forceUpdate
 * observer in DrawCanvas is unchanged.
 *
 * The emit is skipped when the Memos canvas is active: there it would only redraw Memos (the
 * "strokes get cleared" hazard), and switching back to Tasks regenerates its bitmap anyway.
 */
@OptIn(FlowPreview::class)
object TasksCanvasRefresh {
    private const val WINDOW_MS = 75L

    // A conflated channel keeps a request made before the collector below has started
    // (a SharedFlow without subscribers would drop it).
    private val requests = Channel<Unit>(Channel.CONFLATED)

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            requests.receiveAsFlow().debounce(WINDOW_MS).collect {
                if (!DrawCanvas.memoModeChanged.value) {
                    DrawCanvas.forceUpdate.emit(null)
                }
            }
        }
    }

    private var holds = 0
    private var requestedWhileHeld = false

    /** Ask for one full Tasks canvas refresh; safe from any thread. */
    fun request() {
        synchronized(this) {
            if (holds > 0) {
                requestedWhileHeld = true
                return
            }
        }
        requests.trySend(Unit)
    }

    /**
     * Hold requests while a popup works on many tasks (the start-the-day review, plan T4): they
     * become one refresh on [release] instead of one per task.
     */
    fun hold() {
        synchronized(this) { holds++ }
    }

    /** Ends a [hold]; refreshes once if anything asked for it meanwhile. */
    fun release() {
        val refresh = synchronized(this) {
            holds = (holds - 1).coerceAtLeast(0)
            if (holds == 0 && requestedWhileHeld) {
                requestedWhileHeld = false
                true
            } else {
                false
            }
        }
        if (refresh) requests.trySend(Unit)
    }
}

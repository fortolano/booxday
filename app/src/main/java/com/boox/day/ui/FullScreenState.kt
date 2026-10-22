package com.boox.day.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.boox.day.tasks.TaskPagination
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Full screen (Oct 2026): the toolbar and canvas take the whole screen and the calendar section is
 * hidden; Memos and Tasks share it. It lasts for the session only: the app always opens in the
 * normal layout. Compose state like [AiKeyGate], so the toolbar, MainScreen and the canvas host read
 * it directly instead of passing it through MainScreen's root lambda (at the JVM method-size limit).
 *
 * The canvas grows downward from the same top-left corner, so Memos ink keeps its place; ink written
 * low on the page is below the normal view until full screen shows it again ([memoHasMoreBelow]).
 * Tasks rows keep their height and more fit: the shown day's tasks go on longer pages
 * (tasks/TaskPagination), and back on pages of 10 before the normal view returns ([collapse]).
 */
object FullScreenState {
    var isOn by mutableStateOf(false)
        private set

    /** A switch is running (Tasks pages being rearranged): MainScreen keeps the pen off meanwhile. */
    var transitioning by mutableStateOf(false)
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

    /**
     * Set by the canvas host: shows the Tasks page again once its day is back on pages of 10, and
     * returns when it is shown (a tool waiting in [exitThen] then reads the right rows).
     */
    var afterTasksCollapsed: (suspend (TaskPagination.Reflowed) -> Unit)? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // One switch at a time, in order: a collapse asked for during the Tasks reflow waits for it
    private val switching = Mutex()

    fun toggle() = if (isOn) collapse() else expand()

    fun expand() {
        if (isOn || transitioning) return
        beforeChange?.invoke()
        isOn = true
    }

    /**
     * Back to the normal view. A day whose Tasks are on full screen pages goes back to pages of 10
     * first, so the normal view never shows them; then [then] runs. If that fails, full screen stays.
     */
    fun collapse(then: (() -> Unit)? = null) {
        if (!isOn && TaskPagination.current == null) {
            then?.invoke()
            return
        }
        scope.launch {
            val done = switching.withLock {
                if (!isOn && TaskPagination.current == null) return@withLock true
                beforeChange?.invoke()
                transitioning = true
                try {
                    val record = TaskPagination.current
                    val restored = TaskPagination.collapse()
                    if (!restored) return@withLock false
                    if (record != null) afterTasksCollapsed?.invoke(record)
                    isOn = false
                    true
                } finally {
                    transitioning = false
                }
            }
            if (done) {
                then?.invoke()
            } else {
                SnackState.globalSnackFlow.tryEmit(
                    SnackConf(text = "Tasks couldn't go back to the normal view. Try again.", duration = SnackConf.DURATION_ERROR)
                )
            }
        }
    }

    /**
     * Tools that place, sort or move tasks run in the normal view (their code works with pages of 10):
     * in full screen they leave it first. Tasks they hold may have moved meanwhile: read them again.
     */
    fun exitThen(block: () -> Unit) {
        if (!isOn && TaskPagination.current == null) block() else collapse(then = block)
    }

    /** The canvas host's Tasks reflow for full screen, in turn with [collapse] (pen off meanwhile). */
    suspend fun <T> whileSwitching(block: suspend () -> T): T = switching.withLock {
        transitioning = true
        try {
            block()
        } finally {
            transitioning = false
        }
    }
}

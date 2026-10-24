package com.boox.day.today

import android.content.Context
import android.util.Log
import androidx.room.InvalidationTracker
import com.boox.day.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Tells the day's outputs (sleep-screen agenda, Markdown daily note) that what they show may have
 * changed (Oct 2026). Watches the tables themselves, like share/TasksFeedNotifier, so changes made
 * outside the app's repositories count too (sync, handwriting recognition, page reading).
 *
 * A burst of changes is one call, [quietMs] after the last. Ink alone (stroke, the memo panel) waits
 * [inkQuietMs], so writing on a page doesn't redraw anything until the pen has rested.
 */
class DayDataWatcher(
    private val quietMs: Long = 5_000L,
    private val inkQuietMs: Long = 60_000L,
    private val onChanged: () -> Unit,
) {
    private var job: Job? = null

    fun start(context: Context, scope: CoroutineScope) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        job = scope.launch(Dispatchers.IO) {
            val tracker = runCatching { AppDatabase.getDatabase(app).invalidationTracker }
                .onFailure { Log.w(TAG, "Database not available; day outputs won't follow changes", it) }
                .getOrNull() ?: return@launch
            val changes = Channel<Set<String>>(Channel.CONFLATED)
            val observer = object : InvalidationTracker.Observer(TABLES) {
                override fun onInvalidated(tables: Set<String>) {
                    changes.trySend(tables)
                }
            }
            tracker.addObserver(observer)
            try {
                while (true) {
                    // Wait for a change, then until nothing changes for the quiet time
                    var inkOnly = changes.receive().all { it == "stroke" }
                    while (true) {
                        val wait = if (inkOnly) inkQuietMs else quietMs
                        val more = withTimeoutOrNull(wait) { changes.receive() } ?: break
                        inkOnly = inkOnly && more.all { it == "stroke" }
                    }
                    runCatching { onChanged() }.onFailure { Log.w(TAG, "Day output update failed", it) }
                }
            } finally {
                tracker.removeObserver(observer)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private companion object {
        const val TAG = "DayDataWatcher"
        val TABLES = arrayOf(
            "task", "stroke", "calendar_events", "local_events", "calendar_selections",
            "daypage", "unified_ocr_text", "boox_note_links",
        )
    }
}

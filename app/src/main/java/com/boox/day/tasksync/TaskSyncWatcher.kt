package com.boox.day.tasksync

import androidx.room.InvalidationTracker
import com.boox.day.data.db.AppDatabase
import com.boox.day.editor.DrawCanvas
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import kotlin.coroutines.coroutineContext

/**
 * Runs [TaskSyncScanner] when tasks change (Oct 2026). Watches the task and stroke tables, so
 * every writer counts: the app's own edits, recognition, repeating tasks, full screen Tasks. A
 * burst of changes is one scan, [debounceMs] after it ends; nothing is looked at while the pen is
 * on the page or within [inkQuietMs] of the last ink change (writing is still going on). After that
 * quiet the words have settled, so handwriting goes at once: about 10 s after the pen lifts or the
 * typing ends (owner, 31 Oct 2026). Words read later are a change too, so they follow the same way.
 */
internal class TaskSyncWatcher(
    private val db: AppDatabase,
    private val scope: CoroutineScope,
    private val scanner: TaskSyncScanner,
    private val clock: () -> Long = System::currentTimeMillis,
    private val today: () -> String = { LocalDate.now().toString() },
    private val debounceMs: Long = 10_000L,
    private val inkQuietMs: Long = TaskSyncRules.INK_QUIET_MS,
    private val penActive: () -> Boolean = { DrawCanvas.isStylusActiveWithin(5_000L) },
    private val onQueued: () -> Unit = {}
) {
    @Volatile
    private var lastInkAt = 0L
    @Volatile
    private var lastChangeAt = 0L
    private val lock = Any()
    private var pending: Job? = null
    private var pendingAt = Long.MAX_VALUE
    private val scanning = Mutex()

    private val observer = object : InvalidationTracker.Observer(arrayOf("task", "stroke")) {
        override fun onInvalidated(tables: Set<String>) {
            lastChangeAt = clock()
            if ("stroke" in tables) lastInkAt = clock()
            scheduleScan(debounceMs)
        }
    }

    fun start() {
        db.invalidationTracker.addObserver(observer)
        scheduleScan(0L)  // what changed while sync wasn't watching
    }

    fun stop() {
        db.invalidationTracker.removeObserver(observer)
        synchronized(lock) {
            pending?.cancel()
            pending = null
            pendingAt = Long.MAX_VALUE
        }
    }

    /**
     * A change (debounce) replaces the pending scan; a timed rescan ([onlyIfSooner]: handwriting
     * settling, an emptied row's wait) never pushes back a scan that is already due sooner.
     */
    private fun scheduleScan(afterMs: Long, onlyIfSooner: Boolean = false) {
        synchronized(lock) {
            val at = clock() + afterMs
            if (onlyIfSooner && pending != null && pendingAt <= at) return
            pending?.cancel()
            pendingAt = at
            pending = scope.launch(Dispatchers.IO) {
                delay(afterMs)
                waitUntilQuiet()
                val self = coroutineContext[Job]
                synchronized(lock) {
                    if (pending === self) {
                        pending = null
                        pendingAt = Long.MAX_VALUE
                    }
                }
                runScan()
            }
        }
    }

    private suspend fun waitUntilQuiet() {
        while (true) {
            val quietFor = clock() - lastInkAt
            if (quietFor >= inkQuietMs && !penActive()) return
            delay(maxOf(500L, inkQuietMs - quietFor))
        }
    }

    private suspend fun runScan() {
        val settled = clock() - lastChangeAt >= debounceMs  // a timed rescan may come sooner
        val result = scanning.withLock { scanner.scan(clock(), today(), wordsSettled = settled) }
        result.rescanAt?.let { scheduleScan(maxOf(0L, it - clock()), onlyIfSooner = true) }
        if (result.queued > 0 || result.deleted > 0) onQueued()
    }
}

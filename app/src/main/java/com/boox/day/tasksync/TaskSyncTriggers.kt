package com.boox.day.tasksync

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * When task sync runs by itself while BooxDay is open (plan step 2.7, owner decisions 11 and 34):
 * - about 30 s after an edit: the change watcher's scan (5 s after the edit) queued something, then
 *   [AFTER_EDIT_MS]; more edits in that time push it back, so a burst of writing is one run;
 * - when BooxDay comes to the front (its first start too), at most every [RETURN_GAP_MS] since the
 *   last run of any kind: each run costs about one Google call per ticked list, from a limit all
 *   BooxDay users share;
 * - the calendar's Sync button, always.
 * The background worker runs it on "Sync frequency" (sync/BackgroundSyncWorker). Every run looks at
 * the service before sending ([TaskSyncRun]). Runs use the app's scope, so they finish when the
 * screen that asked closes.
 */
internal object TaskSyncTriggers {
    const val AFTER_EDIT_MS = 25_000L
    const val RETURN_GAP_MS = 10 * 60_000L

    @Volatile
    private var shared: Triggers? = null

    private fun triggers(context: Context): Triggers? {
        shared?.let { return it }
        val scope = TaskSync.appScope ?: return null
        val app = context.applicationContext
        return synchronized(this) {
            shared ?: Triggers(
                scope,
                isOn = { TaskSync.isOn(app) },
                lastRunAt = { TaskSync.lastRunAt(app) },
                run = { changedAt -> TaskSyncRun.run(app, changedAt) }
            ).also { shared = it }
        }
    }

    fun afterEdit(context: Context) {
        triggers(context)?.afterEdit()
    }

    /** MainScreen's ON_RESUME. */
    fun onReturn(context: Context) {
        triggers(context)?.onReturn()
    }

    /** The calendar's Sync button. */
    fun syncNow(context: Context) {
        triggers(context)?.syncNow()
    }

    /** Task sync turned off: a run waiting after an edit doesn't start. */
    fun cancel() {
        shared?.cancel()
    }

    internal class Triggers(
        private val scope: CoroutineScope,
        private val isOn: () -> Boolean,
        private val lastRunAt: () -> Long,
        private val run: suspend (changedAt: Long) -> TaskSyncRun.Result,
        private val clock: () -> Long = System::currentTimeMillis,
        private val afterEditMs: Long = AFTER_EDIT_MS,
        private val returnGapMs: Long = RETURN_GAP_MS
    ) {
        private val lock = Any()
        private var waiting: Job? = null

        /** A newer edit replaces a run still waiting; one already running is left to finish. */
        fun afterEdit() {
            if (!isOn()) return
            val changedAt = clock()
            synchronized(lock) {
                waiting?.cancel()
                waiting = scope.launch {
                    delay(afterEditMs)
                    val self = coroutineContext[Job]
                    synchronized(lock) { if (waiting === self) waiting = null }
                    run(changedAt)
                }
            }
        }

        /** @return whether a run started */
        fun onReturn(): Boolean {
            if (!isOn()) return false
            val since = clock() - lastRunAt()
            if (since in 0 until returnGapMs) return false  // a clock set back doesn't block runs for good
            scope.launch { run(0L) }
            return true
        }

        fun syncNow() {
            if (!isOn()) return
            val changedAt = clock()
            scope.launch { run(changedAt) }
        }

        fun cancel() {
            synchronized(lock) {
                waiting?.cancel()
                waiting = null
            }
        }
    }
}

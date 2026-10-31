package com.boox.day.tasksync

import android.content.Context
import com.boox.day.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * When task sync runs by itself while BooxDay is open (plan step 2.7, owner decisions 11 and 34):
 * - about 10 s after an edit: the change watcher's scan (10 s after the last change, owner 31 Oct
 *   2026) queued something, then [AFTER_EDIT_MS]; more edits in that time push it back;
 * - when BooxDay comes to the front (its first start too), at most every [RETURN_GAP_MS] since the
 *   last run of any kind: each run costs about one Google call per ticked list, from a limit all
 *   BooxDay users share;
 * - the calendar's Sync button, always;
 * - again after a run that couldn't finish (offline, Google busy, an error, or a change whose send
 *   failed and waits): after [RETRY_WAITS_MS] (1, 2, 4, 8, 15 minutes), then no more until the next
 *   edit, return or Sync (owner, 30 Oct 2026). A run that needs the user (sign in again, deletes to
 *   approve, no list) isn't retried.
 * The background worker runs it on "Sync frequency" (sync/BackgroundSyncWorker). Every run looks at
 * the service before sending ([TaskSyncRun]). Runs use the app's scope, so they finish when the
 * screen that asked closes.
 */
internal object TaskSyncTriggers {
    const val AFTER_EDIT_MS = 1_000L
    const val RETURN_GAP_MS = 10 * 60_000L
    val RETRY_WAITS_MS = listOf(1L, 2L, 4L, 8L, 15L).map { it * 60_000L }

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
                nextWaitingAt = { AppDatabase.getDatabase(app).taskSyncDao().nextOutboxAttemptAt() },
                run = { changedAt, handwritingNow -> TaskSyncRun.run(app, changedAt, handwritingNow) }
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

    /** Task sync turned off: a run waiting after an edit, or a retry, doesn't start. */
    fun cancel() {
        shared?.cancel()
    }

    internal class Triggers(
        private val scope: CoroutineScope,
        private val isOn: () -> Boolean,
        private val lastRunAt: () -> Long,
        private val nextWaitingAt: () -> Long?,
        private val run: suspend (changedAt: Long, handwritingNow: Boolean) -> TaskSyncRun.Result,
        private val clock: () -> Long = System::currentTimeMillis,
        private val afterEditMs: Long = AFTER_EDIT_MS,
        private val returnGapMs: Long = RETURN_GAP_MS,
        private val retryWaitsMs: List<Long> = RETRY_WAITS_MS
    ) {
        private val lock = Any()
        private var waiting: Job? = null
        private var retry: Job? = null
        private var retriesDone = 0

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
                    runAndRetry(changedAt, fresh = true)
                }
            }
        }

        /** @return whether a run started */
        fun onReturn(): Boolean {
            if (!isOn()) return false
            val since = clock() - lastRunAt()
            if (since in 0 until returnGapMs) return false  // a clock set back doesn't block runs for good
            scope.launch { runAndRetry(0L, fresh = true) }
            return true
        }

        fun syncNow() {
            if (!isOn()) return
            val changedAt = clock()
            scope.launch { runAndRetry(changedAt, fresh = true, handwritingNow = true) }
        }

        fun cancel() {
            synchronized(lock) {
                waiting?.cancel()
                waiting = null
                retry?.cancel()
                retry = null
                retriesDone = 0
            }
        }

        /**
         * [fresh]: an edit, a return or Sync, which starts the retry waits from the first again.
         * [handwritingNow]: Sync was tapped (handwriting without its minute's wait).
         */
        private suspend fun runAndRetry(changedAt: Long, fresh: Boolean, handwritingNow: Boolean = false) {
            if (fresh) synchronized(lock) { retriesDone = 0 }
            val result = run(changedAt, handwritingNow)
            val waitingAt = withContext(Dispatchers.IO) { nextWaitingAt() }
            val unfinished = when (result) {
                TaskSyncRun.Result.OFFLINE, TaskSyncRun.Result.BUSY, TaskSyncRun.Result.FAILED -> true
                TaskSyncRun.Result.DONE -> waitingAt != null  // a send failed: it waits its turn
                else -> false  // needs the user, or off
            }
            val self = coroutineContext[Job]
            synchronized(lock) {
                if (!unfinished) {  // all sent: a retry still waiting isn't needed
                    retry?.takeIf { it !== self }?.cancel()
                    retry = null
                    retriesDone = 0
                    return
                }
                val wait = retryWaitsMs.getOrNull(retriesDone) ?: return  // no more: the next edit, return or Sync
                retriesDone++
                // Not before the waiting change's own time (Google's Retry-After, growing waits)
                val untilDue = (waitingAt ?: 0L) - clock()
                retry?.takeIf { it !== self }?.cancel()
                retry = scope.launch {
                    delay(maxOf(wait, untilDue))
                    if (isOn()) runAndRetry(0L, fresh = false)
                }
            }
        }
    }
}

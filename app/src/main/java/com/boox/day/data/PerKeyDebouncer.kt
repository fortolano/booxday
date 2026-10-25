package com.boox.day.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs [action] for a key once [quietMs] have passed without another [schedule] for that key
 * (Oct 2026: page images are saved when the pen rests, not every 500 ms while writing).
 * Keys are independent. [flush] runs a pending action at once on the caller's thread, [cancel]
 * drops it. An action never runs twice for one pending schedule.
 */
internal class PerKeyDebouncer(
    private val scope: CoroutineScope,
    private val quietMs: Long,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val action: (String) -> Unit,
) {
    private val pending = ConcurrentHashMap<String, Job>()

    fun schedule(key: String) {
        lateinit var job: Job
        job = scope.launch(start = CoroutineStart.LAZY) {
            sleep(quietMs)
            // A newer schedule, a flush or a cancel has taken over this key.
            if (pending.remove(key, job)) action(key)
        }
        pending.put(key, job)?.cancel()
        job.start()
    }

    fun isPending(key: String): Boolean = pending.containsKey(key)

    /** Runs the pending action for [key] now. Returns false when nothing was pending. */
    fun flush(key: String): Boolean {
        val job = pending.remove(key) ?: return false
        job.cancel()
        action(key)
        return true
    }

    fun flushAll() {
        pending.keys.toList().forEach { flush(it) }
    }

    fun cancel(key: String) {
        pending.remove(key)?.cancel()
    }
}

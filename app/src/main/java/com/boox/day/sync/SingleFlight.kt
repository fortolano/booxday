package com.boox.day.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One job at a time (Oct 2026). A caller asking for the job that is already running (same key)
 * waits for it and gets its result instead of starting a second copy; a different key waits its
 * turn. If the running job is cancelled (its caller left the screen), a caller that joined it runs
 * its own instead. Used by [MultiAccountSyncManager.syncAllAccounts], which can start from the
 * calendar's navigation, the cloud button, app start and the background worker at the same time.
 */
internal class SingleFlight<K, V>(private val clock: () -> Long = System::currentTimeMillis) {
    private val lock = Mutex()

    private class Running<K, V>(val key: K, val startedAt: Long, val result: CompletableDeferred<V>)

    @Volatile
    private var running: Running<K, V>? = null

    /**
     * [startedSince] (task sync, Oct 2026): a running job that started before then isn't joined,
     * because it may have missed a change made since; this waits for it, then runs its own.
     */
    suspend fun run(key: K, startedSince: Long = Long.MIN_VALUE, block: suspend () -> V): V {
        val current = running
        if (current != null && current.key == key && current.startedAt >= startedSince) {
            try {
                return current.result.await()
            } catch (e: CancellationException) {
                currentCoroutineContext().ensureActive()  // we were cancelled: give up too
                // the running job was cancelled, not us: run our own below
            }
        }
        return lock.withLock {
            val result = CompletableDeferred<V>()
            running = Running(key, clock(), result)
            try {
                block().also { result.complete(it) }
            } catch (e: Throwable) {
                result.completeExceptionally(e)
                throw e
            } finally {
                running = null
            }
        }
    }
}

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
internal class SingleFlight<K, V> {
    private val lock = Mutex()

    @Volatile
    private var running: Pair<K, CompletableDeferred<V>>? = null

    suspend fun run(key: K, block: suspend () -> V): V {
        val current = running
        if (current != null && current.first == key) {
            try {
                return current.second.await()
            } catch (e: CancellationException) {
                currentCoroutineContext().ensureActive()  // we were cancelled: give up too
                // the running job was cancelled, not us: run our own below
            }
        }
        return lock.withLock {
            val result = CompletableDeferred<V>()
            running = key to result
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

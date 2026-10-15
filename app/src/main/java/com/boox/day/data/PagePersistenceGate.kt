package com.boox.day.data

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Coordinates page-scoped persistence so stroke writes cannot race page deletion.
 */
object PagePersistenceGate {
    private val pageLocks = ConcurrentHashMap<String, ReentrantLock>()
    private val deletedOrDeletingPageIds = ConcurrentHashMap.newKeySet<String>()

    fun isDeleting(pageId: String): Boolean = deletedOrDeletingPageIds.contains(pageId)

    fun <T> withWrite(pageId: String, block: () -> T): T? {
        if (deletedOrDeletingPageIds.contains(pageId)) {
            return null
        }

        val lock = pageLocks.computeIfAbsent(pageId) { ReentrantLock() }
        return lock.withLock {
            if (deletedOrDeletingPageIds.contains(pageId)) {
                return@withLock null
            }
            block()
        }
    }

    fun <T> withDelete(pageId: String, block: () -> T): T {
        deletedOrDeletingPageIds.add(pageId)
        val lock = pageLocks.computeIfAbsent(pageId) { ReentrantLock() }

        try {
            return lock.withLock {
                block()
            }
        } catch (t: Throwable) {
            deletedOrDeletingPageIds.remove(pageId)
            throw t
        }
    }
}

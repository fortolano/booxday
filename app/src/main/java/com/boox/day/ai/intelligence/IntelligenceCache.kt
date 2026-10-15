package com.boox.day.ai.intelligence

import android.util.LruCache
import com.boox.day.data.ai.ScoredContext
import com.boox.day.data.ai.ScorerResult

/**
 * Three-tier caching for the Intelligence Engine.
 *
 * Uses a singleton pattern so cache can be shared across IntelligenceEngine
 * instances and properly invalidated when task data changes.
 */
class IntelligenceCache {

    companion object {
        // Cache TTLs in milliseconds
        private const val CONTEXT_CACHE_TTL = 30 * 60 * 1000L  // 30 minutes
        private const val SCORED_CACHE_TTL = 15 * 60 * 1000L   // 15 minutes
        private const val COMPONENT_CACHE_TTL = 5 * 60 * 1000L // 5 minutes

        // Cache sizes
        private const val SCORED_CACHE_SIZE = 12
        private const val COMPONENT_CACHE_SIZE = 50

        // Shared cache instances (singleton pattern)
        private val sharedScoredCache = LruCache<String, CacheEntry<ScoredContext>>(SCORED_CACHE_SIZE)
        private val sharedComponentCache = LruCache<String, CacheEntry<ScorerResult>>(COMPONENT_CACHE_SIZE)

        @Volatile
        private var sharedInvalidationTime = 0L

        /**
         * Global cache invalidation - call when task data changes.
         * Can be called from anywhere (e.g., TaskRepository after mutations).
         */
        @JvmStatic
        fun invalidateAll() {
            sharedInvalidationTime = System.currentTimeMillis()
            sharedScoredCache.evictAll()
            sharedComponentCache.evictAll()
        }
    }

    // Instance delegates to shared caches
    private val scoredCache get() = sharedScoredCache
    private val componentCache get() = sharedComponentCache

    // Last invalidation timestamp
    private val lastInvalidationTime get() = sharedInvalidationTime

    /**
     * Get cached scored context if valid.
     */
    fun getScoredContext(key: String): ScoredContext? {
        val entry = scoredCache.get(key) ?: return null
        return if (entry.isValid(SCORED_CACHE_TTL, lastInvalidationTime)) {
            entry.value
        } else {
            scoredCache.remove(key)
            null
        }
    }

    /**
     * Store scored context.
     */
    fun putScoredContext(key: String, context: ScoredContext) {
        scoredCache.put(key, CacheEntry(context))
    }

    /**
     * Get cached component result if valid.
     */
    fun getComponentResult(key: String): ScorerResult? {
        val entry = componentCache.get(key) ?: return null
        return if (entry.isValid(COMPONENT_CACHE_TTL, lastInvalidationTime)) {
            entry.value
        } else {
            componentCache.remove(key)
            null
        }
    }

    /**
     * Store component result.
     */
    fun putComponentResult(key: String, result: ScorerResult) {
        componentCache.put(key, CacheEntry(result))
    }

    /**
     * Invalidate all caches.
     */
    fun clear() {
        // Delegate to static invalidation
        invalidateAll()
    }

    /**
     * Invalidate only scored cache (keep component results).
     */
    fun invalidateScoredCache() {
        scoredCache.evictAll()
    }

    private data class CacheEntry<T>(
        val value: T,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun isValid(ttl: Long, invalidationTime: Long): Boolean {
            val now = System.currentTimeMillis()
            return (now - timestamp) < ttl && timestamp > invalidationTime
        }
    }
}

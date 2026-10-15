package com.boox.day.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Cache entity for Daily and Weekly summary results.
 *
 * Stores pre-generated AI summaries to avoid regenerating them on every request.
 * - Daily summaries are valid for 4 hours (plus content hash match)
 * - Weekly summaries are valid for 20 hours (plus content hash match)
 *
 * Cache key format:
 * - Daily: "daily_2024-12-12"
 * - Weekly: "weekly_2024-12-08_2024-12-14" (start_end of the week)
 */
@Entity(
    tableName = "summary_cache",
    indices = [Index(value = ["type"])]
)
data class SummaryCache(
    @PrimaryKey
    val id: String,                // "daily_2024-12-12" or "weekly_2024-12-08_2024-12-14"
    val type: String,              // "daily" or "weekly"
    val dateKey: String,           // The date being summarized (yyyy-MM-dd)
    val rangeStart: String? = null,// Weekly range start (yyyy-MM-dd) when type == weekly
    val rangeEnd: String? = null,  // Weekly range end (yyyy-MM-dd) when type == weekly
    val taskCount: Int? = null,    // Total tasks in the summary range
    val completedCount: Int? = null, // Completed tasks in the summary range
    val lastUpdatedAt: Long? = null, // Max updatedAt from tasks/strokes in range
    val contentHash: String = "",  // Hash of task content used to validate freshness
    val resultJson: String,        // Serialized DailySummary or WeeklySummary JSON
    val generatedAt: Long,         // Timestamp when generated (epoch millis)
    val validHours: Int            // How many hours the cache is valid
) {
    /**
     * Check if this cache entry is still valid.
     */
    fun isValid(): Boolean {
        val now = System.currentTimeMillis()
        val expiresAt = generatedAt + (validHours * 3600_000L)
        return now < expiresAt
    }

    /**
     * Get remaining validity time in minutes.
     */
    fun remainingMinutes(): Int {
        val now = System.currentTimeMillis()
        val expiresAt = generatedAt + (validHours * 3600_000L)
        val remaining = (expiresAt - now) / 60_000
        return remaining.coerceAtLeast(0).toInt()
    }

    companion object {
        const val TYPE_DAILY = "daily"
        const val TYPE_WEEKLY = "weekly"

        const val DAILY_VALID_HOURS = 4
        const val WEEKLY_VALID_HOURS = 20

        fun dailyId(date: String) = "daily_$date"
        fun weeklyId(startDate: String, endDate: String) = "weekly_${startDate}_$endDate"
    }
}

@Dao
interface SummaryCacheDao {
    @Query("SELECT * FROM summary_cache WHERE id = :id")
    fun get(id: String): SummaryCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(cache: SummaryCache)

    @Query("DELETE FROM summary_cache WHERE id = :id")
    fun delete(id: String)

    @Query("DELETE FROM summary_cache WHERE type = :type")
    fun deleteByType(type: String)

    /**
     * Delete all expired cache entries.
     * @param now Current timestamp in millis
     */
    @Query("DELETE FROM summary_cache WHERE (generatedAt + (validHours * 3600000)) < :now")
    fun deleteExpired(now: Long)

    /**
     * Get all cache entries of a specific type.
     */
    @Query("SELECT * FROM summary_cache WHERE type = :type ORDER BY generatedAt DESC")
    fun getAllByType(type: String): List<SummaryCache>

    /**
     * Clear all cache entries.
     */
    @Query("DELETE FROM summary_cache")
    fun clearAll()

    /**
     * Get all cache entries (for backup).
     */
    @Query("SELECT * FROM summary_cache")
    fun getAll(): List<SummaryCache>
}

class SummaryCacheRepository(context: Context) {
    private val dao = AppDatabase.getDatabase(context).summaryCacheDao()

    /**
     * Get a cached summary if valid, null otherwise.
     * Optionally validates against a content hash for freshness.
     */
    fun getValidCache(id: String, expectedContentHash: String? = null): SummaryCache? {
        val cache = dao.get(id) ?: return null
        if (!cache.isValid()) {
            // Auto-delete expired entry
            dao.delete(id)
            return null
        }
        if (expectedContentHash != null) {
            if (cache.contentHash.isBlank() || cache.contentHash != expectedContentHash) {
                dao.delete(id)
                return null
            }
        }
        return cache
    }

    /**
     * Save a summary to cache.
     */
    fun save(cache: SummaryCache) {
        dao.save(cache)
    }

    /**
     * Delete a specific cache entry.
     */
    fun delete(id: String) {
        dao.delete(id)
    }

    /**
     * Clean up all expired entries.
     */
    fun cleanupExpired() {
        dao.deleteExpired(System.currentTimeMillis())
    }

    /**
     * Clear all summary caches (for debugging/testing).
     */
    fun clearAll() {
        dao.clearAll()
    }
}

package com.boox.day.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Query
import androidx.room.Upsert

/**
 * Enum representing when a user typically takes notes relative to events.
 *
 * Used for personalized Smart Link matching - if a user often takes meeting notes
 * the next day (NEXT_DAY pattern), the system can boost cross-day matches for that domain.
 *
 * ## Phase 6 - SemanticGenius Transformation (Section 4.9)
 */
enum class NoteTimePreference {
    /** Note created during event (within event start/end time) */
    DURING,

    /** Note created within 1 hour after event ends */
    IMMEDIATELY_AFTER,

    /** Note created same day but >1 hour after event */
    SAME_DAY,

    /** Note created next day (morning follow-up pattern) */
    NEXT_DAY,

    /** Note created before event (preparation notes) */
    PREP_NOTES;

    companion object {
        /**
         * Get NoteTimePreference from string name.
         * Returns null if name doesn't match any enum value.
         */
        fun fromString(name: String): NoteTimePreference? =
            entries.find { it.name == name }
    }
}

/**
 * User Activity Pattern Entity (Phase 6 - SemanticGenius Transformation)
 *
 * Tracks when users typically take notes relative to events for each domain.
 * This enables personalized Smart Link matching by boosting cross-day matches
 * when the user has established a pattern of next-day or prep note taking.
 *
 * ## Example Usage
 * A user who consistently takes meeting notes the next morning will have:
 * - domain: "WORK"
 * - noteTimePreference: "NEXT_DAY"
 * - observationCount: 5
 * - averageDelayMs: ~14 hours
 *
 * Smart Link can then boost WORK domain events from yesterday by 0.15 when matching.
 *
 * ## Key Design Decisions
 * - Composite primary key (domain, noteTimePreference) to track patterns per domain/timing
 * - observationCount >= 3 required before pattern is used (prevents premature recognition)
 * - averageDelayMs tracks mean time between event end and note creation
 */
@Entity(
    tableName = "user_activity_pattern",
    primaryKeys = ["domain", "noteTimePreference"],
    indices = [
        Index(value = ["domain"]),
        Index(value = ["noteTimePreference"]),
        Index(value = ["observationCount"])
    ]
)
data class UserActivityPattern(
    /** Domain enum name (e.g., "WORK", "BUSINESS", "SCHOOL") */
    val domain: String,

    /** Note time preference enum name (e.g., "NEXT_DAY", "PREP_NOTES") */
    val noteTimePreference: String,

    /** How many times this pattern has been observed */
    val observationCount: Int,

    /** Timestamp of most recent observation */
    val lastObservedAt: Long,

    /** Average time between event end and note creation (milliseconds) */
    val averageDelayMs: Long
)

/**
 * DAO for user activity pattern operations.
 */
@Dao
interface UserActivityPatternDao {

    /**
     * Get a specific pattern by domain and preference.
     *
     * @param domain The domain enum name (e.g., "WORK")
     * @param noteTimePreference The timing preference enum name (e.g., "NEXT_DAY")
     * @return The pattern record, or null if no pattern exists
     */
    @Query("SELECT * FROM user_activity_pattern WHERE domain = :domain AND noteTimePreference = :noteTimePreference")
    suspend fun getPattern(domain: String, noteTimePreference: String): UserActivityPattern?

    /**
     * Get all patterns for a domain.
     *
     * @param domain The domain enum name
     * @return List of patterns for this domain, sorted by observation count
     */
    @Query("SELECT * FROM user_activity_pattern WHERE domain = :domain ORDER BY observationCount DESC")
    suspend fun getPatternsForDomain(domain: String): List<UserActivityPattern>

    /**
     * Get the dominant pattern for a domain (most observed).
     *
     * @param domain The domain enum name
     * @return The most frequently observed pattern, or null if no patterns exist
     */
    @Query("SELECT * FROM user_activity_pattern WHERE domain = :domain ORDER BY observationCount DESC LIMIT 1")
    suspend fun getDominantPattern(domain: String): UserActivityPattern?

    /**
     * Insert or update a pattern record.
     * Uses Room's @Upsert for atomic insert-or-update semantics.
     *
     * @param pattern The pattern record to upsert
     */
    @Upsert
    suspend fun upsert(pattern: UserActivityPattern)

    /**
     * Get all patterns with sufficient observations.
     * A pattern is considered "established" when observationCount >= 3.
     *
     * @param minObservations Minimum observation count (default 3)
     * @return List of established patterns
     */
    @Query("SELECT * FROM user_activity_pattern WHERE observationCount >= :minObservations ORDER BY observationCount DESC")
    suspend fun getEstablishedPatterns(minObservations: Int = 3): List<UserActivityPattern>

    /**
     * Get patterns by timing preference.
     * Useful for finding users who tend to take prep notes across all domains.
     *
     * @param noteTimePreference The timing preference to query
     * @return List of patterns with this preference
     */
    @Query("SELECT * FROM user_activity_pattern WHERE noteTimePreference = :noteTimePreference ORDER BY observationCount DESC")
    suspend fun getPatternsByPreference(noteTimePreference: String): List<UserActivityPattern>

    /**
     * Get total count of pattern records.
     * Useful for monitoring and debugging.
     */
    @Query("SELECT COUNT(*) FROM user_activity_pattern")
    suspend fun getPatternCount(): Int

    /**
     * Delete all patterns for a domain.
     * Useful for resetting user behavior data.
     *
     * @param domain The domain to clear
     * @return Number of records deleted
     */
    @Query("DELETE FROM user_activity_pattern WHERE domain = :domain")
    suspend fun clearPatternsForDomain(domain: String): Int

    /**
     * Delete all pattern records.
     * Use with caution - clears all learned user behavior.
     */
    @Query("DELETE FROM user_activity_pattern")
    suspend fun clearAllPatterns()
}

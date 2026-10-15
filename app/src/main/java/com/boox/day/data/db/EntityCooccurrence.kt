package com.boox.day.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Query
import androidx.room.Upsert

/**
 * Entity Co-occurrence Graph (Phase 5 - SemanticGenius Transformation)
 *
 * Tracks which entities (PERSON, ORG, LOCATION) appear together across notes/events
 * to enable contextual boosting in Smart Link scoring.
 *
 * Key design decisions:
 * - Composite primary key (entity1, entity2) with alphabetical ordering prevents duplicates
 * - Alphabetical ordering: entity1 < entity2 ensures "alice"+"bob" and "bob"+"alice"
 *   are stored as a single record ("alice", "bob")
 * - Separate indices on entity1, entity2, lastSeenAt for efficient lookups
 *
 * Co-occurrence scoring:
 * - Logarithmic scaling: 2 occurrences = 0.5x, 4 = 0.67x, 8 = 0.75x boost factor
 * - Maximum boost capped at COOCCURRENCE_WEIGHT (0.15)
 * - Only entities with 2+ co-occurrences contribute to scoring
 */
@Entity(
    tableName = "entity_cooccurrence",
    primaryKeys = ["entity1", "entity2"],
    indices = [
        Index(value = ["entity1"]),
        Index(value = ["entity2"]),
        Index(value = ["lastSeenAt"])
    ]
)
data class EntityCooccurrence(
    /** First entity (normalized lowercase, alphabetically smaller) */
    val entity1: String,

    /** Second entity (normalized lowercase, alphabetically larger) */
    val entity2: String,

    /** Number of times these entities appeared together */
    val cooccurrenceCount: Int,

    /** Timestamp of most recent co-occurrence */
    val lastSeenAt: Long,

    /** JSON array of source types where seen (e.g., ["BOOX_NOTE", "MEMO"]) */
    val sourceTypes: String
)

/**
 * DAO for entity co-occurrence operations.
 */
@Dao
interface EntityCooccurrenceDao {

    /**
     * Get all co-occurrences for a specific entity.
     * Queries both entity1 and entity2 columns since the entity could be in either position.
     *
     * @param entity The normalized (lowercase) entity text
     * @param limit Maximum number of results (default 20)
     * @return List of co-occurrences sorted by count (most frequent first)
     */
    @Query("""
        SELECT * FROM entity_cooccurrence
        WHERE entity1 = :entity OR entity2 = :entity
        ORDER BY cooccurrenceCount DESC
        LIMIT :limit
    """)
    suspend fun getCooccurrencesForEntity(entity: String, limit: Int = 20): List<EntityCooccurrence>

    /**
     * Get a specific co-occurrence record between two entities.
     * Handles both orderings since caller may not know which is alphabetically first.
     *
     * @param entityA First entity (any order)
     * @param entityB Second entity (any order)
     * @return The co-occurrence record, or null if entities have never co-occurred
     */
    @Query("""
        SELECT * FROM entity_cooccurrence
        WHERE (entity1 = :entityA AND entity2 = :entityB)
           OR (entity1 = :entityB AND entity2 = :entityA)
    """)
    suspend fun getCooccurrence(entityA: String, entityB: String): EntityCooccurrence?

    /**
     * Insert or update a co-occurrence record.
     * Uses Room's @Upsert for atomic insert-or-update semantics.
     *
     * @param cooccurrence The co-occurrence record (entity1 < entity2 alphabetically)
     */
    @Upsert
    suspend fun upsertCooccurrence(cooccurrence: EntityCooccurrence)

    /**
     * Remove old co-occurrence records that haven't been seen recently.
     * Used for periodic cleanup to prevent unbounded table growth.
     *
     * @param cutoff Epoch timestamp - records with lastSeenAt < cutoff will be deleted
     * @return Number of records deleted
     */
    @Query("DELETE FROM entity_cooccurrence WHERE lastSeenAt < :cutoff")
    suspend fun pruneOldCooccurrences(cutoff: Long): Int

    /**
     * Get total count of co-occurrence records.
     * Useful for monitoring table size.
     */
    @Query("SELECT COUNT(*) FROM entity_cooccurrence")
    suspend fun getCooccurrenceCount(): Int

    /**
     * Get co-occurrences with count above threshold.
     * Useful for finding strong entity relationships.
     *
     * @param minCount Minimum co-occurrence count
     * @param limit Maximum number of results
     */
    @Query("""
        SELECT * FROM entity_cooccurrence
        WHERE cooccurrenceCount >= :minCount
        ORDER BY cooccurrenceCount DESC
        LIMIT :limit
    """)
    suspend fun getFrequentCooccurrences(minCount: Int, limit: Int = 50): List<EntityCooccurrence>
}

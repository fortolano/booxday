package com.boox.day.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * LocalCalendar entity - Custom calendars for organizing local events in Standalone mode.
 *
 * Users can create up to 9 calendars (including the "Default" calendar) to organize
 * their local events into categories like "Work", "Family", "Church", etc.
 *
 * Key design decisions:
 * - id: UUID string, auto-generated
 * - isDefault: true only for the "Default" calendar (cannot be deleted/renamed)
 * - sortOrder: 0 for Default, 1-8 for user-created calendars
 * - colorHex: Optional calendar color for visual distinction in the UI
 *
 * See docs/LOCAL_CALENDARS_SPEC.md for full specification.
 */
@Entity(
    tableName = "local_calendars",
    indices = [Index(value = ["sortOrder"])]
)
data class LocalCalendar(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,                    // "Default", "Work", "Family", etc.
    val colorHex: String? = null,        // Optional color (e.g., "#FF5722")
    val sortOrder: Int,                  // 0 = Default, 1-8 = user-created
    val isDefault: Boolean = false,      // true only for "Default" calendar
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Create the Default calendar (should only be called once during migration or first launch).
         */
        fun createDefault(id: String = UUID.randomUUID().toString()): LocalCalendar = LocalCalendar(
            id = id,
            name = "Default",
            colorHex = null,
            sortOrder = 0,
            isDefault = true,
            createdAt = System.currentTimeMillis()
        )
    }
}

/**
 * Data Access Object for LocalCalendar entity.
 * Provides all database operations for local calendar management.
 */
@Dao
interface LocalCalendarDao {
    // ========== Basic Queries ==========

    /**
     * Get all calendars ordered by sortOrder (Default first, then user-created).
     */
    @Query("SELECT * FROM local_calendars ORDER BY sortOrder ASC")
    suspend fun getAll(): List<LocalCalendar>

    /**
     * Observe all calendars (reactive, for UI updates).
     */
    @Query("SELECT * FROM local_calendars ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<LocalCalendar>>

    /**
     * Get a calendar by ID.
     */
    @Query("SELECT * FROM local_calendars WHERE id = :id")
    suspend fun getById(id: String): LocalCalendar?

    /**
     * Get the Default calendar (there should always be exactly one).
     */
    @Query("SELECT * FROM local_calendars WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): LocalCalendar?

    /**
     * Get total count of calendars.
     */
    @Query("SELECT COUNT(*) FROM local_calendars")
    suspend fun getCount(): Int

    /**
     * Get the maximum sortOrder value (used when creating new calendars).
     */
    @Query("SELECT MAX(sortOrder) FROM local_calendars")
    suspend fun getMaxSortOrder(): Int?

    /**
     * Check if a calendar with the given name already exists.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM local_calendars WHERE name = :name COLLATE NOCASE)")
    suspend fun existsByName(name: String): Boolean

    // ========== Insert/Update/Delete ==========

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(calendar: LocalCalendar)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(calendars: List<LocalCalendar>)

    @Update
    suspend fun update(calendar: LocalCalendar)

    @Delete
    suspend fun delete(calendar: LocalCalendar)

    /**
     * Delete multiple calendars by their IDs.
     * Note: Should NOT include the Default calendar's ID.
     */
    @Query("DELETE FROM local_calendars WHERE id IN (:ids) AND isDefault = 0")
    suspend fun deleteByIds(ids: List<String>)

    /**
     * Delete all non-default calendars.
     */
    @Query("DELETE FROM local_calendars WHERE isDefault = 0")
    suspend fun deleteAllNonDefault()
}

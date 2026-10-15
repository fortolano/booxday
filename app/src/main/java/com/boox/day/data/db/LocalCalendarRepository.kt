package com.boox.day.data.db

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow

/**
 * Repository for LocalCalendar - provides clean API for local calendar management.
 *
 * This repository manages the custom local calendars feature for Standalone mode.
 * Users can create up to MAX_CALENDARS calendars to organize their local events.
 *
 * Key responsibilities:
 * - Ensure Default calendar always exists
 * - Enforce maximum calendar limit
 * - Handle calendar deletion (migrate events to Default)
 * - Provide reactive access to calendar list
 *
 * See docs/LOCAL_CALENDARS_SPEC.md for full specification.
 */
class LocalCalendarRepository(private val context: Context) {
    private val dao = AppDatabase.getDatabase(context).localCalendarDao()

    companion object {
        private const val TAG = "LocalCalendarRepo"

        /**
         * Maximum number of calendars allowed (including Default).
         * This provides a reasonable limit to avoid UI clutter.
         */
        const val MAX_CALENDARS = 9
    }

    // ========== Read Operations ==========

    /**
     * Get all calendars ordered by sortOrder.
     */
    suspend fun getAll(): List<LocalCalendar> = dao.getAll()

    /**
     * Observe all calendars (reactive, for UI updates).
     */
    fun observeAll(): Flow<List<LocalCalendar>> = dao.observeAll()

    /**
     * Get a calendar by ID.
     */
    suspend fun getById(id: String): LocalCalendar? = dao.getById(id)

    /**
     * Get the Default calendar.
     * Creates it if it doesn't exist (should only happen on first app launch or after data reset).
     */
    suspend fun getDefault(): LocalCalendar {
        return dao.getDefault() ?: createDefaultCalendar()
    }

    /**
     * Get the Default calendar ID (convenience method for event creation).
     */
    suspend fun getDefaultId(): String = getDefault().id

    /**
     * Check if more calendars can be created.
     */
    suspend fun canAddMore(): Boolean = dao.getCount() < MAX_CALENDARS

    /**
     * Get current calendar count.
     */
    suspend fun getCount(): Int = dao.getCount()

    /**
     * Get remaining slots available.
     */
    suspend fun getRemainingSlots(): Int = MAX_CALENDARS - dao.getCount()

    // ========== Create Operations ==========

    /**
     * Create a new user calendar.
     * @param name Calendar name (must be unique, case-insensitive)
     * @param colorHex Optional color for visual distinction
     * @return The created calendar
     * @throws IllegalStateException if maximum calendars reached
     * @throws IllegalArgumentException if name already exists or is empty
     */
    suspend fun create(name: String, colorHex: String? = null): LocalCalendar {
        val trimmedName = name.trim()

        // Validate
        require(trimmedName.isNotEmpty()) { "Calendar name cannot be empty" }
        check(dao.getCount() < MAX_CALENDARS) { "Maximum calendars reached ($MAX_CALENDARS)" }
        require(!dao.existsByName(trimmedName)) { "A calendar named '$trimmedName' already exists" }

        // Get next sort order
        val nextOrder = (dao.getMaxSortOrder() ?: 0) + 1

        val calendar = LocalCalendar(
            name = trimmedName,
            colorHex = colorHex,
            sortOrder = nextOrder,
            isDefault = false
        )

        dao.insert(calendar)
        Log.i(TAG, "CREATE: Calendar '${calendar.name}' created with id=${calendar.id}, sortOrder=$nextOrder")
        return calendar
    }

    // ========== Update Operations ==========

    /**
     * Update a calendar's properties.
     * Note: Cannot rename the Default calendar.
     * @throws IllegalArgumentException if trying to rename Default calendar
     */
    suspend fun update(calendar: LocalCalendar): LocalCalendar {
        val existing = dao.getById(calendar.id)

        // Prevent renaming Default calendar
        if (existing?.isDefault == true && calendar.name != "Default") {
            throw IllegalArgumentException("Cannot rename the Default calendar")
        }

        // Check for name collision (excluding self)
        if (calendar.name != existing?.name && dao.existsByName(calendar.name)) {
            throw IllegalArgumentException("A calendar named '${calendar.name}' already exists")
        }

        dao.update(calendar)
        Log.d(TAG, "UPDATE: Calendar '${calendar.name}' updated")
        return calendar
    }

    // ========== Delete Operations ==========

    /**
     * Delete calendars by IDs and migrate their events to Default.
     * The Default calendar cannot be deleted.
     * @param calendarIds IDs of calendars to delete
     * @param localEventRepository Repository to migrate events
     * @return Number of events migrated
     */
    suspend fun delete(calendarIds: List<String>, localEventRepository: LocalEventRepository): Int {
        if (calendarIds.isEmpty()) return 0

        // Get Default calendar (events will be migrated here)
        val defaultCalendar = getDefault()

        // Filter out Default calendar ID if somehow included
        val idsToDelete = calendarIds.filter { it != defaultCalendar.id }
        if (idsToDelete.isEmpty()) {
            Log.w(TAG, "DELETE: Attempted to delete Default calendar - ignored")
            return 0
        }

        // Migrate events to Default calendar
        val migratedCount = localEventRepository.migrateEventsToCalendar(idsToDelete, defaultCalendar.id)

        // Delete the calendars
        dao.deleteByIds(idsToDelete)

        Log.i(TAG, "DELETE: Deleted ${idsToDelete.size} calendars, migrated $migratedCount events to Default")
        return migratedCount
    }

    /**
     * Delete a single calendar by ID.
     * @param calendarId ID of calendar to delete
     * @param localEventRepository Repository to migrate events
     * @return Number of events migrated
     */
    suspend fun delete(calendarId: String, localEventRepository: LocalEventRepository): Int {
        return delete(listOf(calendarId), localEventRepository)
    }

    // ========== Initialization ==========

    /**
     * Ensure the Default calendar exists.
     * Should be called on app startup or after database migration.
     */
    suspend fun ensureDefaultExists() {
        if (dao.getDefault() == null) {
            createDefaultCalendar()
        }
    }

    /**
     * Create the Default calendar.
     * @return The created Default calendar
     */
    private suspend fun createDefaultCalendar(): LocalCalendar {
        val default = LocalCalendar.createDefault()
        dao.insert(default)
        Log.i(TAG, "INIT: Created Default calendar with id=${default.id}")
        return default
    }
}

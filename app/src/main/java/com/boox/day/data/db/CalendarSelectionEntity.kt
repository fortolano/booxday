package com.boox.day.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.boox.day.data.model.CalendarSelection
import kotlinx.coroutines.flow.Flow

/**
 * Room entity representing a user's selection settings for a specific calendar.
 * Has a foreign key relationship with ConnectedAccountEntity - CASCADE on delete.
 *
 * @see CalendarSelection for the domain model
 */
@Entity(
    tableName = "calendar_selections",
    foreignKeys = [
        ForeignKey(
            entity = ConnectedAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["accountId", "calendarId"], unique = true)
    ]
)
data class CalendarSelectionEntity(
    @PrimaryKey
    val id: String,
    val accountId: String,
    val calendarId: String,
    val calendarName: String,
    val calendarColor: String?,
    val isFetchEnabled: Boolean = false,
    val isDefaultForAccount: Boolean = false,
    val displayOrder: Int = 0
) {
    /**
     * Convert entity to domain model.
     */
    fun toDomainModel(): CalendarSelection = CalendarSelection(
        id = id,
        accountId = accountId,
        calendarId = calendarId,
        calendarName = calendarName,
        calendarColor = calendarColor,
        isFetchEnabled = isFetchEnabled,
        isDefaultForAccount = isDefaultForAccount,
        displayOrder = displayOrder
    )

    companion object {
        /**
         * Create entity from domain model.
         */
        fun fromDomainModel(selection: CalendarSelection): CalendarSelectionEntity =
            CalendarSelectionEntity(
                id = selection.id,
                accountId = selection.accountId,
                calendarId = selection.calendarId,
                calendarName = selection.calendarName,
                calendarColor = selection.calendarColor,
                isFetchEnabled = selection.isFetchEnabled,
                isDefaultForAccount = selection.isDefaultForAccount,
                displayOrder = selection.displayOrder
            )
    }
}

/**
 * DAO for calendar selections.
 */
@Dao
interface CalendarSelectionDao {
    /**
     * Get all calendar selections for an account.
     */
    @Query("SELECT * FROM calendar_selections WHERE accountId = :accountId ORDER BY displayOrder ASC")
    suspend fun getByAccountId(accountId: String): List<CalendarSelectionEntity>

    /**
     * Get all calendar selections for an account as a reactive Flow.
     */
    @Query("SELECT * FROM calendar_selections WHERE accountId = :accountId ORDER BY displayOrder ASC")
    fun getByAccountIdFlow(accountId: String): Flow<List<CalendarSelectionEntity>>

    /**
     * Get all calendars that have sync enabled (across all accounts).
     */
    @Query("SELECT * FROM calendar_selections WHERE isFetchEnabled = 1 ORDER BY accountId, displayOrder")
    suspend fun getEnabledCalendars(): List<CalendarSelectionEntity>

    /**
     * Get the default calendar for an account.
     */
    @Query("SELECT * FROM calendar_selections WHERE accountId = :accountId AND isDefaultForAccount = 1 LIMIT 1")
    suspend fun getDefaultCalendar(accountId: String): CalendarSelectionEntity?

    /**
     * Get a specific calendar selection.
     */
    @Query("SELECT * FROM calendar_selections WHERE id = :selectionId")
    suspend fun getById(selectionId: String): CalendarSelectionEntity?

    /**
     * Get calendar selection by account and provider's calendar ID.
     */
    @Query("SELECT * FROM calendar_selections WHERE accountId = :accountId AND calendarId = :calendarId")
    suspend fun getByAccountAndCalendarId(accountId: String, calendarId: String): CalendarSelectionEntity?

    /**
     * Insert or update all selections for an account.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(selections: List<CalendarSelectionEntity>)

    /**
     * Insert or update a single selection.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(selection: CalendarSelectionEntity)

    /**
     * Delete all selections for an account.
     * Note: This is also triggered by CASCADE when account is deleted.
     */
    @Query("DELETE FROM calendar_selections WHERE accountId = :accountId")
    suspend fun deleteByAccountId(accountId: String)

    /**
     * Clear default flag for all calendars in an account.
     */
    @Query("UPDATE calendar_selections SET isDefaultForAccount = 0 WHERE accountId = :accountId")
    suspend fun clearDefaultForAccount(accountId: String)

    /**
     * Set a specific calendar as the default for its account.
     */
    @Query("UPDATE calendar_selections SET isDefaultForAccount = 1 WHERE id = :selectionId")
    suspend fun setAsDefaultById(selectionId: String)

    /**
     * Toggle fetch enabled for a calendar.
     */
    @Query("UPDATE calendar_selections SET isFetchEnabled = :enabled WHERE id = :selectionId")
    suspend fun setFetchEnabled(selectionId: String, enabled: Boolean)

    /**
     * Update fetch enabled by account and provider's calendar ID.
     */
    @Query("UPDATE calendar_selections SET isFetchEnabled = :enabled WHERE accountId = :accountId AND calendarId = :calendarId")
    suspend fun setFetchEnabledByCalendarId(accountId: String, calendarId: String, enabled: Boolean)

    /**
     * Set a calendar as default (clears other defaults first).
     * Uses a transaction to ensure atomicity.
     */
    @Transaction
    suspend fun setAsDefault(accountId: String, selectionId: String) {
        clearDefaultForAccount(accountId)
        setAsDefaultById(selectionId)
    }

    /**
     * Count calendars for an account.
     */
    @Query("SELECT COUNT(*) FROM calendar_selections WHERE accountId = :accountId")
    suspend fun countForAccount(accountId: String): Int

    /**
     * Count enabled calendars for an account.
     */
    @Query("SELECT COUNT(*) FROM calendar_selections WHERE accountId = :accountId AND isFetchEnabled = 1")
    suspend fun countEnabledForAccount(accountId: String): Int

    /**
     * Delete a specific calendar selection by ID.
     */
    @Query("DELETE FROM calendar_selections WHERE id = :selectionId")
    suspend fun deleteById(selectionId: String)

    /**
     * Delete calendar selections by calendar IDs (for cleanup of externally deleted calendars).
     */
    @Query("DELETE FROM calendar_selections WHERE accountId = :accountId AND calendarId IN (:calendarIds)")
    suspend fun deleteByCalendarIds(accountId: String, calendarIds: List<String>)
}

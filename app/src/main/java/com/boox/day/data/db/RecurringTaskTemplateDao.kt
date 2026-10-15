package com.boox.day.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface RecurringTaskTemplateDao {
    @Query("SELECT * FROM recurring_task_template WHERE id = :templateId")
    fun getById(templateId: String): RecurringTaskTemplate?

    @Query("SELECT * FROM recurring_task_template WHERE id IN (:templateIds)")
    fun getByIds(templateIds: List<String>): List<RecurringTaskTemplate>

    @Query("SELECT * FROM recurring_task_template ORDER BY createdAt ASC")
    fun getAll(): List<RecurringTaskTemplate>

    @Query("SELECT * FROM recurring_task_template WHERE isActive = 1 ORDER BY createdAt ASC")
    fun getActiveTemplates(): List<RecurringTaskTemplate>

    @Query(
        "SELECT * FROM recurring_task_template " +
            "WHERE isActive = 1 AND anchorDate <= :date AND (endDate IS NULL OR endDate >= :date) " +
            "ORDER BY createdAt ASC"
    )
    fun getActiveTemplatesForDate(date: String): List<RecurringTaskTemplate>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(template: RecurringTaskTemplate)

    /**
     * Insert or update in place. Use this for edits: REPLACE deletes the old row first, and
     * ON DELETE CASCADE then wipes the series' skip/move exceptions (plan T4, Oct 2026).
     */
    @Upsert
    fun upsert(template: RecurringTaskTemplate)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(templates: List<RecurringTaskTemplate>)

    @Delete
    fun delete(template: RecurringTaskTemplate)

    @Query("UPDATE recurring_task_template SET isActive = :isActive, updatedAt = :updatedAt WHERE id = :templateId")
    fun setActive(templateId: String, isActive: Boolean, updatedAt: Long = System.currentTimeMillis())
}

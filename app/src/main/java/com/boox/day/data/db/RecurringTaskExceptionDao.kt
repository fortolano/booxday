package com.boox.day.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RecurringTaskExceptionDao {
    @Query("SELECT * FROM recurring_task_exception WHERE templateId = :templateId AND occurrenceDate = :occurrenceDate LIMIT 1")
    fun getForOccurrence(templateId: String, occurrenceDate: String): RecurringTaskException?

    @Query("SELECT EXISTS(SELECT 1 FROM recurring_task_exception WHERE templateId = :templateId AND occurrenceDate = :occurrenceDate)")
    fun existsForOccurrence(templateId: String, occurrenceDate: String): Boolean

    @Query("SELECT * FROM recurring_task_exception WHERE occurrenceDate = :occurrenceDate")
    fun getForDate(occurrenceDate: String): List<RecurringTaskException>

    // Get all exceptions (for backup export)
    @Query("SELECT * FROM recurring_task_exception")
    fun getAll(): List<RecurringTaskException>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(exception: RecurringTaskException)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(exceptions: List<RecurringTaskException>)

    @Delete
    fun delete(exception: RecurringTaskException)

    @Query("DELETE FROM recurring_task_exception WHERE templateId = :templateId")
    fun deleteForTemplate(templateId: String)
}

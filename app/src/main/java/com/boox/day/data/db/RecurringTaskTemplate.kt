package com.boox.day.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aventrix.jnanoid.jnanoid.NanoIdUtils

@Entity(
    tableName = "recurring_task_template",
    indices = [
        Index("isActive"),
        Index("anchorDate"),
        Index("endDate")
    ]
)
data class RecurringTaskTemplate(
    @PrimaryKey val id: String = NanoIdUtils.randomNanoId(),
    val title: String,
    val defaultPriority: String = "",
    val defaultEstimatedMinutes: Int? = null,
    val recurrenceType: String,
    val daysOfWeek: String? = null,
    val dayOfMonth: Int? = null,
    val intervalDays: Int = 1,
    val anchorDate: String,
    val endDate: String? = null,
    val isActive: Boolean = true,
    val isHabit: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

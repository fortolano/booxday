package com.boox.day.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aventrix.jnanoid.jnanoid.NanoIdUtils

@Entity(
    tableName = "recurring_task_exception",
    foreignKeys = [
        ForeignKey(
            entity = RecurringTaskTemplate::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("templateId"),
        Index("occurrenceDate"),
        Index(value = ["templateId", "occurrenceDate"], unique = true)
    ]
)
data class RecurringTaskException(
    @PrimaryKey val id: String = NanoIdUtils.randomNanoId(),
    val templateId: String,
    val occurrenceDate: String,
    val exceptionType: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

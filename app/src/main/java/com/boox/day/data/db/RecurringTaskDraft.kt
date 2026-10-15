package com.boox.day.data.db

import kotlinx.serialization.json.Json

/**
 * Lightweight recurrence settings passed between the UI and repository.
 * This keeps Compose code decoupled from the Room entity shape.
 */
data class RecurringTaskDraft(
    val recurrenceType: String = "DAILY",
    val anchorDate: String,
    val daysOfWeek: List<Int> = emptyList(),
    val dayOfMonth: Int? = null,
    val intervalDays: Int = 1,
    val endDate: String? = null,
    val isHabit: Boolean = false
) {
    companion object {
        fun fromTemplate(template: RecurringTaskTemplate): RecurringTaskDraft {
            val weekdays = template.daysOfWeek?.let { raw ->
                try {
                    Json.decodeFromString<List<Int>>(raw)
                        .filter { it in 1..7 }
                        .distinct()
                        .sorted()
                } catch (_: Exception) {
                    emptyList()
                }
            } ?: emptyList()

            return RecurringTaskDraft(
                recurrenceType = template.recurrenceType,
                anchorDate = template.anchorDate,
                daysOfWeek = weekdays,
                dayOfMonth = template.dayOfMonth,
                intervalDays = template.intervalDays,
                endDate = template.endDate,
                isHabit = template.isHabit
            )
        }
    }
}

data class SaveRecurringTaskTemplateResult(
    val template: RecurringTaskTemplate? = null,
    val blockedReason: String? = null
) {
    val isSuccess: Boolean
        get() = template != null && blockedReason == null
}

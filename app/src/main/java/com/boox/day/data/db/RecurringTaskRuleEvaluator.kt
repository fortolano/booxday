package com.boox.day.data.db

import java.time.LocalDate
import kotlinx.serialization.json.Json

object RecurringTaskRuleEvaluator {

    fun shouldGenerateOnDate(template: RecurringTaskTemplate, date: String): Boolean {
        val targetDate = try {
            LocalDate.parse(date)
        } catch (_: Exception) {
            return false
        }
        return shouldGenerateOnDate(template, targetDate)
    }

    fun shouldGenerateOnDate(template: RecurringTaskTemplate, date: LocalDate): Boolean {
        if (!template.isActive) return false

        val anchorDate = try {
            LocalDate.parse(template.anchorDate)
        } catch (_: Exception) {
            return false
        }

        if (date.isBefore(anchorDate)) return false

        val endDate = template.endDate?.let {
            try {
                LocalDate.parse(it)
            } catch (_: Exception) {
                return false
            }
        }
        if (endDate != null && date.isAfter(endDate)) return false

        return when (template.recurrenceType) {
            "DAILY" -> {
                val intervalDays = template.intervalDays.coerceAtLeast(1)
                val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(anchorDate, date)
                daysBetween % intervalDays == 0L
            }

            "WEEKLY" -> date.dayOfWeek == anchorDate.dayOfWeek

            "CUSTOM_WEEKDAYS" -> {
                val weekdays = parseDaysOfWeek(template.daysOfWeek)
                weekdays.contains(date.dayOfWeek.value)
            }

            "MONTHLY" -> {
                val dayOfMonth = template.dayOfMonth ?: return false
                dayOfMonth in 1..28 && date.dayOfMonth == dayOfMonth
            }

            else -> false
        }
    }

    private fun parseDaysOfWeek(rawValue: String?): Set<Int> {
        if (rawValue.isNullOrBlank()) return emptySet()
        return try {
            Json.decodeFromString<List<Int>>(rawValue)
                .filter { it in 1..7 }
                .toSortedSet()
        } catch (_: Exception) {
            emptySet()
        }
    }
}

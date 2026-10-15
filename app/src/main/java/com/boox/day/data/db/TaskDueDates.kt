package com.boox.day.data.db

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * One definition of task due dates (plan T2, Oct 2026; DB v27).
 *
 * A task is due on a local calendar day stored as "yyyy-MM-dd" ([Task.dueDay]).
 * The day never shifts with DST or time-zone changes, and a task is overdue only from the day
 * AFTER its due day (it used to show overdue from 00:00 on the due day itself).
 * ISO day strings compare correctly as plain strings.
 */
object TaskDueDates {

    /** Today as "yyyy-MM-dd" in the device's current zone. */
    fun today(): String = LocalDate.now().toString()

    /** Overdue = due on a day before [today] and not completed. */
    fun isOverdue(dueDay: String?, isCompleted: Boolean = false, today: String = today()): Boolean =
        dueDay != null && !isCompleted && dueDay < today

    /** Due today (and not completed). */
    fun isDueToday(dueDay: String?, isCompleted: Boolean = false, today: String = today()): Boolean =
        dueDay != null && !isCompleted && dueDay == today

    fun toLocalDate(dueDay: String?): LocalDate? =
        dueDay?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** Day of an epoch-ms instant in the current zone (date pickers and the ≤ v26 column). */
    fun fromMillis(epochMillis: Long?): String? =
        epochMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString() }

    /** Start of [dueDay] in the current zone, for UI that still works in epoch ms. */
    fun startOfDayMillis(dueDay: String?): Long? =
        toLocalDate(dueDay)?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()

    /** Whole days from [from] to [dueDay] (negative when overdue), or null. */
    fun daysUntil(dueDay: String?, from: LocalDate = LocalDate.now()): Long? =
        toLocalDate(dueDay)?.let { ChronoUnit.DAYS.between(from, it) }
}

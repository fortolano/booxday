package com.boox.day.ui.calendar

import androidx.compose.runtime.Immutable

/**
 * Immutable wrapper for calendar badge data to prevent unnecessary recompositions of CalendarGrid.
 * Compose considers Set<T> unstable, so wrapping it in an @Immutable class ensures stability.
 */
@Immutable
data class CalendarBadges(
    val datesWithNotes: Set<String> = emptySet(),
    val datesWithEvents: Set<String> = emptySet(),
    val datesWithBooxNotes: Set<String> = emptySet(),
    val datesWithTasks: Set<String> = emptySet()
)

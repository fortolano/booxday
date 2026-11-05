package com.boox.day.week

import androidx.compose.ui.graphics.ImageBitmap
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

/** Turns a loaded week into what the page draws. Pure. */
object WeekPresenter {
    const val HANDWRITTEN = "Handwritten task"

    fun page(
        data: WeekData,
        today: LocalDate,
        selected: LocalDate,
        firstDay: DayOfWeek,
        dayFirst: Boolean,
        ink: Map<String, ImageBitmap> = emptyMap(),
        locale: Locale = Locale.getDefault()
    ): WeekPageData {
        val end = data.start.plusDays(6)
        // The tablet region's rule, as the month grid's week numbers and the sleep screen count
        val week = data.start.get(WeekFields.of(firstDay, WeekFields.of(locale).minimalDaysInFirstWeek).weekOfWeekBasedYear())
        return WeekPageData(
            rangeLong = "${range(data.start, end, dayFirst, withYear = true, locale)} · Week $week",
            rangeShort = range(data.start, end, dayFirst, withYear = false, locale),
            isThisWeek = today in data.start..end,
            cells = data.days.map { day ->
                WeekCell(
                    title = DateTimeFormatter.ofPattern("EEE d", locale).format(day.date),
                    isToday = day.date == today,
                    isSelected = day.date == selected,
                    doneCount = day.finished.size,
                    lines = day.events.map { WeekLine(it.event.title, minute = it.minute, allDay = it.allDay) } +
                        day.tasks.map { taskLine(it, ink) }
                )
            } + WeekCell(
                title = "Overdue",
                count = data.overdue.size.takeIf { it > 0 },
                lines = data.overdue.map { taskLine(it, ink) }
            )
        )
    }

    private fun taskLine(item: WeekTaskItem, ink: Map<String, ImageBitmap>): WeekLine {
        val picture = item.task?.id?.let { ink[it] }
        return WeekLine(
            text = item.text ?: HANDWRITTEN,
            ink = picture.takeIf { item.text == null },
            mark = when {
                item.due -> WeekMark.DUE
                item.repeat -> WeekMark.REPEATS
                else -> null
            }
        )
    }

    /** "Nov 2 – 8, 2026", "Oct 26 – Nov 1, 2026", "Dec 28, 2026 – Jan 3, 2027"; "2 – 8 Nov 2026" day first. */
    fun range(start: LocalDate, end: LocalDate, dayFirst: Boolean, withYear: Boolean, locale: Locale = Locale.getDefault()): String {
        val month = DateTimeFormatter.ofPattern("MMM", locale)
        val sameMonth = start.month == end.month && start.year == end.year
        val sameYear = start.year == end.year
        return if (dayFirst) {
            val left = if (sameMonth) "${start.dayOfMonth}" else "${start.dayOfMonth} ${month.format(start)}${if (sameYear || !withYear) "" else " ${start.year}"}"
            "$left – ${end.dayOfMonth} ${month.format(end)}${if (withYear) " ${end.year}" else ""}"
        } else {
            val left = "${month.format(start)} ${start.dayOfMonth}${if (sameYear || !withYear) "" else ", ${start.year}"}"
            val right = if (sameMonth) "${end.dayOfMonth}" else "${month.format(end)} ${end.dayOfMonth}"
            "$left – $right${if (withYear) ", ${end.year}" else ""}"
        }
    }
}

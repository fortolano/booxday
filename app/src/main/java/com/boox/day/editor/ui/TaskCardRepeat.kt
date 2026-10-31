package com.boox.day.editor.ui

import com.boox.day.data.db.RecurringTaskDraft
import com.boox.day.data.db.RecurringTaskTemplate
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * The task card's Repeat screen (31 Oct 2026, task dialogs review option B): one list of choices
 * instead of the old Off/On + Type toggles. Pure, so the series it saves can be tested on the JVM.
 */
enum class RepeatChoice { OFF, DAILY, WEEKLY, SOME_DAYS, MONTHLY }

/** What the Repeat screen shows and edits; saved only through [toDraft]. */
data class RepeatForm(
    val choice: RepeatChoice,
    val start: LocalDate,
    /** ISO weekdays (1 = Monday) for [RepeatChoice.SOME_DAYS]. */
    val days: List<Int>,
    val end: LocalDate?,
    /** Kept from an older "every N days" series; the screen offers only every day for new ones. */
    val intervalDays: Int = 1,
    /** Monthly day; the series supports 1 to 28 only, so a start on the 29th to 31st uses the 28th. */
    val dayOfMonth: Int = monthlyDay(start)
) {
    fun withStart(newStart: LocalDate): RepeatForm = copy(
        start = newStart,
        dayOfMonth = monthlyDay(newStart),
        days = days.ifEmpty { listOf(newStart.dayOfWeek.value) }
    )

    /** The series to save, or null for Off (nothing to save; an existing series is stopped instead). */
    fun toDraft(): RecurringTaskDraft? {
        val type = when (choice) {
            RepeatChoice.OFF -> return null
            RepeatChoice.DAILY -> "DAILY"
            RepeatChoice.WEEKLY -> "WEEKLY"
            RepeatChoice.SOME_DAYS -> "CUSTOM_WEEKDAYS"
            RepeatChoice.MONTHLY -> "MONTHLY"
        }
        return RecurringTaskDraft(
            recurrenceType = type,
            anchorDate = start.toString(),
            daysOfWeek = days.distinct().sorted(),
            dayOfMonth = if (choice == RepeatChoice.MONTHLY) dayOfMonth else null,
            intervalDays = if (choice == RepeatChoice.DAILY) intervalDays.coerceAtLeast(1) else 1,
            endDate = end?.toString()
        )
    }

    companion object {
        /** The form for a task: its series, or Off starting on the task's own day. */
        fun of(template: RecurringTaskTemplate?, taskDay: LocalDate): RepeatForm {
            if (template == null) {
                return RepeatForm(RepeatChoice.OFF, taskDay, listOf(taskDay.dayOfWeek.value), null)
            }
            val draft = RecurringTaskDraft.fromTemplate(template)
            val start = runCatching { LocalDate.parse(draft.anchorDate) }.getOrDefault(taskDay)
            val choice = when (template.recurrenceType.uppercase()) {
                "DAILY" -> RepeatChoice.DAILY
                "WEEKLY" -> RepeatChoice.WEEKLY
                "CUSTOM_WEEKDAYS" -> RepeatChoice.SOME_DAYS
                "MONTHLY" -> RepeatChoice.MONTHLY
                else -> RepeatChoice.DAILY
            }
            return RepeatForm(
                choice = choice,
                start = start,
                days = draft.daysOfWeek.ifEmpty { listOf(start.dayOfWeek.value) },
                end = draft.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                intervalDays = draft.intervalDays.coerceAtLeast(1),
                dayOfMonth = draft.dayOfMonth?.coerceIn(1, 28) ?: monthlyDay(start)
            )
        }
    }
}

fun monthlyDay(start: LocalDate): Int = start.dayOfMonth.coerceAtMost(28)

/** Words for one choice, as the Repeat screen and the card's Repeat line show them. */
fun repeatChoiceLabel(choice: RepeatChoice, form: RepeatForm, locale: Locale = Locale.getDefault()): String = when (choice) {
    RepeatChoice.OFF -> "Off"
    RepeatChoice.DAILY -> if (form.intervalDays > 1) "Every ${form.intervalDays} days" else "Every day"
    RepeatChoice.WEEKLY -> "Every week on ${form.start.dayOfWeek.getDisplayName(TextStyle.FULL, locale)}"
    RepeatChoice.SOME_DAYS -> "On some days"
    RepeatChoice.MONTHLY -> "Every month on the ${ordinal(form.dayOfMonth)}"
}

/** The card's Repeat line: the series in the Repeat screen's words, "(paused)" when paused. */
fun taskRepeatLabel(template: RecurringTaskTemplate, taskDay: LocalDate = LocalDate.now()): String {
    val form = RepeatForm.of(template, taskDay)
    val base = repeatChoiceLabel(form.choice, form)
    return if (template.isActive) base else "$base (paused)"
}

internal fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "$n$suffix"
}

package com.boox.day.proposals

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Something the AI found, for the user to check. Nothing is made from it until they tap (Nov 2026). */
sealed interface Proposal {
    val id: String
    val accepted: Boolean
    /** The bold line asking for a look, such as "Check the date". */
    val needsCheck: String?

    data class Event(
        override val id: String,
        val title: String,
        val date: String?,
        val startMinute: Int?,
        val endMinute: Int?,
        val allDay: Boolean,
        val location: String?,
        override val accepted: Boolean = true,
        override val needsCheck: String? = null
    ) : Proposal

    data class Task(
        override val id: String,
        val text: String,
        val day: String?,
        val dueDay: String?,
        val reminderMinute: Int?,
        override val accepted: Boolean = true,
        override val needsCheck: String? = null
    ) : Proposal

    /** Everything that was shared, kept as a memo. Unticked until the user wants it. */
    data class Note(
        override val id: String = "shared-note",
        override val accepted: Boolean = false,
        override val needsCheck: String? = null
    ) : Proposal
}

/** One item as the AI answers it (`schedule_items`): "" means none. */
data class ScheduleItemAnswer(
    val kind: String = "",
    val title: String = "",
    val date: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val allDay: Boolean = false,
    val location: String = "",
    val isDeadline: Boolean = false,
    val sourceText: String = "",
    val confidence: String = ""
)

/** Checks an AI answer against what was written. Pure; the memo's day is the anchor, not today. */
object ProposalValidator {
    const val CHECK_DATE = "Check the date"
    private const val DEFAULT_LENGTH_MINUTES = 60
    private const val LAST_MINUTE = 24 * 60 - 1
    private val slashDate = Regex("""\b\d{1,2}/\d{1,2}(/\d{2,4})?\b""")

    private val weekdays: Map<Regex, DayOfWeek> = mapOf(
        "mon(day)?" to DayOfWeek.MONDAY,
        "tue(s|sday)?" to DayOfWeek.TUESDAY,
        "wed(s|nesday)?" to DayOfWeek.WEDNESDAY,
        "thu(r|rs|rsday)?" to DayOfWeek.THURSDAY,
        "fri(day)?" to DayOfWeek.FRIDAY,
        "sat(urday)?" to DayOfWeek.SATURDAY,
        "sun(day)?" to DayOfWeek.SUNDAY
    ).mapKeys { Regex("""\b${it.key}\b""", RegexOption.IGNORE_CASE) }

    fun check(answer: ScheduleItemAnswer, id: String, memoDay: LocalDate, today: LocalDate): Proposal {
        val date = runCatching { LocalDate.parse(answer.date.trim()) }.getOrNull()
        val start = minuteOf(answer.startTime)
        val check = dateCheck(date, answer.sourceText, today)
        val day = (date ?: memoDay).toString()
        val title = answer.title.trim()
        if (answer.kind.trim().equals("task", ignoreCase = true)) {
            return Proposal.Task(
                id = id,
                text = title,
                day = day,
                // A written date is a deadline when it says so; a written time makes one too, for the reminder
                dueDay = date?.takeIf { answer.isDeadline || start != null }?.toString(),
                reminderMinute = start.takeIf { date != null },
                needsCheck = check.takeIf { date != null }
            )
        }
        val timed = start != null && !answer.allDay
        return Proposal.Event(
            id = id,
            title = title,
            date = day,
            startMinute = start.takeIf { timed },
            endMinute = if (timed) endOf(start!!, minuteOf(answer.endTime)) else null,
            allDay = !timed,
            location = answer.location.trim().ifBlank { null },
            needsCheck = check ?: CHECK_DATE.takeIf { date == null }
        )
    }

    /** A proper end: a written one after the start, else the default length, within the day. */
    fun endOf(start: Int, end: Int?): Int =
        if (end != null && end > start) end else minOf(start + DEFAULT_LENGTH_MINUTES, LAST_MINUTE)

    private fun dateCheck(date: LocalDate?, source: String, today: LocalDate): String? {
        if (date == null) return null
        val written = weekdays.entries.firstOrNull { it.key.containsMatchIn(source) }?.value
        return when {
            written != null && written != date.dayOfWeek ->
                "$CHECK_DATE: you wrote ${written.getDisplayName(TextStyle.FULL, Locale.getDefault())}"
            date.isBefore(today) -> "$CHECK_DATE: it's in the past"
            // 12/11 reads as two dates, and which one depends on the region
            slashDate.containsMatchIn(source) -> CHECK_DATE
            else -> null
        }
    }

    /** "13:05" to minutes of the day; anything else is null. */
    fun minuteOf(time: String): Int? {
        val parts = time.trim().split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
    }

    /** The events on the proposal's day that it runs into; all-day ones don't count. */
    fun overlapping(event: Proposal.Event, others: List<Triple<String, Int, Int>>): String? {
        val start = event.startMinute ?: return null
        val end = event.endMinute ?: return null
        return others.firstOrNull { (_, otherStart, otherEnd) -> start < otherEnd && otherStart < end }?.first
    }

    /** The day table for the prompt, so the model reads names instead of doing date sums. */
    fun dayTable(anchor: LocalDate, days: Int = 14): String {
        val format = DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd", Locale.ENGLISH)
        return (0 until days).joinToString("\n") { format.format(anchor.plusDays(it.toLong())) }
    }

    fun toggled(proposal: Proposal): Proposal = when (proposal) {
        is Proposal.Event -> Proposal.Task(
            id = proposal.id,
            text = proposal.title,
            day = proposal.date,
            dueDay = proposal.date.takeIf { proposal.startMinute != null },
            reminderMinute = proposal.startMinute,
            accepted = proposal.accepted,
            needsCheck = proposal.needsCheck
        )
        is Proposal.Task -> {
            val start = proposal.reminderMinute
            Proposal.Event(
                id = proposal.id,
                title = proposal.text,
                date = proposal.dueDay ?: proposal.day,
                startMinute = start,
                endMinute = start?.let { endOf(it, null) },
                allDay = start == null,
                location = null,
                accepted = proposal.accepted,
                needsCheck = proposal.needsCheck
            )
        }
        is Proposal.Note -> proposal
    }
}

package com.boox.day.actionitems

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Which memo lines are things to do (action items, Oct 2026). Reads one written line at a time, as
 * recognized on the tablet. English only.
 *
 * A line counts when it has a to-do mark — an empty box ("[ ]", "☐") or a label ("TODO:", "Action:",
 * "Next steps:") — or, after any dash, bullet or number, starts with a doing word ("call", "send",
 * "follow up"), is "Name: doing word…" ("Mike: send the deck"), or says "need to", "remember to",
 * "don't forget"…. A ticked box ("[x]", "☑") is done and never counts. A plain bullet alone doesn't
 * count: meeting notes are mostly bulleted facts.
 *
 * Also reads the day a line names: today, tomorrow, a weekday ("by Friday", "next Tue"), "Oct 12" /
 * "12 Oct", or 10/12 (month/day) — counted from the memo's day, the day it was written for.
 */
object ActionItemRules {

    data class Found(val text: String, val dueDay: String?)

    /** [memoDay]: the day the memo is for ("tomorrow" on Oct 17's memo is Oct 18). */
    fun find(line: String, memoDay: LocalDate): Found? {
        var text = line.trim().replace(WHITESPACE, " ")
        var marked = false
        // Leading marks in any order ("- [ ] call", "[ ] 1. call"); a ticked box anywhere among them is done
        while (true) {
            if (DONE_BOX.containsMatchIn(text)) return null
            val box = OPEN_BOX.find(text)
            val bullet = BULLET.find(text)
            text = when {
                box != null -> text.substring(box.range.last + 1).trim().also { marked = true }
                bullet != null -> text.substring(bullet.range.last + 1).trim()
                else -> break
            }
        }
        LABEL.find(text)?.let { label ->
            marked = true
            text = text.substring(label.range.last + 1).trim()
        }
        if (text.length < 3 || text.none { it.isLetter() }) return null

        val isAction = marked || startsWithDoingWord(text) || assignedDoingWord(text) || MODAL.containsMatchIn(text)
        if (!isAction) return null
        val clean = text.trimEnd('.', ';', ',').take(MAX_LENGTH).replaceFirstChar { it.uppercaseChar() }
        return Found(clean, dueDay(clean, memoDay))
    }

    /** The day a line names (yyyy-MM-dd), or null. An explicit date wins over a weekday, then tomorrow, then today. */
    fun dueDay(text: String, memoDay: LocalDate): String? {
        val lower = text.lowercase()
        explicitDate(lower, memoDay)?.let { return it.toString() }
        WEEKDAY.find(lower)?.let { match -> weekday(match.groupValues[2])?.let { return onOrAfter(memoDay, it, next = match.groupValues[1].isNotEmpty()).toString() } }
        SHORT_WEEKDAY.find(lower)?.let { match -> weekday(match.groupValues[2])?.let { return onOrAfter(memoDay, it, next = match.groupValues[1] == "next").toString() } }
        if (TOMORROW.containsMatchIn(lower)) return memoDay.plusDays(1).toString()
        if (TODAY.containsMatchIn(lower)) return memoDay.toString()
        return null
    }

    /** Stable id of a line on a page, so a line added, sent or hidden isn't offered again. */
    fun key(pageId: String, text: String): String =
        pageId + "|" + text.lowercase().filter { it.isLetterOrDigit() || it == ' ' }.replace(WHITESPACE, " ").trim().take(80)

    // ---- rules -----------------------------------------------------------------------------------------

    private fun startsWithDoingWord(text: String): Boolean {
        val words = text.lowercase().split(' ').map { it.trim(',', '.', ':', ';', '!') }
        val first = if (words.firstOrNull() == "please") words.getOrNull(1) else words.firstOrNull()
        return first != null && first in DOING_WORDS
    }

    /** "Mike: send the deck" / "Mike – send the deck": someone's action. */
    private fun assignedDoingWord(text: String): Boolean {
        val match = ASSIGNED.find(text) ?: return false
        return startsWithDoingWord(match.groupValues[1])
    }

    private fun explicitDate(lower: String, today: LocalDate): LocalDate? {
        MONTH_DAY.find(lower)?.let { m -> return dateOf(monthOf(m.groupValues[1]), m.groupValues[2].toInt(), null, today) }
        DAY_MONTH.find(lower)?.let { m -> return dateOf(monthOf(m.groupValues[2]), m.groupValues[1].toInt(), null, today) }
        NUMERIC.find(lower)?.let { m ->
            var month = m.groupValues[1].toInt()
            var day = m.groupValues[2].toInt()
            if (month > 12 && day <= 12) month = day.also { day = month }  // 25/10: day first
            val year = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }
            return dateOf(month, day, year, today)
        }
        return null
    }

    /** A date without a year is the next one: this year, or next year when it's more than a week past. */
    private fun dateOf(month: Int?, day: Int, year: Int?, today: LocalDate): LocalDate? {
        if (month == null) return null
        val date = runCatching { LocalDate.of(year ?: today.year, month, day) }.getOrNull() ?: return null
        return if (year == null && date.isBefore(today.minusDays(7))) date.plusYears(1) else date
    }

    private fun monthOf(name: String): Int? = MONTHS.indexOfFirst { name.startsWith(it) }.takeIf { it >= 0 }?.plus(1)

    private fun weekday(name: String): DayOfWeek? = WEEKDAYS.indexOfFirst { name.startsWith(it) }.takeIf { it >= 0 }?.let { DayOfWeek.of(it + 1) }

    /** "Friday" is the coming Friday (today when it's Friday); "next Friday" is the Friday of next week (Monday–Sunday). */
    private fun onOrAfter(today: LocalDate, day: DayOfWeek, next: Boolean): LocalDate =
        if (next) today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).with(TemporalAdjusters.nextOrSame(day))
        else today.with(TemporalAdjusters.nextOrSame(day))

    private const val MAX_LENGTH = 200
    private val WHITESPACE = Regex("\\s+")
    private val DONE_BOX = Regex("^\\s*(\\[\\s*[xX✓✔]\\s*]|\\(\\s*[xX✓✔]\\s*\\)|[☑☒✅✓✔])")
    private val OPEN_BOX = Regex("^\\s*(\\[\\s*]|\\(\\s*\\)|[☐□▢◻○◯❏❑口])\\s*")
    private val BULLET = Regex("^\\s*(->|[-–—•*·>→]|\\d{1,2}[.)])\\s*")
    private val LABEL = Regex("^(to\\s*-?\\s*do|action(\\s+items?)?|next\\s+steps?|follow[\\s-]?up)\\s*[:\\-–]\\s*", RegexOption.IGNORE_CASE)
    private val ASSIGNED = Regex("^[A-Z][\\w'.-]*(?:\\s[A-Z][\\w'.-]*)?\\s*[:–-]\\s*(.+)$")
    private val MODAL = Regex("\\b(needs?\\s+to|have\\s+to|has\\s+to|must|remember\\s+to|don'?t\\s+forget|make\\s+sure|follow\\s+up)\\b", RegexOption.IGNORE_CASE)
    private val TODAY = Regex("\\b(today|tonight|eod)\\b")
    private val TOMORROW = Regex("\\b(tomorrow|tmrw|tmr)\\b")
    private val WEEKDAY = Regex("\\b(next\\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b")
    private val SHORT_WEEKDAY = Regex("\\b(by|on|due|before|until|next)\\s+(mon|tues?|wed|thu(?:rs?)?|fri|sat|sun)\\b")
    private val MONTH_DAY = Regex("\\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b")
    private val DAY_MONTH = Regex("\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\b")
    private val NUMERIC = Regex("\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{2}|\\d{4}))?\\b")
    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val WEEKDAYS = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")

    /** Words a to-do line starts with. Words that mostly start a note as a noun ("Report shows…") are left out. */
    private val DOING_WORDS = setOf(
        "call", "email", "e-mail", "text", "message", "phone", "ring", "send", "resend", "re-send", "forward", "reply",
        "respond", "answer", "ask", "tell", "remind", "invite", "contact", "ping", "chase", "follow", "check",
        "double-check", "confirm", "verify", "review", "read", "write", "draft", "edit", "update", "finish", "complete",
        "submit", "sign", "print", "scan", "file", "upload", "download", "share", "publish", "buy", "order",
        "purchase", "pay", "book", "reserve", "schedule", "reschedule", "plan", "arrange", "organize", "organise",
        "prepare", "prep", "fix", "repair", "clean", "cancel", "renew", "return", "pick", "drop", "collect",
        "deliver", "bring", "take", "get", "make", "create", "build", "install", "research", "find", "look",
        "search", "compare", "decide", "discuss", "meet", "talk", "visit", "register", "apply", "approve",
        "request", "pack", "add", "remove", "delete", "transfer", "deposit", "learn", "watch", "try", "start",
        "begin", "resolve", "investigate", "sort", "tidy", "wash", "cook", "charge", "replace", "measure",
        "calculate", "estimate", "invoice", "sell", "ship", "refill", "refund", "claim", "present", "outline",
        "summarize", "summarise", "translate", "rewrite", "proofread", "setup", "backup", "pray", "memorize",
        "exercise", "go", "see", "do", "give", "introduce", "thank", "recommend", "assign", "delegate", "hire",
        "interview", "train"
    )
}

package com.boox.day.export

import com.boox.day.today.DayDigest
import com.boox.day.today.DigestEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * The Markdown daily note (Oct 2026, idea from CrossInk's "My Clippings.txt"): one file per day
 * with the schedule, the tasks and the text of the memo pages, for Obsidian and other notes apps.
 *
 * Boox Day's part sits between [START] and [END]. Writing the note again replaces only that part,
 * so whatever the user added around it in their notes app stays. Front matter (date, source) is
 * written once, when the file is new.
 */
object DailyNoteMarkdown {
    const val START = "<!-- booxday:start -->"
    const val END = "<!-- booxday:end -->"

    fun fileName(digest: DayDigest): String = "${digest.date}.md"

    /** The note's front matter, for a new file. */
    fun frontMatter(digest: DayDigest): String = "---\ndate: ${digest.date}\nsource: Boox Day\n---\n"

    /**
     * Boox Day's part of the note, markers included. [memoWaitReason]: why handwriting is not read
     * yet, if reading is waiting (e.g. "no internet (Wi-Fi is off)", `IndexingStatus`).
     */
    fun block(
        digest: DayDigest,
        use24Hour: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
        memoWaitReason: String? = null
    ): String =
        buildString {
            appendLine(START)
            val date = digest.date
            appendLine("# ${date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)}, ${date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))}")
            appendLine()

            appendLine("## Schedule")
            if (digest.events.isEmpty()) appendLine("No events.")
            digest.events.forEach { event ->
                // Places can be several lines (Google sends postal addresses that way)
                val place = event.location?.let { oneLine(it) }?.takeIf { it.isNotEmpty() }?.let { " ($it)" }.orEmpty()
                appendLine("- ${whenText(event, use24Hour, zone, locale)}: ${oneLine(event.title)}$place")
                event.booxNoteTitles.forEach { appendLine("  - BOOX Note: ${oneLine(it)}") }
            }
            appendLine()

            appendLine("## Tasks")
            if (digest.tasks.isEmpty()) appendLine("No tasks.")
            digest.tasks.forEach { task ->
                val indent = if (task.isSubtask) "  " else ""
                val box = if (task.completed) "[x]" else "[ ]"
                val priority = if (task.priority.isNotEmpty()) "(${task.priority}) " else ""
                val lines = (task.fullText ?: task.title)?.lines()?.map { it.trimEnd() }?.filter { it.isNotBlank() }
                if (lines.isNullOrEmpty()) {
                    appendLine("$indent- $box ${priority}_Handwritten task (not read yet)_")
                } else {
                    appendLine("$indent- $box $priority${lines.first()}")
                    lines.drop(1).forEach { appendLine("$indent  $it") }
                }
            }

            val pages = digest.memoPages.filter { it.text != null || it.textBoxes.isNotEmpty() || it.hasInk }
            if (pages.isNotEmpty()) {
                appendLine()
                appendLine("## Memos")
                pages.forEachIndexed { i, page ->
                    if (pages.size > 1) {
                        appendLine()
                        appendLine("### Page ${i + 1}")
                    }
                    val text = page.text?.trim()
                    when {
                        text != null -> {
                            appendLine()
                            appendLine(text)
                        }
                        page.hasInk -> {
                            appendLine()
                            appendLine(
                                if (memoWaitReason != null) "_Handwriting not read yet: ${memoWaitReason}._"
                                else "_Handwriting not read yet._"
                            )
                        }
                    }
                    page.textBoxes.forEach { box ->
                        appendLine()
                        box.trim().lines().forEach { appendLine("> $it") }
                    }
                }
            }
            appendLine()
            append(END)
        }

    /**
     * The file's new content: [block] in place of the old Boox Day part, or added at the end of a
     * file that has none, or a new file with front matter.
     */
    fun merge(existing: String?, block: String, frontMatter: String): String {
        if (existing.isNullOrBlank()) return frontMatter + "\n" + block + "\n"
        val start = existing.indexOf(START)
        val end = if (start >= 0) existing.indexOf(END, start) else -1
        if (start >= 0 && end > start) {
            return existing.substring(0, start) + block + existing.substring(end + END.length)
        }
        return existing.trimEnd() + "\n\n" + block + "\n"
    }

    private fun whenText(event: DigestEvent, use24Hour: Boolean, zone: ZoneId, locale: Locale): String {
        if (event.allDay) return "All day"
        val time = DateTimeFormatter.ofPattern(if (use24Hour) "H:mm" else "h:mm a", locale)
        val start = Instant.ofEpochMilli(event.startMs).atZone(zone).format(time)
        val end = event.endMs?.takeIf { it > event.startMs }?.let { Instant.ofEpochMilli(it).atZone(zone).format(time) }
        return if (end != null) "$start–$end" else start
    }

    private fun oneLine(text: String) = text.replace(Regex("\\s*\\n\\s*"), " ").trim()
}

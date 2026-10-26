package com.boox.day.today

import java.time.LocalDate

/**
 * One day of BooxDay in plain data (Oct 2026): what the sleep-screen agenda
 * (sleepscreen/) and the Markdown daily note show. Built by [DayDigestLoader].
 */
data class DayDigest(
    val date: LocalDate,
    /** All-day events first, then by start time; hidden calendars left out. */
    val events: List<DigestEvent>,
    /** In the order of the Tasks page, only rows with something on them. */
    val tasks: List<DigestTask>,
    /** Days in a row with a completed task, ending today or yesterday (TaskRepository.getCurrentStreak). */
    val streakDays: Int,
    /** Today's memo pages in page order (the day's DayPage). */
    val memoPages: List<DigestMemoPage> = emptyList(),
    /** The next few days' events, for the large sleep screen. */
    val nextDays: List<Pair<LocalDate, List<DigestEvent>>> = emptyList(),
) {
    val doneCount: Int get() = tasks.count { it.completed && !it.isSubtask }
    val topLevelCount: Int get() = tasks.count { !it.isSubtask }
}

data class DigestEvent(
    val id: String,
    val title: String,
    val startMs: Long,
    /** Null for all-day events (and events without an end). */
    val endMs: Long?,
    val allDay: Boolean,
    val location: String? = null,
    /** Titles of BOOX Notes linked to this event. */
    val booxNoteTitles: List<String> = emptyList(),
)

data class DigestTask(
    val id: String,
    /** First line of the converted, typed or recognized text; null for handwriting not read yet. */
    val title: String?,
    val completed: Boolean,
    /** "", "A", "B" or "C". */
    val priority: String = "",
    val isSubtask: Boolean = false,
    /** The whole text, when the loader was asked for it (the Markdown note). */
    val fullText: String? = null,
)

data class DigestMemoPage(
    val pageId: String,
    /** Recognized text of the page, null until the page has been read. */
    val text: String?,
    /** Typed, voice and handwriting-converted text boxes on the page. */
    val textBoxes: List<String> = emptyList(),
    /** The page has handwriting (so a page without [text] is not read yet, rather than empty). */
    val hasInk: Boolean = false,
)

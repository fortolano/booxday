package com.boox.day.ui.calendar

import com.boox.day.data.db.BooxNoteIndex
import com.boox.day.data.db.CalendarEvent

/**
 * A Boox Note paired with its linked calendar event (if any).
 * Used for display in DayQuickViewPopup to show both the note and what event it's linked to.
 *
 * Mental model: A user may create a note on Dec 20th, but link it to a meeting
 * from Dec 18th titled "Follow-up with XYZ". This class captures both pieces.
 */
data class BooxNoteWithEvent(
    /** The Boox Note itself */
    val note: BooxNoteIndex,

    /** The calendar event this note is linked to (null = general note, not linked to any event) */
    val linkedEvent: CalendarEvent?,

    /** True if the user manually created this link (vs auto-linked by time proximity) */
    val isManualLink: Boolean = false
)

/**
 * Data model for the Day Quick View popup.
 * Contains all information needed to display the popup when user long-presses a calendar day.
 */
data class DayQuickViewData(
    /** Date in "yyyy-MM-dd" format */
    val date: String,

    /** Human-readable date for display (e.g., "December 30, 2025") */
    val displayDate: String,

    /** List of Boox Notes found for this day, with their linked events */
    val booxNotes: List<BooxNoteWithEvent>,

    /** Number of calendar events on this day */
    val eventCount: Int,

    /** Number of tasks due on this day */
    val tasksDueCount: Int
) {
    /** True if there are any Boox Notes for this day */
    val hasBooxNotes: Boolean get() = booxNotes.isNotEmpty()

    /** True if there are any calendar events on this day */
    val hasEvents: Boolean get() = eventCount > 0

    /** True if there are any tasks due on this day */
    val hasTasksDue: Boolean get() = tasksDueCount > 0

    /** True if there's any content to show (at least one of notes, events, or tasks) */
    val hasAnyContent: Boolean get() = hasBooxNotes || hasEvents || hasTasksDue
}

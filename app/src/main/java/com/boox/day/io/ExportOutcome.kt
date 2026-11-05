package com.boox.day.io

import android.net.Uri

/** How an export ended (Nov 2026). [message] is what the user reads. */
sealed interface ExportOutcome {
    val message: String

    /** The files that were written; some pages may have failed, which [message] says. */
    data class Saved(val uris: List<Uri>, val mimeType: String, override val message: String) : ExportOutcome

    /** Nothing on the chosen pages, so nothing was written. */
    data class Empty(override val message: String) : ExportOutcome

    data class Failed(override val message: String) : ExportOutcome

    companion object {
        const val FAILED_MESSAGE = "Couldn't save the file. Your earlier exports are safe."
        const val TOO_BIG_MESSAGE = "This day is too big for one file. Try one page at a time."
        const val EMPTY_PAGE_MESSAGE = "This page is empty, so nothing was exported."
        const val EMPTY_PAGES_MESSAGE = "These pages are empty, so nothing was exported."
        const val NO_MEMOS_MESSAGE = "No memos in these dates."
        const val TOO_BIG_DATES_MESSAGE = "These dates are too big for one file. Try fewer days."
        private const val NOT_READ_NOTE = " Handwriting wasn't typed: its language pack isn't on this tablet."
        private const val MOVED_NOTE = "Couldn't reach the chosen folder. "

        /** What to say when [target] held nothing to export. */
        fun emptyMessage(target: ExportTarget): String = when {
            target is ExportTarget.Days -> NO_MEMOS_MESSAGE
            target is ExportTarget.Page || (target is ExportTarget.DayPages && target.pageIds.size == 1) -> EMPTY_PAGE_MESSAGE
            else -> EMPTY_PAGES_MESSAGE
        }

        /** [saved] with what else the user should know. */
        fun withNotes(saved: String, handwritingNotRead: Boolean, movedToDefault: Boolean): String =
            (if (movedToDefault) MOVED_NOTE else "") + saved + if (handwritingNotRead) NOT_READ_NOTE else ""
    }
}

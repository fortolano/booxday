package com.boox.day.ui.dialogs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.boox.day.data.db.CalendarEvent

/**
 * What is open in Boox Notes, and where it starts (Nov 2026). Global because the toolbar, the events
 * panel, search, Quick View and event details all open it, and MainScreen has no room for more state.
 */
object BooxNotesPlaces {
    enum class SettingsPage { SETUP, HANDWRITING }

    /** Where the window starts when it opens */
    data class Start(
        val date: String? = null,
        val noteId: String? = null,
        val suggestions: Boolean = false,
        val linkToEvent: CalendarEvent? = null
    )

    var notesOpen by mutableStateOf(false)
    var setupOpen by mutableStateOf(false)
    var settingsPage by mutableStateOf(SettingsPage.SETUP)

    /** Goes up after each check for new notes, so an open window reads the list again */
    var checkCount by mutableIntStateOf(0)

    /** The day shown when a start isn't given: the app's selected day */
    var start: Start = Start()
        private set

    private fun open(start: Start) {
        this.start = start
        notesOpen = true
    }

    fun openNotes() = open(Start())

    /** Opens the Note place, from a badge, a search result, Quick View or event details */
    fun openNote(noteId: String, date: String? = null) = open(Start(date = date, noteId = noteId))

    fun openSuggestions() = open(Start(suggestions = true))

    /** Link a Note: the notes around [event], from event details */
    fun openLinkANote(event: CalendarEvent) = open(Start(linkToEvent = event))

    fun closeNotes() {
        notesOpen = false
        start = Start()
    }

    fun openSetup() {
        settingsPage = SettingsPage.SETUP
        setupOpen = true
    }

    fun openHandwriting() {
        settingsPage = SettingsPage.HANDWRITING
        setupOpen = true
    }
}

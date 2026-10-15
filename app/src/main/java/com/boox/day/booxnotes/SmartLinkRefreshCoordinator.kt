package com.boox.day.booxnotes

import android.content.Context
import com.boox.day.data.db.BooxNoteRepository
import com.boox.day.data.db.CalendarEventRepository

/**
 * Small wrapper so event mutation paths can request Smart Link recomputation
 * without duplicating BooxNoteIndexer construction details everywhere.
 */
class SmartLinkRefreshCoordinator(
    context: Context,
    private val eventRepository: CalendarEventRepository = CalendarEventRepository(context.applicationContext),
    private val noteRepository: BooxNoteRepository = BooxNoteRepository(context.applicationContext),
    private val booxIntegration: BooxNotesIntegration = BooxNotesIntegration(context.applicationContext)
) {
    private val appContext = context.applicationContext

    private val booxNoteIndexer by lazy {
        BooxNoteIndexer(
            context = appContext,
            booxIntegration = booxIntegration,
            noteRepository = noteRepository,
            eventRepository = eventRepository
        )
    }

    suspend fun refreshDates(dates: Collection<String>, reason: String) {
        booxNoteIndexer.refreshSmartLinksForDates(dates, reason)
    }
}

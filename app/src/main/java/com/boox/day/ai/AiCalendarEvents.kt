package com.boox.day.ai

import android.content.Context
import android.util.Log
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.LocalEvent
import com.boox.day.data.db.LocalEventRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * A day's events for the AI features (Oct 2026): synced Google/Outlook events (calendar_events) and
 * events kept on the tablet (local_events), each event once.
 *
 * Daily Summary, Weekly Summary and Smart Suggestions read only the tablet's events before, so a
 * synced calendar never reached them (Go 10.3: 607 synced events, none on the tablet). Synced events
 * come from the calendars shown in BooxDay, as on the calendar: the privacy policy says only
 * Optimize My Day (AgenticContextBuilder) reads hidden calendars, so it can avoid planning over them.
 */
object AiCalendarEvents {
    private const val TAG = "AiCalendarEvents"

    /** Events that touch [date], multi-day events included. */
    suspend fun forDay(context: Context, date: LocalDate): List<LocalEvent> = withContext(Dispatchers.IO) {
        val dayId = date.toString()
        val synced = try {
            AppDatabase.getDatabase(context).calendarEventDao().getByDayIdFilteredSync(dayId).map { it.toLocalEvent() }
        } catch (e: Exception) {
            Log.w(TAG, "Synced events for $dayId not loaded: ${e.message}")
            emptyList()
        }
        val onTablet = try {
            LocalEventRepository(context).getEventsForDay(dayId)
        } catch (e: Exception) {
            Log.w(TAG, "Tablet events for $dayId not loaded: ${e.message}")
            emptyList()
        }
        merge(synced, onTablet)
    }

    /**
     * [synced] plus the [onTablet] events without a synced copy, all-day events first, then by start.
     * A tablet event written to Google/Outlook carries the synced event's id (the calendar screen's
     * rule, EventsViewModel: provider id, else Google id, else its own id).
     */
    fun merge(synced: List<LocalEvent>, onTablet: List<LocalEvent>): List<LocalEvent> {
        val syncedIds = synced.mapNotNull { it.providerEventId }.toSet()
        val tabletOnly = onTablet.filter { (it.providerEventId ?: it.googleEventId ?: it.id) !in syncedIds }
        return (synced + tabletOnly).sortedWith(compareByDescending<LocalEvent> { it.isAllDay }.thenBy { it.startTime })
    }
}

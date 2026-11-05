package com.boox.day.today

import com.boox.day.data.db.CalendarEvent
import com.boox.day.data.db.LocalEvent

/**
 * Which tablet events to show next to the synced ones (Nov 2026). An event made or edited on the
 * tablet can have a tablet copy and a synced row. The copy goes by its service's id, not its own, so
 * comparing its own id let it through and the sleep screen and daily notes listed the event twice.
 */
object ShownEvents {

    /** The events in [local] that aren't already in [synced]. */
    fun tabletOnly(synced: List<CalendarEvent>, local: List<LocalEvent>): List<LocalEvent> {
        val syncedIds = synced.mapTo(HashSet()) { it.id }
        return local.filter { it.toCalendarEvent().id !in syncedIds }
    }
}

package com.boox.day.sync

import com.boox.day.data.db.CalendarEvent
import com.boox.day.data.db.CalendarEventRepository
import com.boox.day.data.db.LocalEvent
import com.boox.day.data.db.LocalEventDao
import com.boox.day.data.db.LocalEventRepository
import com.boox.day.data.db.SyncStatus

/**
 * Editing or deleting a synced event while offline (owner, 2 Nov 2026; all services). Until then
 * BooxDay said "No internet connection" and the change was lost. Now the change shows at once and
 * waits in the tablet's copy of the event (local_events, PENDING_UPDATE or PENDING_DELETE), which
 * the offline-change push (SyncWorker → LocalEventService.syncPendingChanges) sends when the tablet
 * is back online. A sync that runs first doesn't undo it ([withWaitingChanges]).
 */
internal class OfflineEventChanges(
    private val localEvents: LocalEventRepository,
    private val localEventDao: LocalEventDao,
    private val events: CalendarEventRepository
) {
    /** [edited] (the synced event [cloud] with the user's fields) shows now and is sent later. */
    suspend fun keepEdit(cloud: CalendarEvent, edited: CalendarEvent) {
        val copy = copyOf(cloud)
        val waiting = (copy ?: newCopy(cloud)).copy(
            title = edited.title,
            dayId = edited.dayId,
            endDayId = edited.endDayId,
            startTime = edited.startTime,
            endTime = edited.endTime,
            isAllDay = edited.isAllDay,
            location = edited.location,
            description = edited.description,
            attendees = edited.attendees,
            syncEnabled = true,
            syncStatus = if (copy?.syncStatus == SyncStatus.PENDING_CREATE) SyncStatus.PENDING_CREATE else SyncStatus.PENDING_UPDATE,
            updatedAt = System.currentTimeMillis()
        )
        if (copy == null) localEventDao.insert(waiting) else localEventDao.update(waiting)
        events.cacheEvent(edited)
    }

    /** [cloud] disappears now and is deleted on its service later. */
    suspend fun keepDelete(cloud: CalendarEvent) {
        val copy = copyOf(cloud)
        val waiting = (copy ?: newCopy(cloud)).copy(
            syncEnabled = true,
            syncStatus = SyncStatus.PENDING_DELETE,
            updatedAt = System.currentTimeMillis()
        )
        // The copy first: removing the synced event drops copies that wait for nothing
        if (copy == null) localEventDao.insert(waiting) else localEventDao.update(waiting)
        events.removeEvent(cloud.id)
    }

    /**
     * What a sync fetched for [accountId], with its changes still waiting to be sent laid over it:
     * an event deleted offline stays out, an event edited offline keeps the edit ([cached], as the
     * tablet shows it). An event the service no longer has stays out (its waiting edit is dropped
     * when sending finds it gone).
     */
    suspend fun withWaitingChanges(accountId: String, fetched: List<CalendarEvent>, cached: List<CalendarEvent>): List<CalendarEvent> {
        val waiting = localEventDao.getWaitingChangesForAccount(accountId)
        if (waiting.isEmpty()) return fetched
        val deleted = waiting.filter { it.syncStatus == SyncStatus.PENDING_DELETE }.mapNotNull { it.remoteId() }.toSet()
        val edited = waiting.filter { it.syncStatus == SyncStatus.PENDING_UPDATE }.mapNotNull { it.remoteId() }.toSet()
        val cachedById = cached.associateBy { it.id }
        return fetched.filter { it.id !in deleted }.map { event ->
            if (event.id in edited) cachedById[event.id] ?: event else event
        }
    }

    private suspend fun copyOf(cloud: CalendarEvent): LocalEvent? =
        localEventDao.getByProviderEventId(cloud.id) ?: localEventDao.getByGoogleEventId(cloud.id)

    /** A copy of a synced event made elsewhere, to carry a change to it. */
    private fun newCopy(cloud: CalendarEvent): LocalEvent {
        val isGoogle = cloud.providerId == "GOOGLE"
        return LocalEvent(
            title = cloud.title,
            dayId = cloud.dayId,
            endDayId = cloud.endDayId.ifEmpty { cloud.dayId },
            startTime = cloud.startTime,
            endTime = cloud.endTime,
            isAllDay = cloud.isAllDay,
            location = cloud.location,
            description = cloud.description,
            colorHex = cloud.colorHex,
            attendees = cloud.attendees,
            providerId = cloud.providerId,
            accountId = cloud.accountId,
            accountEmail = cloud.accountEmail,
            providerEventId = cloud.id,
            providerCalendarId = cloud.calendarId,
            providerCalendarName = cloud.calendarName,
            googleEventId = if (isGoogle) cloud.id else null,
            googleCalendarId = if (isGoogle) cloud.calendarId else null,
            googleCalendarName = if (isGoogle) cloud.calendarName else null,
            isReadOnly = cloud.isReadOnly,
            // Never "just confirmed": the push's stale-update check skips copies without a sync time
            lastSyncedAt = null
        )
    }

    private fun LocalEvent.remoteId(): String? = providerEventId ?: googleEventId
}

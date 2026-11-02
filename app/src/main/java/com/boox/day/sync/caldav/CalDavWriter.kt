package com.boox.day.sync.caldav

import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.CalDavResource
import com.boox.day.data.model.ProviderEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Events created, changed or deleted on the tablet, written to the CalDAV server (plan stage 3, step
 * 3.5, Oct 2026; owner decision 60: show and edit, like Google). Every write is guarded by the file's
 * change tag (If-Match): when the file changed on the server meanwhile, it is read again and the
 * change is made to the new version, once. [CalDavEditor] changes only BooxDay's fields, and of
 * those an edit takes the server's new value for each one the user left as it was ([merged]), so a
 * phone edit made since the last sync isn't undone by a dialog that still showed the old value.
 * A second clash fails with [CalDavConnection.Failure.Server] 412.
 *
 * The stored copy (caldav_resource) is updated with what the server now holds, so the next sync
 * downloads nothing for it.
 */
internal class CalDavWriter(
    db: AppDatabase,
    private val now: () -> Long = System::currentTimeMillis
) {
    /** The event's file is no longer on the server (or was never read). */
    class EventGone : Exception("The event is no longer on the server")

    private val dao = db.calDavDao()

    /**
     * A new event in [calendarHref]; returns its row. Its file is named after [uid]: when a try sent
     * later (the same uid) finds its file already there, the answer to an earlier try was lost on
     * the way back, and that file is the event (no second copy).
     */
    suspend fun create(
        connection: CalDavConnection,
        accountId: String,
        calendarHomeUrl: String,
        calendarHref: String,
        fields: CalDavEditor.Fields,
        zone: ZoneId,
        uid: String = UUID.randomUUID().toString()
    ): ProviderEvent {
        val href = calendarHref.trimEnd('/') + "/$uid.ics"
        val ics = CalDavEditor.newEvent(uid, fields, zone, now())
        val resource = CalDavResource(accountId, href, calendarHref, null, ics, uid, now())
        val saved = try {
            val etag = connection.putEvent(url(calendarHomeUrl, href), ics, ifMatch = null, create = true)
            store(connection, calendarHomeUrl, resource.copy(etag = etag), etag)
        } catch (e: CalDavConnection.Failure.Server) {
            if (e.code != 412) throw e
            download(connection, calendarHomeUrl, resource) ?: throw e
        }
        return rowOf(saved, occurrence = null, fields, zone)
    }

    /** [eventId]'s event (or that one day of its series) with [fields]; returns its row. */
    suspend fun update(
        connection: CalDavConnection,
        accountId: String,
        calendarHomeUrl: String,
        calendarHref: String,
        eventId: String,
        fields: CalDavEditor.Fields,
        zone: ZoneId
    ): ProviderEvent {
        val (resource, occurrence) = find(accountId, calendarHref, eventId) ?: throw EventGone()
        val saved = guarded(connection, calendarHomeUrl, resource, onGone = { throw EventGone() }) { current ->
            val wanted = if (current === resource) fields
            else merged(fields, fieldsOf(resource, eventId, occurrence, zone), fieldsOf(current, eventId, occurrence, zone))
            val ics = CalDavEditor.edit(current.ics, occurrence, wanted, zone, now())
            val etag = connection.putEvent(url(calendarHomeUrl, current.href), ics, ifMatch = current.etag, create = false)
            store(connection, calendarHomeUrl, current.copy(ics = ics, etag = etag, syncedAt = now()), etag)
        }
        return rowOf(saved, occurrence, fields, zone)
    }

    /** Deletes [eventId]'s event, or only that day of its series. One already gone counts as deleted. */
    suspend fun delete(
        connection: CalDavConnection,
        accountId: String,
        calendarHomeUrl: String,
        calendarHref: String,
        eventId: String
    ) {
        val (resource, occurrence) = find(accountId, calendarHref, eventId) ?: return
        guarded(connection, calendarHomeUrl, resource, onGone = { forget(resource) }) { current ->
            val left = CalDavEditor.removeOccurrence(current.ics, occurrence)
            if (left == null) {
                connection.deleteEvent(url(calendarHomeUrl, current.href), current.etag)
                forget(current)
            } else {
                val etag = connection.putEvent(url(calendarHomeUrl, current.href), left, ifMatch = current.etag, create = false)
                store(connection, calendarHomeUrl, current.copy(ics = left, etag = etag, syncedAt = now()), etag)
            }
        }
    }

    // ── Steps ──

    /**
     * Runs [write] on the stored copy; when the server says the file changed (412), once more on the
     * server's version. [onGone] when the file was deleted there meanwhile.
     */
    private suspend fun <T> guarded(
        connection: CalDavConnection,
        calendarHomeUrl: String,
        resource: CalDavResource,
        onGone: suspend () -> T,
        write: suspend (CalDavResource) -> T
    ): T {
        try {
            return write(resource)
        } catch (e: CalDavConnection.Failure.Server) {
            if (e.code != 412) throw e
        }
        val fresh = download(connection, calendarHomeUrl, resource) ?: return onGone()
        return write(fresh)
    }

    /**
     * Saves what the server now holds. With a change tag, the text sent is the server's; without
     * one the server changed it (RFC 4791 5.3.4), so its version is read back.
     */
    private suspend fun store(connection: CalDavConnection, calendarHomeUrl: String, resource: CalDavResource, etag: String?): CalDavResource {
        val saved = if (etag != null) resource else download(connection, calendarHomeUrl, resource) ?: resource
        withContext(Dispatchers.IO) { dao.upsertResource(saved) }
        return saved
    }

    private suspend fun download(connection: CalDavConnection, calendarHomeUrl: String, resource: CalDavResource): CalDavResource? {
        val calendarUrl = url(calendarHomeUrl, resource.calendarHref)
        val file = connection.downloadEvents(calendarUrl, listOf(resource.href)).firstOrNull() ?: return null
        return resource.copy(etag = file.etag, ics = file.ics, uid = CalDavEvents.uidOf(file.ics), syncedAt = now())
            .also { withContext(Dispatchers.IO) { dao.upsertResource(it) } }
    }

    private suspend fun forget(resource: CalDavResource) =
        withContext(Dispatchers.IO) { dao.deleteResource(resource.accountId, resource.href) }

    /** The stored file behind a row id, and the day of its series ([CalDavEvents.eventId]). */
    private suspend fun find(accountId: String, calendarHref: String, eventId: String): Pair<CalDavResource, String?>? {
        val rest = eventId.removePrefix("caldav:$accountId:").takeIf { it != eventId } ?: return null
        val hash = rest.substringBefore(':')
        val occurrence = rest.substringAfter(':', "").ifEmpty { null }
        val resource = withContext(Dispatchers.IO) { dao.getResources(accountId, calendarHref) }
            .firstOrNull { CalDavEvents.hrefHash(it.href) == hash } ?: return null
        return resource to occurrence
    }

    /**
     * The fields to write after a clash: each one the user changed ([mine] differs from [base], the
     * version the tablet had) as the user set it, the others as the server now has them ([theirs]).
     * Day and times go together. Without both versions, the user's.
     */
    internal fun merged(mine: CalDavEditor.Fields, base: CalDavEditor.Fields?, theirs: CalDavEditor.Fields?): CalDavEditor.Fields {
        if (base == null || theirs == null) return mine
        val m = mine.normal()
        val b = base.normal()
        val t = theirs.normal()
        fun <T> pick(mineValue: T, baseValue: T, theirValue: T) = if (mineValue == baseValue) theirValue else mineValue
        val timing = if (m.timing() == b.timing()) t else m
        return m.copy(
            title = pick(m.title, b.title, t.title),
            location = pick(m.location, b.location, t.location),
            description = pick(m.description, b.description, t.description),
            date = timing.date, startTime = timing.startTime, endTime = timing.endTime, isAllDay = timing.isAllDay
        )
    }

    /** Blank is none; an all-day event has no times; no end is an hour (as written). */
    private fun CalDavEditor.Fields.normal() = copy(
        title = title.trim(),
        location = location?.trim()?.ifEmpty { null },
        description = description?.trim()?.ifEmpty { null },
        startTime = if (isAllDay) LocalTime.MIDNIGHT else startTime,
        endTime = if (isAllDay) null else endTime ?: startTime.plusHours(1)
    )

    private fun CalDavEditor.Fields.timing() = listOf(date, startTime, endTime, isAllDay)

    /** BooxDay's fields of one row of a stored file, or null when the row isn't in it. */
    private fun fieldsOf(resource: CalDavResource, eventId: String, occurrence: String?, zone: ZoneId): CalDavEditor.Fields? {
        // Around the day's original date for a series; any date for a single event (one row)
        val around = occurrence?.take(8)?.let { LocalDate.parse(it, DateTimeFormatter.BASIC_ISO_DATE) }
        val from = around?.minusDays(400) ?: LocalDate.of(1900, 1, 1)
        val to = around?.plusDays(400) ?: LocalDate.of(2199, 12, 31)
        val row = CalDavEvents.occurrences(
            resource.accountId, resource.calendarHref, resource.href, resource.ics, from, to, readOnly = false, zone = zone
        ).firstOrNull { it.id == eventId } ?: return null
        val start = Instant.ofEpochMilli(row.startTime).atZone(zone)
        return CalDavEditor.Fields(
            title = row.title,
            date = if (row.isAllDay) LocalDate.parse(row.dayId) else start.toLocalDate(),
            startTime = start.toLocalTime(),
            endTime = row.endTime?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() },
            isAllDay = row.isAllDay,
            location = row.location,
            description = row.description
        )
    }

    /** The written event's row, as the next sync will read it (same id). */
    private fun rowOf(resource: CalDavResource, occurrence: String?, fields: CalDavEditor.Fields, zone: ZoneId): ProviderEvent {
        val id = CalDavEvents.eventId(resource.accountId, resource.href, occurrence)
        val rows = CalDavEvents.occurrences(
            resource.accountId, resource.calendarHref, resource.href, resource.ics,
            fields.date.minusDays(1), fields.date.plusDays(1), readOnly = false, zone = zone
        )
        return rows.firstOrNull { it.id == id } ?: rows.firstOrNull() ?: throw EventGone()
    }

    private fun url(calendarHomeUrl: String, href: String): String =
        (calendarHomeUrl.toHttpUrl().resolve(href) ?: throw CalDavConnection.Failure.BadAddress()).toString()}

package com.boox.day.sync.caldav

import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.CalDavResource
import com.boox.day.data.model.ProviderEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/**
 * Reads one CalDAV calendar for the sync window (plan stage 3, step 3.4, Oct 2026). The server lists
 * the window's event files with their change tags (one small request); only new or changed files are
 * downloaded, files that left the calendar or the window are forgotten, and every kept file becomes
 * its rows ([CalDavEvents]). The files are kept whole in caldav_resource, so a tablet edit (step 3.5)
 * changes only BooxDay's fields.
 *
 * The rows cover the whole window, or the read fails (a [CalDavConnection.Failure]): the sync manager
 * deletes window events a provider didn't return, so a half-read calendar must never look complete.
 */
internal class CalDavReader(
    private val db: AppDatabase,
    private val now: () -> Long = System::currentTimeMillis
) {
    sealed class Outcome {
        data class Read(val events: List<ProviderEvent>, val downloaded: Int, val removed: Int) : Outcome()

        /** The calendar was removed on the server: its files are forgotten (like a deleted Google calendar). */
        object Gone : Outcome()
    }

    private val dao = db.calDavDao()

    suspend fun read(
        connection: CalDavConnection,
        accountId: String,
        calendarHomeUrl: String,
        calendarHref: String,
        readOnly: Boolean,
        from: LocalDate,
        to: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): Outcome {
        val calendarUrl = calendarHomeUrl.toHttpUrl().resolve(calendarHref)?.toString()
            ?: throw CalDavConnection.Failure.BadAddress()
        val start = Date.from(from.atStartOfDay(zone).toInstant())
        val end = Date.from(to.plusDays(1).atStartOfDay(zone).toInstant())

        val tags = try {
            connection.eventTags(calendarUrl, start, end)
        } catch (e: CalDavConnection.Failure.Server) {
            if (e.code != 404 && e.code != 410) throw e
            withContext(Dispatchers.IO) {
                db.runInTransaction {
                    dao.deleteResourcesOf(accountId, calendarHref)
                    dao.deleteCalendar(accountId, calendarHref)
                }
            }
            return Outcome.Gone
        }

        val stored = withContext(Dispatchers.IO) { dao.getResources(accountId, calendarHref) }.associateBy { it.href }
        val changed = tags.filter { (href, tag) -> stored[href].let { it == null || tag == null || it.etag != tag } }.keys
        val files = if (changed.isEmpty()) emptyList() else connection.downloadEvents(calendarUrl, changed.toList())
        val gone = stored.keys - tags.keys

        val kept = withContext(Dispatchers.IO) {
            val syncedAt = now()
            db.runInTransaction {
                for (file in files) {
                    dao.upsertResource(
                        CalDavResource(accountId, file.href, calendarHref, file.etag, file.ics, CalDavEvents.uidOf(file.ics), syncedAt)
                    )
                }
                gone.forEach { dao.deleteResource(accountId, it) }
            }
            dao.getResources(accountId, calendarHref)
        }
        val events = kept.flatMap { CalDavEvents.occurrences(accountId, calendarHref, it.href, it.ics, from, to, readOnly, zone) }
        return Outcome.Read(events, downloaded = files.size, removed = gone.size)
    }
}

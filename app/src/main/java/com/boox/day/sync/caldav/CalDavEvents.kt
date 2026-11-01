package com.boox.day.sync.caldav

import android.util.Log
import com.boox.day.data.model.ProviderEvent
import com.boox.day.data.model.timedEventLastDay
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.Attendee
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import java.io.StringReader
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAmount

/**
 * One CalDAV event file (iCalendar) turned into the rows BooxDay shows (plan stage 3, step 3.4,
 * Oct 2026), the way Google's events arrive: one row per day a repeating event happens, inside the
 * sync window. Repeats are worked out in the event's own time zone, so a 09:00 meeting in Berlin
 * stays 09:00 Berlin across a summer-time change; a moved or retitled day (an exception, own
 * RECURRENCE-ID) replaces that day, a cancelled or removed day (EXDATE) is left out.
 *
 * All-day rows start at the device's midnight and end at the next free day's midnight, as Google's.
 */
internal object CalDavEvents {
    private const val TAG = "CalDavEvents"
    private const val UNTITLED = "Untitled Event"
    private const val DAY_MS = 86_400_000L

    /** Rows per repeating event at most: a window holds ~150 days, so only a broken rule gets near it. */
    private const val MAX_OCCURRENCES = 2000

    private val UTC_KEY = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    /**
     * BooxDay's id for a row: unique per account and file, the same at every sync (calendar_events'
     * key, Memo links and Smart Link use it). [occurrence] is the repeat's original start, null for
     * an event that doesn't repeat.
     */
    fun eventId(accountId: String, href: String, occurrence: String?): String =
        "caldav:$accountId:${hrefHash(href)}" + (occurrence?.let { ":$it" } ?: "")

    /** 16 hex characters of the file path's SHA-256: short, and no server characters in BooxDay's ids. */
    fun hrefHash(href: String): String =
        MessageDigest.getInstance("SHA-256").digest(href.toByteArray()).take(8).joinToString("") { "%02x".format(it) }

    /** The file's UID (one file holds one event and its exceptions), or null when unreadable. */
    fun uidOf(ics: String): String? = parse(ics)?.events()?.firstNotNullOfOrNull { it.uid?.value }

    /**
     * The file's rows that touch [from]..[to] (days, inclusive). [readOnly] comes from the calendar.
     * An unreadable file gives no rows (logged by size only: event text is user content).
     */
    fun occurrences(
        accountId: String,
        calendarHref: String,
        href: String,
        ics: String,
        from: LocalDate,
        to: LocalDate,
        readOnly: Boolean,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<ProviderEvent> {
        val events = parse(ics)?.events() ?: return emptyList()
        return try {
            Rows(accountId, calendarHref, href, from, to, readOnly, zone).of(events)
        } catch (e: Exception) {
            Log.w(TAG, "Event file not expanded (${ics.length} chars): ${e.javaClass.simpleName}")
            emptyList()
        }
    }

    private class Rows(
        val accountId: String,
        val calendarHref: String,
        val href: String,
        val from: LocalDate,
        val to: LocalDate,
        val readOnly: Boolean,
        val zone: ZoneId
    ) {
        val windowStart = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val windowEnd = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        fun of(events: List<VEvent>): List<ProviderEvent> {
            val master = events.firstOrNull { it.recurrenceId == null }
            val exceptions = events.filter { it.recurrenceId?.date != null }
            val replaced = exceptions.map { key(it.recurrenceId.date) }.toSet()
            val rows = mutableListOf<ProviderEvent>()

            if (master != null && !master.cancelled() && master.startDate?.date != null) {
                if (!master.repeats()) {
                    row(master, master, master.startDate.date, occurrence = null)?.let(rows::add)
                } else {
                    val excluded = master.excludedKeys()
                    for (start in master.starts()) {
                        val k = key(start)
                        if (k in replaced || k in excluded || dayKey(start) in excluded) continue
                        row(master, master, start, occurrence = k)?.let(rows::add)
                    }
                }
            }
            // An exception is its own event for that day; one without its series (an invitation to
            // a single day) shows on its own
            for (exception in exceptions) {
                if (exception.cancelled()) continue
                val start = exception.startDate?.date ?: continue
                row(exception, master, start, occurrence = key(exception.recurrenceId.date))?.let(rows::add)
            }
            return rows
        }

        /** The row for one start, or null when it falls outside the window. */
        private fun row(event: VEvent, master: VEvent?, start: Date, occurrence: String?): ProviderEvent? {
            val allDay = event.startDate.date !is DateTime
            val startMs: Long
            val endMs: Long
            val firstDay: LocalDate
            val lastDay: LocalDate
            if (allDay) {
                firstDay = localDate(start)
                val days = event.lengthInDays()
                lastDay = firstDay.plusDays(days - 1)
                startMs = firstDay.atStartOfDay(zone).toInstant().toEpochMilli()
                endMs = firstDay.plusDays(days).atStartOfDay(zone).toInstant().toEpochMilli()
            } else {
                startMs = start.time
                endMs = startMs + event.lengthInMs()
                firstDay = Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate()
                lastDay = timedEventLastDay(startMs, endMs, zone)
            }
            if (firstDay > to || lastDay < from) return null

            return ProviderEvent(
                id = eventId(accountId, href, occurrence),
                calendarId = calendarHref,
                title = event.summary?.value?.takeIf { it.isNotBlank() }
                    ?: master?.summary?.value?.takeIf { it.isNotBlank() } ?: UNTITLED,
                startTime = startMs,
                endTime = endMs,
                isAllDay = allDay,
                location = (event.location ?: master?.location)?.value?.takeIf { it.isNotBlank() },
                description = (event.description ?: master?.description)?.value?.takeIf { it.isNotBlank() },
                attendees = event.getProperties<Attendee>(Property.ATTENDEE)
                    .mapNotNull { it.calAddress?.schemeSpecificPart?.takeIf { address -> address.isNotBlank() } }
                    .joinToString(",").takeIf { it.isNotEmpty() },
                colorHex = null,  // the calendar's colour, set by the provider
                isReadOnly = readOnly
            )
        }

        /**
         * Every start of a repeating event near the window: DTSTART (always the first, RFC 5545
         * 3.8.5.3), its RRULEs and RDATEs. Exact window checks happen per row.
         */
        private fun VEvent.starts(): Collection<Date> {
            val seed = startDate.date
            val value = if (seed is DateTime) Value.DATE_TIME else Value.DATE
            val span = if (seed is DateTime) lengthInMs() else lengthInDays() * DAY_MS
            val near = DateTime(windowStart - span - DAY_MS).apply { isUtc = true }
            val far = DateTime(windowEnd + DAY_MS).apply { isUtc = true }
            val found = LinkedHashMap<String, Date>()
            if (seed.time in near.time..far.time) found[key(seed)] = seed
            for (rule in getProperties<RRule>(Property.RRULE)) {
                for (date in rule.recur.getDates(seed, near, far, value, MAX_OCCURRENCES)) found.putIfAbsent(key(date), date)
            }
            for (rdate in getProperties<RDate>(Property.RDATE)) {
                rdate.dates?.forEach { found.putIfAbsent(key(it), it) }
                rdate.periods?.forEach { period -> period.start?.let { found.putIfAbsent(key(it), it) } }
            }
            return found.values.take(MAX_OCCURRENCES)
        }

        /** EXDATE keys; a day-only EXDATE on a timed series removes that whole day ([dayKey]). */
        private fun VEvent.excludedKeys(): Set<String> =
            getProperties<ExDate>(Property.EXDATE).flatMap { it.dates.orEmpty() }.map { key(it) }.toSet()

        private fun VEvent.repeats(): Boolean =
            getProperties<Property>(Property.RRULE).isNotEmpty() || getProperties<Property>(Property.RDATE).isNotEmpty()

        private fun VEvent.cancelled(): Boolean = status?.value.equals("CANCELLED", ignoreCase = true)

        /** All-day length: DTEND (exclusive) or DURATION, at least one day. */
        private fun VEvent.lengthInDays(): Long {
            val first = localDate(startDate.date)
            val end = getEndDate(false)?.date
            val days = when {
                end != null -> ChronoUnit.DAYS.between(first, localDate(end))
                duration != null -> amountInMs(duration.duration, startDate.date.time) / DAY_MS
                else -> 1
            }
            return days.coerceAtLeast(1)
        }

        /** Timed length: DTEND or DURATION (the same exact length for every repeat, RFC 5545 3.8.2.2), else 0. */
        private fun VEvent.lengthInMs(): Long {
            val start = startDate.date.time
            val end = getEndDate(false)?.date
            val ms = when {
                end != null -> end.time - start
                duration != null -> amountInMs(duration.duration, start)
                else -> 0
            }
            return ms.coerceAtLeast(0)
        }

        private fun amountInMs(amount: TemporalAmount, from: Long): Long {
            val start = Instant.ofEpochMilli(from).atZone(ZoneOffset.UTC)
            return ChronoUnit.MILLIS.between(start, start.plus(amount))
        }

        /** The day of a value: a DATE as written, a DATE-TIME on the device's clock. */
        private fun localDate(date: Date): LocalDate =
            if (date is DateTime) Instant.ofEpochMilli(date.time).atZone(zone).toLocalDate()
            else LocalDate.parse(date.toString(), DateTimeFormatter.BASIC_ISO_DATE)

        /** A start's day in the EXDATE form "yyyyMMdd". */
        private fun dayKey(date: Date): String = localDate(date).format(DateTimeFormatter.BASIC_ISO_DATE)
    }

    /** A start as the same text whatever time zone it was written in: "yyyyMMdd" or UTC "yyyyMMddTHHmmssZ". */
    private fun key(date: Date): String =
        if (date is DateTime) UTC_KEY.format(Instant.ofEpochMilli(date.time)) else date.toString()

    private fun Calendar.events(): List<VEvent> = getComponents<VEvent>(Component.VEVENT)

    internal fun parse(ics: String): Calendar? = try {
        CalendarBuilder().build(StringReader(ics))
    } catch (e: Exception) {
        Log.w(TAG, "Unreadable event file (${ics.length} chars): ${e.javaClass.simpleName}")
        null
    }
}

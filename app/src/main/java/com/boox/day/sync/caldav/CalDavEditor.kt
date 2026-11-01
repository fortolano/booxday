package com.boox.day.sync.caldav

import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.ComponentList
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.PropertyList
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.CalScale
import net.fortuna.ical4j.model.property.Description
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStamp
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.LastModified
import net.fortuna.ical4j.model.property.Location
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.RecurrenceId
import net.fortuna.ical4j.model.property.Sequence
import net.fortuna.ical4j.model.property.Summary
import net.fortuna.ical4j.model.property.Uid
import net.fortuna.ical4j.model.property.Version
import java.io.StringWriter
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Changes to a CalDAV event file made on the tablet (plan stage 3, step 3.5, Oct 2026). Only the
 * fields BooxDay edits change (title, day and times, place, notes); everything else in the file
 * (alarms, attendees, the organizer, other apps' properties, the series' other days) stays as the
 * server sent it. Attendees are never written: on iCloud and similar servers a new ATTENDEE sends
 * an invitation email.
 *
 * One day of a repeating series changes or goes alone, like Google: an edit becomes (or updates)
 * that day's exception, a delete adds an EXDATE. Its row id stays the same ([CalDavEvents.eventId]).
 */
internal object CalDavEditor {
    private const val PRODID = "-//BooxDay//BooxDay//EN"

    /** BooxDay's fields of an event, as its dialog gives them (one day; end on that day or none). */
    data class Fields(
        val title: String,
        val date: LocalDate,
        val startTime: LocalTime,
        val endTime: LocalTime?,
        val isAllDay: Boolean,
        val location: String?,
        val description: String?
    )

    /** A new event file with one event. Timed events are written in UTC (every server reads it). */
    fun newEvent(uid: String, fields: Fields, zone: ZoneId, now: Long): String {
        val event = VEvent().apply {
            properties.add(Uid(uid))
            properties.add(DtStamp(utc(now)))
        }
        apply(event, fields, zone, now, keepDays = false)
        val calendar = Calendar().apply {
            properties.add(ProdId(PRODID))
            properties.add(Version.VERSION_2_0)
            properties.add(CalScale.GREGORIAN)
            components.add(event)
        }
        return write(calendar)
    }

    /**
     * [ics] with BooxDay's fields set on the event, or on one day of a series ([occurrence], the
     * key in the row id). An all-day event that stays all-day keeps its number of days (the dialog
     * has no end date).
     */
    fun edit(ics: String, occurrence: String?, fields: Fields, zone: ZoneId, now: Long): String {
        val calendar = parse(ics)
        val events = calendar.events()
        val master = events.firstOrNull { it.recurrenceId == null }
        val target = when {
            occurrence == null -> master ?: events.first()
            else -> events.firstOrNull { it.recurrenceId != null && key(it.recurrenceId.date) == occurrence }
                ?: exceptionOf(checkNotNull(master) { "No series for this day" }, occurrence).also { calendar.components.add(it) }
        }
        apply(target, fields, zone, now, keepDays = true)
        return write(calendar)
    }

    /**
     * [ics] without one day of a series, or null when nothing is left (delete the file). Without
     * [occurrence] the whole event goes.
     */
    fun removeOccurrence(ics: String, occurrence: String?): String? {
        if (occurrence == null) return null
        val calendar = parse(ics)
        val events = calendar.events()
        events.filter { it.recurrenceId != null && key(it.recurrenceId.date) == occurrence }
            .forEach { calendar.components.remove(it) }
        val master = events.firstOrNull { it.recurrenceId == null }
        if (master == null) return if (calendar.events().isEmpty()) null else write(calendar)
        master.properties.add(ExDate(params(occurrence), occurrence))
        bumpSequence(master)
        return write(calendar)
    }

    // ── Steps ──

    private fun apply(event: VEvent, fields: Fields, zone: ZoneId, now: Long, keepDays: Boolean) {
        val props = event.properties
        val oldDays = event.startDate?.date?.takeIf { keepDays && it !is DateTime && fields.isAllDay }?.let { start ->
            event.getEndDate(false)?.date?.let { end -> ChronoUnit.DAYS.between(day(start), day(end)).coerceAtLeast(1) }
        } ?: 1L
        listOf(Property.DTSTART, Property.DTEND, Property.DURATION).forEach { name -> props.removeIf { it.name == name } }

        // Written as text, as the server's files are read: building ical4j dates in code shifts
        // a day by the device's time zone
        val (start, end) = if (fields.isAllDay) {
            fields.date.format(DateTimeFormatter.BASIC_ISO_DATE) to fields.date.plusDays(oldDays).format(DateTimeFormatter.BASIC_ISO_DATE)
        } else {
            val from = fields.date.atTime(fields.startTime).atZone(zone)
            // No end: an hour, as Google gets it (CalendarSyncManager.createEvent)
            val to = fields.endTime?.let { fields.date.atTime(it).atZone(zone) } ?: from.plusHours(1)
            utcText(from.toInstant().toEpochMilli()) to utcText(to.toInstant().toEpochMilli())
        }
        props.add(DtStart(params(start), start))
        props.add(DtEnd(params(end), end))
        set(props, Property.SUMMARY, fields.title.takeIf { it.isNotBlank() }?.let(::Summary))
        set(props, Property.LOCATION, fields.location?.takeIf { it.isNotBlank() }?.let(::Location))
        set(props, Property.DESCRIPTION, fields.description?.takeIf { it.isNotBlank() }?.let(::Description))
        set(props, Property.DTSTAMP, DtStamp(utc(now)))
        set(props, Property.LAST_MODIFIED, LastModified(utc(now)))
        bumpSequence(event)
    }

    /** A copy of the series' event for one day: same fields and alarms, no repeat rules. */
    private fun exceptionOf(master: VEvent, occurrence: String): VEvent {
        val props = PropertyList<Property>(master.properties)
        listOf(Property.RRULE, Property.RDATE, Property.EXDATE, Property.EXRULE).forEach { name -> props.removeIf { it.name == name } }
        props.add(RecurrenceId(params(occurrence), occurrence))
        val alarms = ComponentList<VAlarm>().apply { master.alarms.forEach { add(it.copy<VAlarm>()) } }
        return VEvent(props, alarms)
    }

    private fun bumpSequence(event: VEvent) {
        val next = (event.sequence?.sequenceNo ?: 0) + 1
        set(event.properties, Property.SEQUENCE, Sequence(next))
    }

    private fun set(props: PropertyList<Property>, name: String, value: Property?) {
        props.removeIf { it.name == name }
        if (value != null) props.add(value)
    }

    private fun utc(ms: Long) = DateTime(ms).apply { isUtc = true }

    private fun utcText(ms: Long) = utc(ms).toString()

    /** VALUE=DATE for a day ("yyyyMMdd"), none for a UTC time. */
    private fun params(value: String) = ParameterList().apply { if (value.length == 8) add(Value.DATE) }

    private fun day(date: Date): LocalDate = LocalDate.parse(date.toString().take(8), DateTimeFormatter.BASIC_ISO_DATE)

    /** The same key as [CalDavEvents]: "yyyyMMdd", or UTC "yyyyMMddTHHmmssZ". */
    private fun key(date: Date): String =
        if (date is DateTime) DateTime(date.time).apply { isUtc = true }.toString() else date.toString()

    private fun parse(ics: String): Calendar = checkNotNull(CalDavEvents.parse(ics)) { "Unreadable event file" }

    private fun Calendar.events(): List<VEvent> = getComponents<VEvent>(Component.VEVENT)

    private fun write(calendar: Calendar): String = StringWriter().also { CalendarOutputter(false).output(calendar, it) }.toString()
}

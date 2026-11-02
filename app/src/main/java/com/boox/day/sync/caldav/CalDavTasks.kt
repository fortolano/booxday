package com.boox.day.sync.caldav

import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.PropertyList
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.RelType
import net.fortuna.ical4j.model.parameter.Related
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.Action
import net.fortuna.ical4j.model.property.CalScale
import net.fortuna.ical4j.model.property.Completed
import net.fortuna.ical4j.model.property.Created
import net.fortuna.ical4j.model.property.Description
import net.fortuna.ical4j.model.property.DtStamp
import net.fortuna.ical4j.model.property.Due
import net.fortuna.ical4j.model.property.LastModified
import net.fortuna.ical4j.model.property.PercentComplete
import net.fortuna.ical4j.model.property.Priority
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.RelatedTo
import net.fortuna.ical4j.model.property.Status
import net.fortuna.ical4j.model.property.Summary
import net.fortuna.ical4j.model.property.Uid
import net.fortuna.ical4j.model.property.Version
import java.io.StringWriter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * CalDAV tasks (VTODO, RFC 5545) as task sync reads and writes them (plan stage 4, steps 4.3-4.5,
 * Nov 2026):
 * - SUMMARY = the words; STATUS COMPLETED (or CANCELLED, or a COMPLETED time / 100 % without a
 *   status) = ticked; DUE = the due day (BooxDay has no due time); RELATED-TO (RELTYPE PARENT, the
 *   default) = the parent's UID; PRIORITY 1-4 / 5 / 6-9 = A / B / C.
 * - The reminder (owner decision 69): a reminder (VALARM) that rings on the due day, else a due time,
 *   is the task's own reminder time. BooxDay writes its own reminder time as a VALARM at that time
 *   on the due day and sends none for the usual time (the tablet's setting).
 * - A repeating task (RRULE, RDATE or day exceptions) is read only to be left out (decision 69).
 * Writing changes a field only when it differs from what the file says, so everything else (notes,
 * categories, start dates, other apps' properties) stays as the server has it.
 */
internal object CalDavTasks {
    private const val PRODID = "-//BooxDay//BooxDay//EN"

    /** A task file's fields as BooxDay reads them. */
    data class Read(
        val uid: String?,
        val title: String,
        val completed: Boolean,
        /** "yyyy-MM-dd", or null: undated. */
        val dueDay: String?,
        val parentUid: String?,
        /** "", "A", "B" or "C". */
        val priority: String,
        /** Minutes after midnight on the due day, or null: no reminder of its own. */
        val reminderMinute: Int?,
        val repeats: Boolean,
        /** LAST-MODIFIED (epoch ms), for "the later change wins". */
        val modifiedAt: Long?
    )

    /** What BooxDay writes. [priority] and [reminderMinute] only count with `extras` (always, for CalDAV). */
    data class Fields(
        val title: String,
        val completed: Boolean,
        val dueDay: String?,
        val parentUid: String?,
        val priority: String,
        val reminderMinute: Int?
    )

    /** Null: no task in the file, or unreadable. */
    fun read(ics: String, zone: ZoneId): Read? {
        val calendar = CalDavEvents.parse(ics) ?: return null
        val todos = calendar.todos()
        val todo = todos.master() ?: return null
        val due = dueOf(todo, zone)
        return Read(
            uid = todo.uid?.value,
            title = todo.summary?.value?.trim().orEmpty(),
            completed = completedOf(todo),
            dueDay = due?.day?.toString(),
            parentUid = parentOf(todo),
            priority = letter(todo.priority?.level ?: 0),
            reminderMinute = reminderOf(todo, due, zone),
            repeats = todos.size > 1 || todo.getProperty<Property>(Property.RRULE) != null ||
                todo.getProperty<Property>(Property.RDATE) != null,
            modifiedAt = todo.lastModified?.dateTime?.time
        )
    }

    /** A new task file. */
    fun newTask(uid: String, fields: Fields, zone: ZoneId, now: Long): String {
        val todo = VToDo().apply {
            properties.add(Uid(uid))
            properties.add(DtStamp(utc(now)))
            properties.add(Created(utc(now)))
            properties.add(Status.VTODO_NEEDS_ACTION)
        }
        val calendar = Calendar().apply {
            properties.add(ProdId(PRODID))
            properties.add(Version.VERSION_2_0)
            properties.add(CalScale.GREGORIAN)
            components.add(todo)
        }
        apply(todo, fields, zone, now)
        return write(calendar)
    }

    /** [ics] with BooxDay's fields set; the rest as it was. */
    fun edit(ics: String, fields: Fields, zone: ZoneId, now: Long): String {
        val calendar = checkNotNull(CalDavEvents.parse(ics)) { "Unreadable task file" }
        val todo = checkNotNull(calendar.todos().master()) { "No task in the file" }
        apply(todo, fields, zone, now)
        return write(calendar)
    }

    // ── Steps ──

    private fun apply(todo: VToDo, fields: Fields, zone: ZoneId, now: Long) {
        val props = todo.properties
        set(props, Property.SUMMARY, Summary(fields.title))
        if (completedOf(todo) != fields.completed) {
            if (fields.completed) {
                set(props, Property.STATUS, Status.VTODO_COMPLETED)
                set(props, Property.COMPLETED, Completed(utc(now)))
                set(props, Property.PERCENT_COMPLETE, PercentComplete(100))
            } else {
                set(props, Property.STATUS, Status.VTODO_NEEDS_ACTION)
                set(props, Property.COMPLETED, null)
                set(props, Property.PERCENT_COMPLETE, null)
            }
        }
        if (dueOf(todo, zone)?.day?.toString() != fields.dueDay) {
            set(props, Property.DURATION, null)  // DUE and DURATION can't both be there
            val day = fields.dueDay?.let(LocalDate::parse)
            set(props, Property.DUE, day?.let(::dueDate))
            // A start after the due day isn't allowed (RFC 5545 3.8.2.3): it goes
            if (day != null && startOf(todo, zone)?.toLocalDate()?.isAfter(day) == true) set(props, Property.DTSTART, null)
        }
        if (parentOf(todo) != fields.parentUid) {
            props.removeIf { it is RelatedTo && it.isParent() }
            fields.parentUid?.let { props.add(RelatedTo(ParameterList().apply { add(RelType.PARENT) }, it)) }
        }
        if (letter(todo.priority?.level ?: 0) != fields.priority) {
            set(props, Property.PRIORITY, level(fields.priority)?.let(::Priority))
        }
        val due = dueOf(todo, zone)
        val wanted = fields.reminderMinute?.takeIf { due != null }
        if (due != null && reminderOf(todo, due, zone) != wanted) {
            // The reminders on the due day give way to BooxDay's (none for the usual time); a due
            // time stood for the reminder too, so the due date keeps only its day
            // (alarms is a filtered copy: they live in the task's components)
            alarmsOnDueDay(todo, due, zone).forEach { todo.components.remove(it.first) }
            if (due.time != null) set(props, Property.DUE, dueDate(due.day))
            if (wanted != null) todo.components.add(alarm(due.day.atTime(wanted / 60, wanted % 60).atZone(zone), fields.title))
        }
        set(props, Property.DTSTAMP, DtStamp(utc(now)))
        set(props, Property.LAST_MODIFIED, LastModified(utc(now)))
    }

    private class DueAt(val day: LocalDate, val time: LocalTime?)

    /** The due day, and its time when DUE has one (a DATE-TIME on the device's clock). */
    private fun dueOf(todo: VToDo, zone: ZoneId): DueAt? {
        val date = todo.due?.date ?: return null
        return if (date is DateTime) {
            val at = Instant.ofEpochMilli(date.time).atZone(zone)
            DueAt(at.toLocalDate(), at.toLocalTime())
        } else {
            DueAt(LocalDate.parse(date.toString(), DateTimeFormatter.BASIC_ISO_DATE), null)
        }
    }

    private fun startOf(todo: VToDo, zone: ZoneId): ZonedDateTime? {
        val date = todo.startDate?.date ?: return null
        return if (date is DateTime) Instant.ofEpochMilli(date.time).atZone(zone)
        else LocalDate.parse(date.toString(), DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay(zone)
    }

    private fun completedOf(todo: VToDo): Boolean {
        val status = todo.status?.value
        if (status != null) return status.equals("COMPLETED", ignoreCase = true) || status.equals("CANCELLED", ignoreCase = true)
        return todo.dateCompleted != null || todo.percentComplete?.percentage == 100
    }

    private fun parentOf(todo: VToDo): String? =
        todo.getProperties<RelatedTo>(Property.RELATED_TO).firstOrNull { it.isParent() }?.value?.trim()?.takeIf { it.isNotEmpty() }

    private fun RelatedTo.isParent(): Boolean =
        getParameter<RelType>(Parameter.RELTYPE)?.value?.equals("PARENT", ignoreCase = true) ?: true

    /** A reminder that rings on the due day, else the due time, as minutes after midnight. */
    private fun reminderOf(todo: VToDo, due: DueAt?, zone: ZoneId): Int? {
        if (due == null) return null
        alarmsOnDueDay(todo, due, zone).firstOrNull()?.let { return it.second }
        return due.time?.let { it.hour * 60 + it.minute }
    }

    /** The reminders that ring on the due day, each with its minute of the day. */
    private fun alarmsOnDueDay(todo: VToDo, due: DueAt, zone: ZoneId): List<Pair<VAlarm, Int>> =
        todo.alarms.mapNotNull { alarm ->
            val trigger = alarm.trigger ?: return@mapNotNull null
            val at = trigger.dateTime?.let { Instant.ofEpochMilli(it.time).atZone(zone) }
                ?: trigger.duration?.let { amount ->
                    // RFC 5545 3.8.6.3: from the start, or with RELATED=END from the due date
                    val dueAt = due.day.atTime(due.time ?: LocalTime.MIDNIGHT).atZone(zone)
                    val base = if (trigger.getParameter<Related>(Parameter.RELATED) == Related.END) dueAt else startOf(todo, zone) ?: dueAt
                    runCatching { base.plus(amount) }.getOrNull()
                }
            at?.takeIf { it.toLocalDate() == due.day }?.let { alarm to it.hour * 60 + it.minute }
        }

    private fun alarm(at: ZonedDateTime, title: String) = VAlarm(utc(at.toInstant().toEpochMilli())).apply {
        properties.add(Action.DISPLAY)
        properties.add(Description(title.ifBlank { "Task" }))
    }

    /** RFC 5545 3.8.1.9: 1-4 high, 5 middle, 6-9 low, 0 none. */
    private fun letter(level: Int): String = when (level) {
        in 1..4 -> "A"
        5 -> "B"
        in 6..9 -> "C"
        else -> ""
    }

    private fun level(letter: String): Int? = when (letter) {
        "A" -> 1
        "B" -> 5
        "C" -> 9
        else -> null
    }

    /** Written as text, as the server's files are read: building ical4j dates in code shifts a day by the device's time zone. */
    private fun dueDate(day: LocalDate) = Due(ParameterList().apply { add(Value.DATE) }, day.format(DateTimeFormatter.BASIC_ISO_DATE))

    private fun set(props: PropertyList<Property>, name: String, value: Property?) {
        props.removeIf { it.name == name }
        if (value != null) props.add(value)
    }

    private fun utc(ms: Long) = DateTime(ms).apply { isUtc = true }

    private fun Calendar.todos(): List<VToDo> = getComponents<VToDo>(Component.VTODO)

    /** The task itself, not one of its day exceptions. */
    private fun List<VToDo>.master(): VToDo? =
        firstOrNull { it.getProperty<Property>(Property.RECURRENCE_ID) == null } ?: firstOrNull()

    private fun write(calendar: Calendar): String = StringWriter().also { CalendarOutputter(false).output(calendar, it) }.toString()
}

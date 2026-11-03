package com.boox.day.sync.caldav

import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.ComponentList
import net.fortuna.ical4j.model.Date
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
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Due
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.LastModified
import net.fortuna.ical4j.model.property.PercentComplete
import net.fortuna.ical4j.model.property.Priority
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RecurrenceId
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
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * CalDAV tasks (VTODO, RFC 5545) as task sync reads and writes them (plan stage 4, steps 4.3-4.5,
 * Nov 2026):
 * - SUMMARY = the words; STATUS COMPLETED (or CANCELLED, or a COMPLETED time / 100 % without a
 *   status) = ticked; DUE = the due day (BooxDay has no due time); RELATED-TO (RELTYPE PARENT, the
 *   default) = the parent's UID; PRIORITY 1-4 / 5 / 6-9 = A / B / C.
 * - The reminder (owner decision 69): a reminder (VALARM) that rings on the due day, else a due time,
 *   is the task's own reminder time. BooxDay writes its own reminder time as a VALARM at that time
 *   on the due day and sends none for the usual time (the tablet's setting).
 * - A repeating task (RRULE or RDATE) comes as one task per due day (owner decision 76, Nov 2026,
 *   Thunderbird's way): [occurrences] gives the days, each with its own day exception
 *   (RECURRENCE-ID) over the series; ticking or changing one day writes that exception
 *   ([editOccurrence]), clearing it adds an EXDATE ([removeOccurrence]). Tasks.org moves the series
 *   on instead, and reads such exceptions badly (the owner chose this way knowing it).
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

    // ── Repeating tasks (owner decision 76) ──

    /**
     * One due day of a repeating task: [key] names the day as its RECURRENCE-ID does ("yyyyMMdd", or
     * a UTC time "yyyyMMddTHHmmssZ", as [CalDavEvents] keys event days), [read] its fields: the
     * series' with that day's exception over them.
     */
    data class Occurrence(val key: String, val read: Read)

    /** The days of a repeating task due from [from] to [to]; null when [ics] isn't a repeating task. */
    fun occurrences(ics: String, zone: ZoneId, from: LocalDate, to: LocalDate): List<Occurrence>? {
        val todos = CalDavEvents.parse(ics)?.todos() ?: return null
        val series = Series.of(todos, zone) ?: return null
        val keys = LinkedHashSet(series.keysDue(from, to)).apply { addAll(series.exceptions.keys) }
        return keys.mapNotNull { key ->
            series.at(key)?.takeIf { read -> read.dueDay != null && LocalDate.parse(read.dueDay) in from..to }?.let { Occurrence(key, it) }
        }
    }

    /** The day [key] of a repeating task, or null when it isn't one (removed with EXDATE, or not in its rule). */
    fun occurrence(ics: String, zone: ZoneId, key: String): Read? {
        val todos = CalDavEvents.parse(ics)?.todos() ?: return null
        val series = Series.of(todos, zone) ?: return null
        if (key !in series.exceptions && !series.repeatsOn(key)) return null
        return series.at(key)
    }

    /** [ics] with BooxDay's fields set on the day [key] of the series: its exception, made from the series when it has none. */
    fun editOccurrence(ics: String, key: String, fields: Fields, zone: ZoneId, now: Long): String {
        val calendar = checkNotNull(CalDavEvents.parse(ics)) { "Unreadable task file" }
        val series = checkNotNull(Series.of(calendar.todos(), zone)) { "Not a repeating task" }
        val exception = series.exceptions[key] ?: series.exceptionFor(key).also { calendar.components.add(it) }
        apply(exception, fields, zone, now)
        return write(calendar)
    }

    /** [ics] without the day [key]: its exception goes and the series gets an EXDATE (as Thunderbird deletes one day). */
    fun removeOccurrence(ics: String, key: String, now: Long): String {
        val calendar = checkNotNull(CalDavEvents.parse(ics)) { "Unreadable task file" }
        val todos = calendar.todos()
        todos.filter { it.recurrenceKey() == key }.forEach { calendar.components.remove(it) }
        val master = checkNotNull(todos.firstOrNull { it.recurrenceId == null }) { "No series in the file" }
        master.properties.add(ExDate(dayParams(key), key))
        set(master.properties, Property.DTSTAMP, DtStamp(utc(now)))
        set(master.properties, Property.LAST_MODIFIED, LastModified(utc(now)))
        return write(calendar)
    }

    /** A repeating task's series and its day exceptions. */
    private class Series(val master: VToDo, val exceptions: Map<String, VToDo>, val zone: ZoneId) {
        companion object {
            fun of(todos: List<VToDo>, zone: ZoneId): Series? {
                val master = todos.firstOrNull { it.recurrenceId == null } ?: return null
                if (master.getProperties<Property>(Property.RRULE).isEmpty() && master.getProperties<Property>(Property.RDATE).isEmpty()) return null
                if (seedOf(master) == null) return null
                return Series(master, todos.mapNotNull { todo -> todo.recurrenceKey()?.let { it to todo } }.toMap(), zone)
            }

            /** RFC 5545: the rule counts from DTSTART; tasks often have only DUE, which then stands for it. */
            fun seedOf(todo: VToDo): Date? = todo.startDate?.date ?: todo.due?.date
        }

        private val seed: Date = checkNotNull(seedOf(master))

        /** Days from a day's start to its due day (0 without DTSTART). */
        private val dueOffset: Long = master.startDate?.date?.let { start ->
            master.due?.date?.let { due -> ChronoUnit.DAYS.between(dayOf(start), dayOf(due)) }
        } ?: 0L

        private val exDates: List<Date> = master.getProperties<ExDate>(Property.EXDATE).flatMap { it.dates.orEmpty() }
        private val excluded: Set<String> = exDates.map { CalDavEvents.key(it) }.toSet()
        private val excludedDays: Set<LocalDate> = exDates.map { dayOf(it) }.toSet()

        /** The days (keys) whose due day falls from [from] to [to], without the removed ones. */
        fun keysDue(from: LocalDate, to: LocalDate): List<String> =
            starts(from.minusDays(dueOffset + 1), to.minusDays(dueOffset - 1)).keys.filter { !isExcluded(it) }

        fun repeatsOn(key: String): Boolean {
            if (isExcluded(key)) return false
            val day = keyDay(key)
            return key in starts(day.minusDays(1), day.plusDays(1))
        }

        /** The fields of day [key]: its exception's, else the series' moved to that day. */
        fun at(key: String): Read {
            val exception = exceptions[key]
            val ownDue = exception?.let { dueOf(it, zone) }
            val day = ownDue?.day ?: keyDay(key).plusDays(dueOffset)
            return Read(
                uid = master.uid?.value,
                title = (exception?.summary ?: master.summary)?.value?.trim().orEmpty(),
                completed = if (exception != null) completedOf(exception) else completedOf(master),
                dueDay = day.toString(),
                parentUid = null,
                priority = letter((exception?.priority ?: master.priority)?.level ?: 0),
                reminderMinute = if (exception != null) reminderOf(exception, ownDue, zone) else reminderOf(master, dueOf(master, zone), zone),
                repeats = true,
                modifiedAt = (exception?.lastModified ?: master.lastModified)?.dateTime?.time
            )
        }

        /** A copy of the series for day [key]: same fields and reminders, its own start and due, no repeat rules. */
        fun exceptionFor(key: String): VToDo {
            val props = PropertyList<Property>(master.properties)
            listOf(Property.RRULE, Property.RDATE, Property.EXDATE, Property.EXRULE, Property.RECURRENCE_ID, Property.DURATION)
                .forEach { name -> props.removeIf { it.name == name } }
            props.add(RecurrenceId(dayParams(key), key))
            if (master.startDate != null) {
                props.removeIf { it.name == Property.DTSTART }
                props.add(DtStart(dayParams(key), key))
            }
            val masterDue = master.due?.date
            if (masterDue != null) {
                props.removeIf { it.name == Property.DUE }
                props.add(
                    if (masterDue is DateTime && key.length > 8) {
                        // A due time stays a time: the day's start plus the series' start-to-due span
                        val span = master.startDate?.date?.let { masterDue.time - it.time } ?: 0L
                        Due(ParameterList(), UTC_KEY.format(Instant.from(UTC_KEY.parse(key)).plusMillis(span)))
                    } else {
                        dueDate(keyDay(key).plusDays(dueOffset))
                    }
                )
            }
            val alarms = ComponentList<VAlarm>().apply { master.alarms.forEach { add(it.copy<VAlarm>()) } }
            return VToDo(props).also { todo -> alarms.forEach { todo.components.add(it) } }
        }

        private fun isExcluded(key: String) = key in excluded || keyDay(key) in excludedDays

        /** Every start of the series from [from] to [to], keyed: the seed itself, its RRULEs and RDATEs. */
        private fun starts(from: LocalDate, to: LocalDate): Map<String, Date> {
            val value = if (seed is DateTime) Value.DATE_TIME else Value.DATE
            val near = DateTime(from.atStartOfDay(zone).toInstant().toEpochMilli()).apply { isUtc = true }
            val far = DateTime(to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()).apply { isUtc = true }
            val found = LinkedHashMap<String, Date>()
            fun take(date: Date) {
                if (dayOf(date) in from..to) found.putIfAbsent(CalDavEvents.key(date), date)
            }
            take(seed)
            for (rule in master.getProperties<RRule>(Property.RRULE)) {
                rule.recur.getDates(seed, near, far, value, MAX_DAYS).forEach { take(it) }
            }
            for (rdate in master.getProperties<RDate>(Property.RDATE)) {
                rdate.dates?.forEach { take(it) }
                rdate.periods?.forEach { period -> period.start?.let { take(it) } }
            }
            return found
        }

        private fun dayOf(date: Date): LocalDate =
            if (date is DateTime) Instant.ofEpochMilli(date.time).atZone(zone).toLocalDate()
            else LocalDate.parse(date.toString(), DateTimeFormatter.BASIC_ISO_DATE)

        private fun keyDay(key: String): LocalDate =
            if (key.length == 8) LocalDate.parse(key, DateTimeFormatter.BASIC_ISO_DATE)
            else Instant.from(UTC_KEY.parse(key)).atZone(zone).toLocalDate()
    }

    private const val MAX_DAYS = 400
    private val UTC_KEY = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    /** VALUE=DATE for a day key ("yyyyMMdd"), none for a UTC time. */
    private fun dayParams(key: String) = ParameterList().apply { if (key.length == 8) add(Value.DATE) }

    private fun VToDo.recurrenceKey(): String? = recurrenceId?.date?.let { CalDavEvents.key(it) }

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

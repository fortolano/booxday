package com.boox.day.sleepscreen

import kotlin.math.max
import kotlin.math.min

/**
 * Size classes of the sleep-screen agenda, by the panel's physical short side (Oct 2026).
 * Go 6 and Go 7 are [COMPACT], Go 10.3 is [MEDIUM], Note Max (13.3") is [LARGE].
 */
enum class SleepSizeClass { COMPACT, MEDIUM, LARGE;

    companion object {
        fun of(shortSidePx: Int, ppi: Int): SleepSizeClass {
            val inches = shortSidePx.toFloat() / ppi
            return when {
                inches < 4.6f -> COMPACT
                inches < 7f -> MEDIUM
                else -> LARGE
            }
        }
    }
}

/**
 * Type sizes (points) and spacing (millimetres) for one size class. Set in physical units so text
 * has the same real size on every tablet; [px] and [mm] turn them into pixels.
 */
data class SleepAgendaStyle(
    val sizeClass: SleepSizeClass,
    val ppi: Int,
    val numeralPt: Float,
    val weekdayPt: Float,
    val monthPt: Float,
    val labelPt: Float,
    val bodyPt: Float,
    val secondaryPt: Float,
    val marginMm: Float,
    val gapMm: Float,
) {
    fun px(pt: Float): Float = pt * ppi / 72f
    fun mm(mm: Float): Float = mm * ppi / 25.4f

    /** Line widths: hairlines for frames and the timeline, a heavy rule under the header. */
    val hairline: Float get() = max(2f, ppi / 150f)
    val heavy: Float get() = max(4f, ppi / 75f)

    companion object {
        fun forClass(sizeClass: SleepSizeClass, ppi: Int) = when (sizeClass) {
            SleepSizeClass.COMPACT -> SleepAgendaStyle(sizeClass, ppi, 44f, 16f, 11f, 8.5f, 11.5f, 9.5f, 6f, 3.5f)
            SleepSizeClass.MEDIUM -> SleepAgendaStyle(sizeClass, ppi, 72f, 22f, 14f, 10f, 14f, 11f, 9f, 6f)
            SleepSizeClass.LARGE -> SleepAgendaStyle(sizeClass, ppi, 92f, 26f, 16f, 11f, 15.5f, 12f, 11f, 7f)
        }
    }
}

/**
 * What fits on the sleep screen: pure arithmetic on row heights, so the dropping order can be
 * tested without drawing. Things are given up in a fixed order until both columns fit:
 * next days, memo panel, event locations, done tasks (become a count), events already over
 * (become a count), subtasks; then each list keeps what fits and ends with "+N more".
 * The date header, the footer and at least one row of each list are never given up.
 */
object SleepAgendaLayout {

    /** Heights in pixels, measured by the renderer from its fonts. */
    data class Metrics(
        val sectionHeader: Float,
        val row: Float,
        /** Extra height of an event's location line. */
        val secondLine: Float,
        val allDayRow: Float,
        val note: Float,
        val memoPanelMin: Float,
        val nextDaysPanel: Float,
        /** The month calendar: shown only in space nothing else needs. */
        val monthPanel: Float,
        val gap: Float,
    )

    /** What there is to show, counted. */
    data class Content(
        val allDayEvents: Int,
        val pastTimedEvents: Int,
        val comingTimedEvents: Int,
        val eventsWithLocation: Int,
        val openTasks: Int,
        val doneTasks: Int,
        val subtasks: Int,
        val hasMemo: Boolean,
        val hasNextDays: Boolean,
    )

    data class Plan(
        val showNextDays: Boolean,
        val showMemo: Boolean,
        val showLocations: Boolean,
        val collapseDone: Boolean,
        val collapsePast: Boolean,
        val hideSubtasks: Boolean,
        /** Timed event rows drawn (past ones included unless collapsed); null = all. */
        val eventRows: Int?,
        /** Task rows drawn; null = all. */
        val taskRows: Int?,
        /** Height left for the memo panel (0 when not shown). */
        val memoHeight: Float,
        /** A month calendar at the bottom, on days light enough to leave room for it. */
        val showMonth: Boolean = false,
    )

    /**
     * [bodyHeight] is the space between header and footer. Two columns (medium, large) share it
     * side by side, with the memo panel and the next days strip under them; compact stacks the
     * schedule over the tasks.
     */
    fun plan(sizeClass: SleepSizeClass, content: Content, m: Metrics, bodyHeight: Float): Plan {
        val twoColumns = sizeClass != SleepSizeClass.COMPACT
        var plan = Plan(
            showNextDays = content.hasNextDays && sizeClass == SleepSizeClass.LARGE,
            showMemo = content.hasMemo && twoColumns,
            showLocations = true,
            collapseDone = false,
            collapsePast = false,
            hideSubtasks = false,
            eventRows = null,
            taskRows = null,
            memoHeight = 0f,
        )
        val steps: List<(Plan) -> Plan> = listOf(
            { it.copy(showNextDays = false) },
            { it.copy(showMemo = false) },
            { it.copy(showLocations = false) },
            { it.copy(collapseDone = true) },
            { it.copy(collapsePast = true) },
            { it.copy(hideSubtasks = true) },
        )
        var step = 0
        while (true) {
            if (fits(sizeClass, content, m, bodyHeight, plan)) break
            if (step == steps.size) return trim(sizeClass, content, m, bodyHeight, plan)
            plan = steps[step++](plan)
        }
        // The month calendar only takes room nothing else needs: the memo panel at its smallest stays
        val showMonth = freeHeight(sizeClass, content, m, bodyHeight, plan) >= m.monthPanel + m.gap
        val monthSpace = if (showMonth) m.monthPanel + m.gap else 0f
        return plan.copy(
            showMonth = showMonth,
            memoHeight = if (plan.showMemo) memoHeight(content, m, bodyHeight - monthSpace, plan) else 0f,
        )
    }

    private fun fits(sizeClass: SleepSizeClass, c: Content, m: Metrics, body: Float, p: Plan): Boolean {
        val schedule = scheduleHeight(c, m, p, eventRows = null)
        val tasks = tasksHeight(c, m, p, taskRows = null)
        return if (sizeClass == SleepSizeClass.COMPACT) {
            schedule + m.gap + tasks <= body
        } else {
            val below = belowColumns(m, p, c, body)
            max(schedule, tasks) <= body - below
        }
    }

    /** Space under the two columns: next days strip, and the memo panel at its smallest. */
    private fun belowColumns(m: Metrics, p: Plan, c: Content, body: Float): Float {
        var h = 0f
        if (p.showNextDays) h += m.nextDaysPanel + m.gap
        if (p.showMemo) h += m.memoPanelMin + m.gap
        return h
    }

    /** Height left over when everything planned is drawn at its smallest. */
    private fun freeHeight(sizeClass: SleepSizeClass, c: Content, m: Metrics, body: Float, p: Plan): Float {
        val schedule = scheduleHeight(c, m, p, null)
        val tasks = tasksHeight(c, m, p, null)
        return if (sizeClass == SleepSizeClass.COMPACT) body - schedule - m.gap - tasks
        else body - max(schedule, tasks) - belowColumns(m, p, c, body)
    }

    /** The memo panel takes what the columns leave, from its minimum up to 45% of the body. */
    private fun memoHeight(c: Content, m: Metrics, body: Float, p: Plan): Float {
        val columns = max(scheduleHeight(c, m, p, null), tasksHeight(c, m, p, null))
        val nextDays = if (p.showNextDays) m.nextDaysPanel + m.gap else 0f
        val free = body - columns - nextDays - m.gap
        return min(max(free, m.memoPanelMin), body * 0.45f)
    }

    /** Lists trimmed to what fits, each ending with "+N more". */
    private fun trim(sizeClass: SleepSizeClass, c: Content, m: Metrics, body: Float, p: Plan): Plan {
        val eventTotal = timedRows(c, p)
        val taskTotal = taskRowsTotal(c, p)
        var events = eventTotal
        var tasks = taskTotal
        fun schedule() = scheduleHeight(c, m, p, events)
        fun taskList() = tasksHeight(c, m, p, tasks)
        if (sizeClass == SleepSizeClass.COMPACT) {
            // Take a row from the longer list until both fit stacked
            while (schedule() + m.gap + taskList() > body && (events > 1 || tasks > 1)) {
                if (events >= tasks && events > 1) events-- else if (tasks > 1) tasks-- else events--
            }
        } else {
            while (schedule() > body && events > 1) events--
            while (taskList() > body && tasks > 1) tasks--
        }
        return p.copy(
            eventRows = if (events < eventTotal) events else null,
            taskRows = if (tasks < taskTotal) tasks else null,
            memoHeight = 0f,
        )
    }

    private fun timedRows(c: Content, p: Plan): Int =
        c.comingTimedEvents + if (p.collapsePast) (if (c.pastTimedEvents > 0) 1 else 0) else c.pastTimedEvents

    private fun taskRowsTotal(c: Content, p: Plan): Int {
        val open = c.openTasks + if (p.hideSubtasks) 0 else c.subtasks
        val done = if (p.collapseDone) (if (c.doneTasks > 0) 1 else 0) else c.doneTasks
        return open + done
    }

    /** [eventRows] = timed rows drawn (one of them "+N more" when cut); null = all. */
    internal fun scheduleHeight(c: Content, m: Metrics, p: Plan, eventRows: Int?): Float {
        var h = m.sectionHeader
        if (c.allDayEvents > 0) h += m.allDayRow
        val rows = eventRows ?: timedRows(c, p)
        if (rows == 0 && c.allDayEvents == 0) return h + m.note
        h += rows * m.row
        if (p.showLocations && eventRows == null) h += c.eventsWithLocation * m.secondLine
        return h
    }

    internal fun tasksHeight(c: Content, m: Metrics, p: Plan, taskRows: Int?): Float {
        val rows = taskRows ?: taskRowsTotal(c, p)
        return m.sectionHeader + if (rows == 0) m.note else rows * m.row
    }
}

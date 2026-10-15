package com.boox.day.ai

import com.boox.day.data.ai.BlockedSlot
import com.boox.day.data.ai.CapacityAnalysis
import com.boox.day.data.ai.FreeSlot
import com.boox.day.data.ai.PreferenceConstraints
import com.boox.day.data.db.LocalEvent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.max

/**
 * Analyzes a day's capacity based on events and preferences.
 *
 * Calculates:
 * - Total available minutes (based on work hours)
 * - Committed minutes (from calendar events)
 * - Free time slots (gaps >= 30 minutes)
 * - Capacity percentage
 */
object CapacityAnalyzer {

    /** Minimum slot duration to consider useful (minutes) */
    private const val MIN_SLOT_DURATION = 30

    /** Default work day start hour if no preferences */
    private const val DEFAULT_DAY_START = 6

    /** Default work day end hour if no preferences */
    private const val DEFAULT_DAY_END = 22

    /**
     * Analyze capacity for a specific date.
     *
     * @param date The date to analyze
     * @param events Calendar events for that date
     * @param preferences User preference constraints
     * @param taskLoadEstimate Estimated minutes needed for tasks
     * @return CapacityAnalysis with all metrics
     */
    fun analyze(
        date: LocalDate,
        events: List<LocalEvent>,
        preferences: PreferenceConstraints,
        taskLoadEstimate: Int = 0
    ): CapacityAnalysis {
        // Determine work hours
        val dayStartHour = preferences.workHoursStart.coerceIn(0, 23)
        val dayEndHour = preferences.workHoursEnd.coerceIn(dayStartHour + 1, 24)

        // Total available minutes
        val totalMinutes = (dayEndHour - dayStartHour) * 60

        // Build list of busy intervals (from events + blocked slots)
        val busyIntervals = buildBusyIntervals(events, preferences.blockedSlots, date, dayStartHour, dayEndHour)

        // Merge overlapping intervals
        val mergedIntervals = mergeIntervals(busyIntervals)

        // Calculate committed time
        val committedMinutes = mergedIntervals.sumOf { it.end - it.start }

        // Find free slots
        val freeSlots = findFreeSlots(mergedIntervals, dayStartHour, dayEndHour)

        // Calculate metrics
        val freeMinutes = totalMinutes - committedMinutes
        val capacityPercentage = if (totalMinutes > 0) {
            (committedMinutes.toFloat() / totalMinutes) * 100
        } else {
            0f
        }

        return CapacityAnalysis(
            date = date,
            totalMinutes = totalMinutes,
            committedMinutes = committedMinutes,
            freeMinutes = max(0, freeMinutes),
            freeSlots = freeSlots,
            capacityPercentage = capacityPercentage,
            isOverbooked = capacityPercentage >= 100,
            taskLoadEstimate = taskLoadEstimate
        )
    }

    /**
     * Build list of busy time intervals from events and blocked slots.
     */
    private fun buildBusyIntervals(
        events: List<LocalEvent>,
        blockedSlots: List<BlockedSlot>,
        date: LocalDate,
        dayStartHour: Int,
        dayEndHour: Int
    ): List<Interval> {
        val intervals = mutableListOf<Interval>()

        // Add event intervals
        for (event in events) {
            if (event.isAllDay) {
                // All-day event blocks the entire day
                intervals.add(Interval(dayStartHour * 60, dayEndHour * 60))
            } else {
                // Convert epoch millis to minutes since day start
                val startMinutes = epochToMinutesSinceMidnight(event.startTime)
                val endMinutes = if (event.endTime != null) {
                    epochToMinutesSinceMidnight(event.endTime)
                } else {
                    startMinutes + 60  // Default 1 hour if no end time
                }

                // Clamp to day boundaries
                val clampedStart = startMinutes.coerceIn(dayStartHour * 60, dayEndHour * 60)
                val clampedEnd = endMinutes.coerceIn(dayStartHour * 60, dayEndHour * 60)

                if (clampedEnd > clampedStart) {
                    intervals.add(Interval(clampedStart, clampedEnd))
                }
            }
        }

        // Add blocked slots
        for (slot in blockedSlots) {
            val slotStartMinutes = slot.startHour * 60
            val slotEndMinutes = slot.endHour * 60

            val clampedStart = slotStartMinutes.coerceIn(dayStartHour * 60, dayEndHour * 60)
            val clampedEnd = slotEndMinutes.coerceIn(dayStartHour * 60, dayEndHour * 60)

            if (clampedEnd > clampedStart) {
                intervals.add(Interval(clampedStart, clampedEnd))
            }
        }

        return intervals.sortedBy { it.start }
    }

    /**
     * Merge overlapping intervals.
     */
    private fun mergeIntervals(intervals: List<Interval>): List<Interval> {
        if (intervals.isEmpty()) return emptyList()

        val sorted = intervals.sortedBy { it.start }
        val merged = mutableListOf<Interval>()

        var current = sorted[0]

        for (i in 1 until sorted.size) {
            val next = sorted[i]
            if (next.start <= current.end) {
                // Overlapping - extend current
                current = Interval(current.start, max(current.end, next.end))
            } else {
                // Non-overlapping - add current and move to next
                merged.add(current)
                current = next
            }
        }
        merged.add(current)

        return merged
    }

    /**
     * Find free slots between busy intervals.
     */
    private fun findFreeSlots(
        busyIntervals: List<Interval>,
        dayStartHour: Int,
        dayEndHour: Int
    ): List<FreeSlot> {
        val slots = mutableListOf<FreeSlot>()
        val dayStartMinutes = dayStartHour * 60
        val dayEndMinutes = dayEndHour * 60

        var currentMinute = dayStartMinutes

        for (busy in busyIntervals) {
            if (busy.start > currentMinute) {
                val gapDuration = busy.start - currentMinute
                if (gapDuration >= MIN_SLOT_DURATION) {
                    slots.add(createFreeSlot(currentMinute, busy.start))
                }
            }
            currentMinute = max(currentMinute, busy.end)
        }

        // Check for gap after last busy interval
        if (dayEndMinutes > currentMinute) {
            val gapDuration = dayEndMinutes - currentMinute
            if (gapDuration >= MIN_SLOT_DURATION) {
                slots.add(createFreeSlot(currentMinute, dayEndMinutes))
            }
        }

        return slots
    }

    /**
     * Create a FreeSlot from minute values.
     */
    private fun createFreeSlot(startMinutes: Int, endMinutes: Int): FreeSlot {
        val startHour = startMinutes / 60
        val startMin = startMinutes % 60
        val endHour = endMinutes / 60
        val endMin = endMinutes % 60

        return FreeSlot(
            startTime = String.format("%02d:%02d", startHour, startMin),
            endTime = String.format("%02d:%02d", endHour, endMin),
            durationMinutes = endMinutes - startMinutes,
            label = FreeSlot.getLabelForHour(startHour)
        )
    }

    /**
     * Convert epoch milliseconds to minutes since midnight.
     */
    private fun epochToMinutesSinceMidnight(epochMillis: Long): Int {
        val instant = Instant.ofEpochMilli(epochMillis)
        val localTime = LocalTime.ofInstant(instant, ZoneId.systemDefault())
        return localTime.hour * 60 + localTime.minute
    }

    /**
     * Find the best slot for a task of given duration.
     * Prefers slots during peak productivity hours.
     */
    fun findBestSlot(
        capacity: CapacityAnalysis,
        durationMinutes: Int,
        peakHours: Pair<Int, Int>?
    ): FreeSlot? {
        val suitableSlots = capacity.freeSlots
            .filter { it.durationMinutes >= durationMinutes }

        if (suitableSlots.isEmpty()) return null

        // If no peak hours preference, return first suitable slot
        if (peakHours == null) return suitableSlots.first()

        // Prefer slots that overlap with peak hours
        val (peakStart, peakEnd) = peakHours

        val peakSlot = suitableSlots.find { slot ->
            val slotStart = slot.getStartTimeLocal()?.hour ?: 0
            slotStart >= peakStart && slotStart < peakEnd
        }

        return peakSlot ?: suitableSlots.first()
    }

    /**
     * Check if user is at or over capacity.
     */
    fun isAtCapacity(capacity: CapacityAnalysis): Boolean {
        return capacity.capacityPercentage >= 80
    }

    /**
     * Get summary text for capacity status.
     */
    fun getCapacitySummary(capacity: CapacityAnalysis): String {
        val percentage = capacity.capacityPercentage.toInt()
        val freeHours = capacity.freeMinutes / 60
        val freeMinutesRemainder = capacity.freeMinutes % 60

        return when {
            percentage >= 120 -> "Severely overbooked at $percentage% capacity"
            percentage >= 100 -> "Overbooked at $percentage% capacity"
            percentage >= 80 -> "Busy day at $percentage% capacity (~${freeHours}h ${freeMinutesRemainder}m free)"
            percentage >= 50 -> "Moderate load at $percentage% capacity (~${freeHours}h ${freeMinutesRemainder}m free)"
            else -> "Light day at $percentage% capacity (~${freeHours}h ${freeMinutesRemainder}m free)"
        }
    }

    /**
     * Internal interval representation.
     */
    private data class Interval(
        val start: Int,  // Minutes since midnight
        val end: Int     // Minutes since midnight
    )
}

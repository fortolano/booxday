package com.boox.day.data

import android.util.Log
import com.boox.day.PERF_LOGS
import com.boox.day.data.db.BooxNoteRepository
import com.boox.day.data.db.CalendarEventRepository
import com.boox.day.data.db.DayPageRepository
import com.boox.day.data.db.LocalEventRepository
import com.boox.day.data.db.TaskRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

private const val TAG = "CacheWarmupCoordinator"

/**
 * Result of parallel cache warmup containing badge data for all content types.
 */
data class CacheWarmupResult(
    val datesWithNotes: Set<String>,
    val datesWithTasks: Set<String>,
    val datesWithEvents: Set<String>,
    val datesWithBooxNotes: Set<String>
)

/**
 * Coordinates parallel cache warming for all repositories when the viewing month changes.
 *
 * This replaces individual sequential preWarm calls with a single parallel operation,
 * reducing total warmup time from sequential sum to max of individual operations.
 */
class CacheWarmupCoordinator(
    private val dayPageRepository: DayPageRepository,
    private val localEventRepository: LocalEventRepository,
    private val calendarEventRepository: CalendarEventRepository,  // Google/Outlook events
    private val taskRepository: TaskRepository,
    private val booxNoteRepository: BooxNoteRepository
) {
    /**
     * Warm up all caches for a month in parallel and return combined badge data.
     * Also fetches adjacent month data for calendar grid overflow days (prev/next month
     * days that appear in the current month's view).
     *
     * @param yearMonth Format: "yyyy-MM" (e.g., "2025-12")
     * @return CacheWarmupResult containing badge dates from current + adjacent months
     */
    suspend fun warmupForMonth(yearMonth: String): CacheWarmupResult = coroutineScope {
        val start = System.currentTimeMillis()
        if (PERF_LOGS) Log.i(TAG, "PERF: warmupForMonth START for $yearMonth (including overflow days)")

        // Calculate adjacent months for calendar grid overflow days
        val (prevMonth, nextMonth) = calculateAdjacentMonths(yearMonth)

        // Launch all warmup operations in parallel (current + adjacent months)
        // Current month - notes
        val notesDeferred = async {
            dayPageRepository.preWarmCacheForMonth(yearMonth)
            dayPageRepository.getDatesWithNotesForMonthSync(yearMonth).toSet()
        }
        // Adjacent months - notes (for overflow days)
        val notesPrevDeferred = async { dayPageRepository.getDatesWithNotesForMonthSync(prevMonth).toSet() }
        val notesNextDeferred = async { dayPageRepository.getDatesWithNotesForMonthSync(nextMonth).toSet() }

        // Current month - local events (device-only)
        val localEventsDeferred = async { localEventRepository.preWarmCacheForMonth(yearMonth) }
        // Adjacent months - local events
        val localEventsPrevDeferred = async { localEventRepository.preWarmCacheForMonth(prevMonth) }
        val localEventsNextDeferred = async { localEventRepository.preWarmCacheForMonth(nextMonth) }

        // Current month - cloud events (Google/Outlook)
        val cloudEventsDeferred = async { calendarEventRepository.preWarmCacheForMonth(yearMonth) }
        // Adjacent months - cloud events
        val cloudEventsPrevDeferred = async { calendarEventRepository.preWarmCacheForMonth(prevMonth) }
        val cloudEventsNextDeferred = async { calendarEventRepository.preWarmCacheForMonth(nextMonth) }

        // Current month - tasks
        val tasksDeferred = async { taskRepository.preWarmCacheForMonth(yearMonth) }
        // Adjacent months - tasks
        val tasksPrevDeferred = async { taskRepository.preWarmCacheForMonth(prevMonth) }
        val tasksNextDeferred = async { taskRepository.preWarmCacheForMonth(nextMonth) }

        // Current month - Boox notes
        val booxDeferred = async {
            booxNoteRepository.preWarmLinksCacheForMonth(yearMonth)
            booxNoteRepository.getDatesWithNotesForMonth(yearMonth)
        }
        // Adjacent months - Boox notes
        val booxPrevDeferred = async { booxNoteRepository.getDatesWithNotesForMonth(prevMonth) }
        val booxNextDeferred = async { booxNoteRepository.getDatesWithNotesForMonth(nextMonth) }

        // Await all results and combine current + adjacent month data
        val localEventDates = localEventsDeferred.await() + localEventsPrevDeferred.await() + localEventsNextDeferred.await()
        val cloudEventDates = cloudEventsDeferred.await() + cloudEventsPrevDeferred.await() + cloudEventsNextDeferred.await()

        val result = CacheWarmupResult(
            datesWithNotes = notesDeferred.await() + notesPrevDeferred.await() + notesNextDeferred.await(),
            datesWithTasks = tasksDeferred.await() + tasksPrevDeferred.await() + tasksNextDeferred.await(),
            datesWithEvents = localEventDates + cloudEventDates,
            datesWithBooxNotes = booxDeferred.await() + booxPrevDeferred.await() + booxNextDeferred.await()
        )

        if (PERF_LOGS) {
            Log.i(
                TAG,
                "PERF: warmupForMonth COMPLETE for $yearMonth (incl. overflow) - " +
                    "notes=${result.datesWithNotes.size}, " +
                    "tasks=${result.datesWithTasks.size}, " +
                    "events=${result.datesWithEvents.size}, " +
                    "booxNotes=${result.datesWithBooxNotes.size} " +
                    "(${System.currentTimeMillis() - start}ms)"
            )
        }

        result
    }

    /**
     * Warm a single month WITHOUT overflow days.
     * Used by warmupAdjacentMonths to avoid re-querying months already covered.
     */
    private suspend fun warmupForMonthExclusive(yearMonth: String): CacheWarmupResult = coroutineScope {
        val notesDeferred = async {
            dayPageRepository.preWarmCacheForMonth(yearMonth)
            dayPageRepository.getDatesWithNotesForMonthSync(yearMonth).toSet()
        }
        val localEventsDeferred = async { localEventRepository.preWarmCacheForMonth(yearMonth) }
        val cloudEventsDeferred = async { calendarEventRepository.preWarmCacheForMonth(yearMonth) }
        val tasksDeferred = async { taskRepository.preWarmCacheForMonth(yearMonth) }
        val booxDeferred = async {
            booxNoteRepository.preWarmLinksCacheForMonth(yearMonth)
            booxNoteRepository.getDatesWithNotesForMonth(yearMonth)
        }

        CacheWarmupResult(
            datesWithNotes = notesDeferred.await(),
            datesWithTasks = tasksDeferred.await(),
            datesWithEvents = localEventsDeferred.await() + cloudEventsDeferred.await(),
            datesWithBooxNotes = booxDeferred.await()
        )
    }

    /**
     * Calculate previous and next month strings from a yearMonth.
     * @return Pair of (prevMonth, nextMonth) in "yyyy-MM" format
     */
    private fun calculateAdjacentMonths(yearMonth: String): Pair<String, String> {
        val parts = yearMonth.split("-")
        val year = parts[0].toInt()
        val month = parts[1].toInt()

        val prevMonth = if (month == 1) "${year - 1}-12"
                        else "$year-${(month - 1).toString().padStart(2, '0')}"
        val nextMonth = if (month == 12) "${year + 1}-01"
                        else "$year-${(month + 1).toString().padStart(2, '0')}"

        return Pair(prevMonth, nextMonth)
    }

    /**
     * Pre-warm caches for adjacent months (previous and next).
     * Called in background after current month warmup to speed up month navigation.
     *
     * @param yearMonth Current month in format "yyyy-MM" (e.g., "2025-12")
     */
    suspend fun warmupAdjacentMonths(yearMonth: String) = coroutineScope {
        val start = System.currentTimeMillis()
        if (PERF_LOGS) Log.i(TAG, "PERF: warmupAdjacentMonths START for $yearMonth")

        val (prevMonth, nextMonth) = calculateAdjacentMonths(yearMonth)

        // Launch both warmups in parallel
        val prevDeferred = async {
            try { warmupForMonthExclusive(prevMonth) }
            catch (e: Exception) { Log.w(TAG, "Failed to warm $prevMonth: ${e.message}"); null }
        }
        val nextDeferred = async {
            try { warmupForMonthExclusive(nextMonth) }
            catch (e: Exception) { Log.w(TAG, "Failed to warm $nextMonth: ${e.message}"); null }
        }

        prevDeferred.await()
        nextDeferred.await()

        if (PERF_LOGS) Log.i(TAG, "PERF: warmupAdjacentMonths COMPLETE (${System.currentTimeMillis() - start}ms)")
    }
}

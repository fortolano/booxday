package com.boox.day.search

import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.UnifiedOcrSearchResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The columns of an event that search reads (calendar_events and local_events). */
data class EventSearchRow(
    val id: String,
    val title: String,
    val description: String?,
    val dayId: String,
)

/** The columns of a task that search reads. */
data class TaskSearchRow(
    val id: String,
    val pageId: String,
    val dayDate: String,
    val typedText: String?,
    val convertedText: String?,
    val recognizedText: String?,
)

/**
 * Events and tasks for the search dialog, read once when the dialog opens and folded once,
 * then matched in memory on every keystroke (Oct 2026). Replaces the LIKE queries, which matched
 * the whole query as one pattern, left accents to the letter and read % and _ as wildcards.
 *
 * Scores are the dialog's tiers: [EXACT] when every query word starts a word (title or
 * description for events, any of the three texts for tasks), [PARTIAL] when every word is only
 * somewhere inside. Each tier keeps the [perTierLimit] most recent hits per source, as before.
 */
class SearchCandidates(private val db: AppDatabase) {

    private class Entry(val result: (Double) -> UnifiedOcrSearchResult, val key: String, val date: String, val folded: String)

    private val mutex = Mutex()
    private var events: List<Entry>? = null
    private var tasks: List<Entry>? = null

    /** Results for [query], tier 1 and tier 3 of the dialog. Call from a background thread. */
    suspend fun search(query: String, perTierLimit: Int = 20): List<Pair<String, UnifiedOcrSearchResult>> {
        val words = SearchText.queryWords(query)
        if (words.isEmpty()) return emptyList()
        val (eventEntries, taskEntries) = load()
        val out = mutableListOf<Pair<String, UnifiedOcrSearchResult>>()
        for (entries in listOf(eventEntries, taskEntries)) {
            val exact = entries.filter { SearchText.matchesWordStarts(it.folded, words) }
            val exactKeys = exact.mapTo(HashSet()) { it.key }
            val partial = entries.filter { it.key !in exactKeys && SearchText.matchesSubstrings(it.folded, words) }
            exact.sortedByDescending { it.date }.take(perTierLimit).forEach { out += it.key to it.result(EXACT) }
            partial.sortedByDescending { it.date }.take(perTierLimit).forEach { out += it.key to it.result(PARTIAL) }
        }
        return out
    }

    private suspend fun load(): Pair<List<Entry>, List<Entry>> = mutex.withLock {
        val loadedEvents = events ?: (
            db.calendarEventDao().getSearchRows().map { eventEntry(it, "CALENDAR_EVENT", "CE_") } +
                db.localEventDao().getSearchRows().map { eventEntry(it, "LOCAL_EVENT", "LE_") }
            ).also { events = it }
        val loadedTasks = tasks ?: db.taskDao().getSearchRows().map(::taskEntry).also { tasks = it }
        loadedEvents to loadedTasks
    }

    private fun eventEntry(row: EventSearchRow, type: String, keyPrefix: String) = Entry(
        result = { score ->
            UnifiedOcrSearchResult(
                id = row.id, sourceType = type, sourceId = row.id, pageId = "",
                sourceTitle = row.title, sourceDate = row.dayId,
                snippet = row.description?.take(200) ?: row.title, score = score
            )
        },
        key = keyPrefix + row.id,
        date = row.dayId,
        // A line break keeps a word at the end of the title apart from the description's first
        folded = SearchText.fold(row.title + "\n" + (row.description ?: "")),
    )

    private fun taskEntry(row: TaskSearchRow) = Entry(
        result = { score ->
            UnifiedOcrSearchResult(
                id = row.id, sourceType = "TASK", sourceId = row.id, pageId = row.pageId,
                sourceTitle = null, sourceDate = row.dayDate,
                snippet = row.typedText ?: row.convertedText ?: row.recognizedText ?: "", score = score
            )
        },
        key = "T_" + row.id,
        date = row.dayDate,
        folded = SearchText.fold(listOfNotNull(row.typedText, row.convertedText, row.recognizedText).joinToString("\n")),
    )

    companion object {
        const val EXACT = 2.0
        const val PARTIAL = 0.5
    }
}

package com.boox.day.actionitems

import android.content.Context
import com.boox.day.data.db.DayPageRepository
import com.boox.day.data.db.KvProxy
import com.boox.day.ocr.DigitalInkService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.time.LocalDate

/** A thing to do found on a memo page. [key] stays the same when the page is read again. */
data class ActionItem(val key: String, val text: String, val dueDay: String?, val pageId: String, val pageNumber: Int)

/**
 * Reads a day's memo pages for action items (Oct 2026), on the tablet only: handwriting line by line
 * with ML Kit (nothing goes to Cloud Vision or an AI), typed text boxes as typed.
 */
object ActionItemsReader {

    /** [handwritingUnavailable]: the handwriting model isn't on the tablet yet (only typed text was read). */
    data class Result(val items: List<ActionItem>, val pagesRead: Int, val handwritingUnavailable: Boolean)

    /** All memo pages of [date], or just [pageIds] (in their order). Days named in a line count from [date]. Call off the main thread. */
    suspend fun read(context: Context, scope: CoroutineScope, date: String, pageIds: List<String>? = null): Result {
        val memoDay = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now())
        val app = context.applicationContext
        val pages = pageIds ?: withContext(Dispatchers.IO) { DayPageRepository(app).getByDate(date)?.pageIds.orEmpty() }
        val ink = DigitalInkService.create(app, scope).takeIf { it.isReady() }
        val items = mutableListOf<ActionItem>()
        val keys = HashSet<String>()
        pages.forEachIndexed { index, pageId ->
            for (line in MemoPageText.pageLines(app, ink, pageId)) {
                val found = ActionItemRules.find(line, memoDay) ?: continue
                val key = ActionItemRules.key(pageId, found.text)
                if (keys.add(key)) items += ActionItem(key, found.text, found.dueDay, pageId, index + 1)
            }
        }
        return Result(items, pages.size, handwritingUnavailable = ink == null)
    }
}

/**
 * Action items already added to Tasks or hidden, per day, so they aren't offered again
 * (BooxDay's key-value store; old days are dropped).
 */
object ActionItemsMemory {
    const val ADDED = "added"
    const val HIDDEN = "hidden"

    private const val KEY = "memo_action_items"
    private const val KEEP_DAYS = 90L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val serializer = MapSerializer(String.serializer(), MapSerializer(String.serializer(), String.serializer()))

    /** key → state for [date]. Call off the main thread. */
    fun handled(context: Context, date: String): Map<String, String> = load(context)[date].orEmpty()

    fun mark(context: Context, date: String, key: String, state: String) {
        val app = context.applicationContext
        scope.launch {
            synchronized(this@ActionItemsMemory) {
                val oldest = LocalDate.now().minusDays(KEEP_DAYS).toString()
                val days = load(app).filterKeys { it >= oldest }.toMutableMap()
                days[date] = days[date].orEmpty() + (key to state)
                KvProxy(app).setKv(KEY, days, serializer)
            }
        }
    }

    private fun load(context: Context): Map<String, Map<String, String>> =
        runCatching { KvProxy(context).get(KEY, serializer) }.getOrNull().orEmpty()
}

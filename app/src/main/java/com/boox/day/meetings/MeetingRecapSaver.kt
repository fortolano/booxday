package com.boox.day.meetings

import android.content.Context
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.room.withTransaction
import com.boox.day.SCREEN_HEIGHT
import com.boox.day.SCREEN_WIDTH
import com.boox.day.ai.MeetingRecap
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.DayPageRepository
import com.boox.day.data.db.Page
import com.boox.day.data.db.TextAnnotation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** A recap as typed pages right after the meeting's notes: editable, searchable, exported, backed up. */
object MeetingRecapSaver {
    internal const val MARGIN = 50f
    internal const val FONT_SIZE = 37f

    /** The recap as plain text, the same in the saved page and the PDF. */
    fun text(title: String, recap: MeetingRecap): String = buildString {
        appendLine("Recap · $title")
        appendLine()
        appendLine("Summary")
        appendLine(recap.summary)
        section("Decisions", recap.decisions)
        section("Action items", recap.actionItems.map { action ->
            listOf(action.text, action.owner, action.dueDate).filter { it.isNotBlank() }.joinToString(" · ")
        })
        section("Follow-ups", recap.followUps)
        section("Open questions", recap.openQuestions)
    }.trim()

    private fun StringBuilder.section(name: String, items: List<String>) {
        if (items.isEmpty()) return
        appendLine()
        appendLine(name)
        items.forEach { appendLine("• $it") }
    }

    /** Splits [text] into page-sized parts at line ends; measured with [fits]. */
    internal fun pagesOf(text: String, fits: (String) -> Boolean): List<String> {
        val pages = mutableListOf<String>()
        var current = ""
        for (line in text.lines()) {
            var rest = line
            while (true) {
                val next = if (current.isEmpty()) rest else "$current\n$rest"
                if (fits(next)) {
                    current = next
                    break
                }
                if (current.isNotEmpty()) {
                    pages += current
                    current = ""
                    continue
                }
                // One line taller than a page, as shared web text can be
                val cut = cutAt(rest, fits)
                pages += rest.take(cut).trimEnd()
                rest = rest.drop(cut).trimStart()
                if (rest.isEmpty()) break
            }
        }
        if (current.isNotBlank()) pages += current
        return pages
    }

    /** How much of [line] fits, ending at a word when there is one. */
    private fun cutAt(line: String, fits: (String) -> Boolean): Int {
        var low = 1
        var high = line.length
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (fits(line.take(middle))) low = middle else high = middle - 1
        }
        val space = line.lastIndexOf(' ', low)
        return if (space > 0) space + 1 else low
    }

    /** Writes the recap atomically in one Room transaction in place of any saved before it. Returns the first page's id. */
    suspend fun save(context: Context, eventId: String, title: String, pages: MeetingPages.Pages, recap: MeetingRecap): String = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val db = AppDatabase.getDatabase(app)
        val dayPages = DayPageRepository(app)
        val dayPageDao = db.dayPageDao()
        val pageDao = db.pageDao()
        val textDao = db.textAnnotationDao()

        val width = SCREEN_WIDTH - 2 * MARGIN
        val parts = pagesOf(text(title, recap)) { height(it, width) <= SCREEN_HEIGHT - 2 * MARGIN }
        require(parts.isNotEmpty()) { "Recap produced no pages" }

        val firstId = db.withTransaction {
            val openBefore = dayPageDao.getByDate(pages.date)?.openPageId
            val existingPageIds = dayPageDao.getByDate(pages.date)?.pageIds.orEmpty().toMutableList()
            val ids = parts.indices.map { index ->
                pages.recap.getOrNull(index) ?: run {
                    val newPage = Page(linkedEventId = eventId)
                    pageDao.create(newPage)
                    existingPageIds.add(newPage.id)
                    newPage.id
                }
            }
            pages.recap.forEach { pageId ->
                textDao.getForPageNow(pageId)
                    .filter { it.sourceType == MeetingPages.RECAP_SOURCE }
                    .forEach { textDao.delete(it) }
            }
            parts.forEachIndexed { index, part ->
                textDao.insert(
                    TextAnnotation(
                        id = UUID.randomUUID().toString(),
                        content = part,
                        x = MARGIN,
                        y = MARGIN,
                        width = width,
                        height = height(part, width),
                        fontSize = FONT_SIZE,
                        fontWeight = 400,
                        textColor = 0xFF000000.toInt(),
                        backgroundColor = 0x00000000,
                        textAlign = "left",
                        padding = 0f,
                        pageId = ids[index],
                        sourceType = MeetingPages.RECAP_SOURCE
                    )
                )
            }

            // Right after the notes, not at the end of the day
            val rest = existingPageIds.filter { it !in ids }
            val after = rest.indexOfLast { it in pages.notes }
            dayPageDao.setPageIds(pages.date, rest.take(after + 1) + ids + rest.drop(after + 1))
            openBefore?.let { dayPageDao.setOpenPageId(pages.date, it) }
            ids.first()
        }
        // Invalidate cache only after the transaction has committed
        dayPages.invalidatePageCache(pages.date)
        firstId
    }

    internal fun height(text: String, width: Float): Float {
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = FONT_SIZE }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt()).setAlignment(Layout.Alignment.ALIGN_NORMAL).build().height.toFloat()
    }
}

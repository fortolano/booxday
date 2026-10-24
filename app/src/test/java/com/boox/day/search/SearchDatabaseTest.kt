package com.boox.day.search

import android.app.Application
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.CalendarEvent
import com.boox.day.data.db.LocalEvent
import com.boox.day.data.db.Page
import com.boox.day.data.db.Task
import com.boox.day.data.db.UnifiedOcrText
import com.boox.day.ocr.model.OcrSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Search against the app's real database (Oct 2026): memos and BOOX Notes through the FTS4 index
 * (word starts, accents ignored, the one-time move from the simple tokenizer to unicode61), and
 * events and tasks through [SearchCandidates]. Robolectric, in-memory Room, AppDatabase.FTS4_CALLBACK.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SearchDatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var sql: SupportSQLiteDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(AppDatabase.FTS4_CALLBACK)
            .build()
        sql = db.openHelper.writableDatabase
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun aNewIndexUsesUnicode61() {
        assertTrue(ftsTableSql(), ftsTableSql().contains("unicode61"))
    }

    @Test
    fun memoSearchMatchesWordStartsInAnyOrderAndIgnoresAccents() = runBlocking {
        memo("p1", "Meeting with Mary about dark matter")
        memo("p2", "Café au lait with Zoë")
        memo("p3", "Primary school run")
        memo("p4", "Trip to Łódź")

        assertEquals(listOf("p1"), memoHits("meet"))
        assertEquals(listOf("p1"), memoHits("mat dar"))
        assertEquals(listOf("p1"), memoHits("MARY"))
        assertEquals(listOf("p2"), memoHits("cafe"))
        assertEquals(listOf("p2"), memoHits("zoe"))
        assertEquals(listOf("p2"), memoHits("CAFÉ"))
        assertEquals(listOf("p4"), memoHits("łódź"))
        assertEquals(listOf("p4"), memoHits("Lodz".replace("L", "Ł")))
        assertTrue("inside a word is not a word start", memoHits("eeting").isEmpty())
        assertTrue("every word has to be there", memoHits("meet zebra").isEmpty())
        assertTrue("operators are plain words", memoHits("meet OR zebra").isEmpty())
        assertTrue(memoHits("\"").isEmpty())
    }

    @Test
    fun anIndexMadeWithTheSimpleTokenizerIsMovedToUnicode61Once() = runBlocking {
        // The table as the app created it until Oct 2026
        sql.execSQL("DROP TABLE unified_ocr_text_fts")
        sql.execSQL(
            """CREATE VIRTUAL TABLE unified_ocr_text_fts USING fts4(
                content="unified_ocr_text", text, textNormalized, synopsis)"""
        )
        memo("p1", "Café meeting")
        memo("p2", "Garden plan")
        assertTrue("simple does not fold accents", memoHits("cafe").isEmpty())

        AppDatabase.FTS4_CALLBACK.onOpen(sql) // the next app start

        assertTrue(ftsTableSql().contains("unicode61"))
        assertEquals(rowCount(), ftsDocumentCount())
        assertEquals("ok", scalar("PRAGMA integrity_check"))
        sql.execSQL("INSERT INTO unified_ocr_text_fts(unified_ocr_text_fts) VALUES('integrity-check')")
        assertEquals(listOf("p1"), memoHits("cafe"))

        // Later starts leave it alone, and the triggers write into the new table
        val sqlBefore = ftsTableSql()
        AppDatabase.FTS4_CALLBACK.onOpen(sql)
        assertEquals(sqlBefore, ftsTableSql())
        memo("p3", "Crème brûlée")
        assertEquals(rowCount(), ftsDocumentCount())
        assertEquals(listOf("p3"), memoHits("creme brulee"))
    }

    @Test
    fun eventsAndTasksMatchWordStartsFirstThenInsideWords() = runBlocking {
        db.calendarEventDao().upsert(event("e1", "Meeting with Mary", "Room 4", "2026-10-20"))
        db.calendarEventDao().upsert(event("e2", "Primary school", null, "2026-10-22"))
        db.localEventDao().insert(LocalEvent(id = "l1", title = "Café with Zoë", dayId = "2026-10-21", startTime = 0))
        val page = Page().also { db.pageDao().create(it) }
        db.taskDao().insertAll(listOf(
            Task(id = "t1", dayDate = "2026-10-23", taskPageIndex = 0, rowIndex = 0, pageId = page.id, typedText = "Pay 50% deposit"),
            Task(id = "t2", dayDate = "2026-10-23", taskPageIndex = 0, rowIndex = 1, pageId = page.id, recognizedText = "Call Mary"),
            Task(id = "t3", dayDate = "2026-10-23", taskPageIndex = 0, rowIndex = 2, pageId = page.id),
        ))
        val candidates = SearchCandidates(db)

        val mary = candidates.search("mary").associate { it.first to it.second.score }
        assertEquals(mapOf("CE_e1" to 2.0, "T_t2" to 2.0, "CE_e2" to 0.5), mary)

        assertEquals(listOf("LE_l1"), candidates.search("cafe zoe").map { it.first })
        assertEquals(listOf("CE_e1"), candidates.search("room meet").map { it.first })
        assertEquals(listOf("T_t1"), candidates.search("50%").map { it.first })
        assertTrue("% is not a wildcard", candidates.search("%").isEmpty())

        val hit = candidates.search("deposit").single().second
        assertEquals("TASK", hit.sourceType)
        assertEquals(page.id, hit.pageId)
        assertEquals("2026-10-23", hit.sourceDate)
        assertEquals("Pay 50% deposit", hit.snippet)
    }

    @Test
    fun eachTierKeepsTheMostRecentHits() = runBlocking {
        db.calendarEventDao().upsertAll((1..25).map { day ->
            event("e$day", "Standup", null, "2026-10-%02d".format(day))
        })
        val hits = SearchCandidates(db).search("stand", perTierLimit = 20)
        assertEquals(20, hits.size)
        assertEquals("CE_e25", hits.first().first)
        assertTrue(hits.none { it.first == "CE_e1" })
    }

    private fun memoHits(query: String): List<String> = runBlocking {
        val ftsQuery = SearchText.ftsPrefixQuery(query)
        if (ftsQuery.isEmpty()) emptyList()
        else db.unifiedOcrDao().searchTextByType(ftsQuery, OcrSource.MEMO.name).map { it.pageId }.sorted()
    }

    private suspend fun memo(pageId: String, body: String) {
        db.unifiedOcrDao().upsertOcrText(
            UnifiedOcrText(
                id = UnifiedOcrText.generateId(OcrSource.MEMO, "2026-10-24", pageId),
                sourceType = OcrSource.MEMO.name,
                sourceId = "2026-10-24",
                pageId = pageId,
                text = body,
                textNormalized = body.lowercase(),
                cleanText = body,
                wordCount = body.split(' ').size,
                confidence = 1f,
                synopsis = null,
                sourceDate = "2026-10-24",
                updatedAt = 1_760_500_000_000
            )
        )
    }

    private fun event(id: String, title: String, description: String?, day: String) = CalendarEvent(
        id = id, calendarId = "cal", calendarName = "Work", title = title, dayId = day, endDayId = day,
        startTime = 0, description = description
    )

    private fun ftsTableSql() = scalar("SELECT sql FROM sqlite_master WHERE name = 'unified_ocr_text_fts'")

    private fun rowCount() = scalar("SELECT COUNT(*) FROM unified_ocr_text").toInt()

    private fun ftsDocumentCount() = scalar("SELECT COUNT(*) FROM unified_ocr_text_fts_docsize").toInt()

    private fun scalar(query: String): String = sql.query(query).use { it.moveToFirst(); it.getString(0) }
}

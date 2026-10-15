package com.boox.day.data.db

import android.app.Application
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.boox.day.ocr.model.OcrSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * unified_ocr_text_fts is an external-content FTS4 table kept in step with unified_ocr_text by
 * triggers (AppDatabase.FTS4_CALLBACK). Re-indexing an item used to leave a stale document in
 * the index each time ("malformed inverted index" in PRAGMA integrity_check, Oct 2026).
 *
 * Runs on the JVM (Robolectric) against the real Room database, DAO and FTS callback.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class UnifiedOcrFtsConsistencyTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: UnifiedOcrDao
    private lateinit var sql: SupportSQLiteDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(AppDatabase.FTS4_CALLBACK)
            .build()
        dao = db.unifiedOcrDao()
        sql = db.openHelper.writableDatabase // opens the database, so onOpen creates the FTS table
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun reindexingTheSameItemTwiceKeepsTheIndexInStep() = runBlocking {
        for (source in SOURCES) {
            dao.upsertOcrText(text(source, "first version zebra"))
            dao.upsertOcrText(text(source, "second version giraffe"))
            dao.upsertOcrText(text(source, "second version giraffe"))
        }

        assertEquals(SOURCES.size, rowCount())
        assertEquals(rowCount(), ftsDocumentCount())
        assertIndexIsSound()

        for (source in SOURCES) {
            val hits = dao.searchTextByType("\"giraffe\"", source.name)
            assertEquals("$source new text", listOf(idOf(source)), hits.map { it.id })
            assertTrue("$source old text", dao.searchTextByType("\"zebra\"", source.name).isEmpty())
        }
    }

    @Test
    fun deletesRemoveTheirIndexDocuments() = runBlocking {
        for (source in SOURCES) dao.upsertOcrText(text(source, "lunch with mary"))

        dao.deleteTextById(idOf(OcrSource.MEMO))
        dao.deleteTextBySource(OcrSource.TASK.name, SOURCE_ID)
        dao.deleteTextBySourceType(OcrSource.CALENDAR_EVENT.name)

        assertEquals(1, rowCount())
        assertEquals(rowCount(), ftsDocumentCount())
        assertIndexIsSound()
        assertEquals(listOf(idOf(OcrSource.BOOX_NOTE)), dao.searchText("\"mary\"").map { it.id })

        dao.deleteAllText()
        assertEquals(0, ftsDocumentCount())
        assertIndexIsSound()
    }

    @Test
    fun searchFindsPagesStoredWithoutASynopsis() = runBlocking {
        // storeOcrResult writes synopsis = null when a page has no semantic bundle
        dao.upsertOcrText(text(OcrSource.MEMO, "Lunch with Mary at noon").copy(synopsis = null))
        dao.upsertOcrText(text(OcrSource.BOOX_NOTE, "Mary call").copy(synopsis = "Call Mary back"))

        val all = dao.searchText("\"mary\"").associate { it.sourceType to it.snippet }
        assertEquals("Lunch with <b>Mary</b> at noon", all["MEMO"])
        assertEquals("Call <b>Mary</b> back", all["BOOX_NOTE"])

        val memos = dao.searchTextByType("\"mary\"", OcrSource.MEMO.name)
        assertEquals(listOf("Lunch with <b>Mary</b> at noon"), memos.map { it.snippet })
    }

    @Test
    fun aDatabaseWrittenWithTheOldTriggersIsRepairedOnOpen() = runBlocking {
        // Put back the triggers the app shipped with until Oct 2026 and write the way it did
        sql.execSQL("DROP TRIGGER unified_ocr_text_fts_bd")
        sql.execSQL("DROP TRIGGER unified_ocr_text_fts_bu")
        sql.execSQL("DROP TRIGGER unified_ocr_text_fts_au")
        sql.execSQL(
            """CREATE TRIGGER unified_ocr_text_fts_ad AFTER DELETE ON unified_ocr_text BEGIN
                DELETE FROM unified_ocr_text_fts WHERE docid = old.rowid; END"""
        )
        sql.execSQL(
            """CREATE TRIGGER unified_ocr_text_fts_au AFTER UPDATE ON unified_ocr_text BEGIN
                DELETE FROM unified_ocr_text_fts WHERE docid = old.rowid;
                INSERT INTO unified_ocr_text_fts(docid, text, textNormalized, synopsis)
                VALUES (new.rowid, new.text, new.textNormalized, new.synopsis); END"""
        )
        repeat(3) { round ->
            sql.execSQL(
                "INSERT OR REPLACE INTO unified_ocr_text (id, sourceType, sourceId, pageId, text, " +
                    "textNormalized, cleanText, synopsis, wordCount, confidence, domainConfidence, semanticVersion, updatedAt) " +
                    "VALUES ('${idOf(OcrSource.CALENDAR_EVENT)}', 'CALENDAR_EVENT', '$SOURCE_ID', '$PAGE_ID', " +
                    "'standup round$round', 'standup round$round', '', '', 2, 1.0, 0, 1, 0)"
            )
        }
        assertEquals(1, rowCount())
        assertEquals("the old write path leaves stale documents", 3, ftsDocumentCount())
        // What the Go 10.3 reported. (Not the FTS 'integrity-check' command here: it raises
        // SQLITE_CORRUPT, and Android's corruption handler then closes the database.)
        assertTrue(
            scalar("PRAGMA integrity_check"),
            scalar("PRAGMA integrity_check").contains("malformed inverted index for FTS4 table main.unified_ocr_text_fts")
        )

        AppDatabase.FTS4_CALLBACK.onOpen(sql) // the next app start

        assertEquals(1, ftsDocumentCount())
        assertIndexIsSound()
        assertFalse(triggerExists("unified_ocr_text_fts_ad"))
        assertTrue(triggerExists("unified_ocr_text_fts_bd"))
        assertEquals(listOf(idOf(OcrSource.CALENDAR_EVENT)), dao.searchText("\"round2\"").map { it.id })
        assertTrue(dao.searchText("\"round0\"").isEmpty())

        // Later opens leave the repaired database alone, and writes stay in step
        AppDatabase.FTS4_CALLBACK.onOpen(sql)
        dao.upsertOcrText(text(OcrSource.CALENDAR_EVENT, "standup moved"))
        assertEquals(1, rowCount())
        assertEquals(rowCount(), ftsDocumentCount())
        assertIndexIsSound()
    }

    private fun assertIndexIsSound() {
        assertEquals("ok", scalar("PRAGMA integrity_check"))
        // FTS4's own check (compares the index with the content table); throws if they differ
        sql.execSQL("INSERT INTO unified_ocr_text_fts(unified_ocr_text_fts) VALUES('integrity-check')")
    }

    private fun rowCount() = scalar("SELECT COUNT(*) FROM unified_ocr_text").toInt()

    private fun ftsDocumentCount() = scalar("SELECT COUNT(*) FROM unified_ocr_text_fts_docsize").toInt()

    private fun triggerExists(name: String) =
        scalar("SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger' AND name = '$name'") == "1"

    private fun scalar(query: String): String = sql.query(query).use { it.moveToFirst(); it.getString(0) }

    private fun idOf(source: OcrSource) = UnifiedOcrText.generateId(source, SOURCE_ID, PAGE_ID)

    private fun text(source: OcrSource, body: String) = UnifiedOcrText(
        id = idOf(source),
        sourceType = source.name,
        sourceId = SOURCE_ID,
        pageId = PAGE_ID,
        text = body,
        textNormalized = body.lowercase(),
        cleanText = body,
        wordCount = body.split(' ').size,
        confidence = 1f,
        synopsis = body,
        sourceDate = "2026-10-15",
        updatedAt = 1_760_500_000_000
    )

    private companion object {
        val SOURCES = listOf(OcrSource.MEMO, OcrSource.TASK, OcrSource.CALENDAR_EVENT, OcrSource.BOOX_NOTE)
        const val SOURCE_ID = "src1"
        const val PAGE_ID = "p1"
    }
}

package com.boox.day.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boox.day.data.AppRepository
import com.boox.day.data.PageDataManager
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.encodeStrokePoints
import com.boox.day.data.db.isUndecodable
import com.boox.day.sync.ExportService
import com.boox.day.sync.ImportService
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.UUID

/**
 * Plan 2026-10-01, A7 (Phase 0 test e): one stroke row whose SB1 blob can't be decoded must not
 * crash page loading (or empty the page) - the other strokes still load.
 */
@RunWith(AndroidJUnit4::class)
class CorruptStrokeBlobLoadTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private val pageId = "page-corrupt-${UUID.randomUUID()}"

    @Before
    fun setUp() {
        db = TestDb.inMemory(context)
    }

    @After
    fun tearDown() {
        PageDataManager.removePage(pageId)
        db.close()
    }

    @Test
    fun strokeQueries_withCorruptBlob_returnTheGoodStrokes() = runBlocking {
        seedPageWithOneCorruptStroke()
        val problems = mutableListOf<String>()

        runCatching { db.pageDao().getPageWithStrokesByIdSuspend(pageId).strokes }
            .onFailure { problems += "getPageWithStrokesByIdSuspend (page loader) threw $it" }
            .onSuccess { problems += checkGoodStrokes("getPageWithStrokesByIdSuspend", it.map { s -> s.id }) }
        runCatching { db.strokeDao().getAllForPage(pageId) }
            .onFailure { problems += "getAllForPage (task moves) threw $it" }
            .onSuccess { problems += checkGoodStrokes("getAllForPage", it.map { s -> s.id }) }
        runCatching { db.strokeDao().getAllStrokes() }
            .onFailure { problems += "getAllStrokes (backup export) threw $it" }
            .onSuccess { problems += checkGoodStrokes("getAllStrokes", it.map { s -> s.id }) }

        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun pageLoader_withCorruptBlob_loadsGoodStrokesWithoutCrashing() = runBlocking {
        seedPageWithOneCorruptStroke()

        val uncaught = recordUncaughtExceptions {
            PageDataManager.requestPageLoadJoin(repositoryOnTestDb(), pageId, null)
        }

        assertTrue("page loader threw an uncaught exception: $uncaught", uncaught.isEmpty())
        assertEquals(
            listOf("good-1", "good-2"),
            PageDataManager.getStrokes(pageId).map { it.id }.sorted()
        )
    }

    @Test
    fun pageLoader_withMissingPageRow_doesNotCrash() = runBlocking {
        val uncaught = recordUncaughtExceptions {
            PageDataManager.requestPageLoadJoin(repositoryOnTestDb(), pageId, null)
        }

        assertTrue("page loader threw an uncaught exception: $uncaught", uncaught.isEmpty())
        assertFalse("half-loaded page was kept in memory", PageDataManager.isPageLoaded(pageId))
    }

    @Test
    fun undecodableStroke_isMarked_andItsBlobIsNeverOverwritten() = runBlocking {
        seedPageWithOneCorruptStroke()
        val damagedBytes = rawBlob("bad")

        val bad = db.strokeDao().getAllForPage(pageId).single { it.id == "bad" }
        assertTrue("damaged stroke not marked undecodable", bad.isUndecodable)

        // Writing the loaded row back (update or REPLACE insert, even moved) must be refused.
        assertTrue(runCatching { db.strokeDao().update(bad.copy(top = 1f)) }.isFailure)
        assertTrue(runCatching { db.strokeDao().create(listOf(bad.copy(left = 99f))) }.isFailure)
        assertTrue(damagedBytes.contentEquals(rawBlob("bad")))
    }

    @Test
    fun backupExport_skipsUndecodableStroke_keepsTheOthers() = runBlocking {
        seedPageWithOneCorruptStroke()

        val export = ExportService(context) { db }.createBackup()

        assertTrue("export failed: $export", export is ExportService.ExportResult.Success)
        val file = (export as ExportService.ExportResult.Success).backupFile
        try {
            val target = TestDb.inMemory(context)
            try {
                val restore = ImportService(context) { target }.restoreBackup({ file.inputStream() })
                assertTrue("restore failed: $restore", restore is ImportService.ImportResult.Success)
                assertEquals(listOf("good-1", "good-2"), target.strokeDao().getAllForPage(pageId).map { it.id }.sorted())
            } finally {
                target.close()
            }
        } finally {
            file.delete()
        }
    }

    private fun rawBlob(strokeId: String): ByteArray =
        db.openHelper.readableDatabase.query("SELECT points FROM stroke WHERE id = ?", arrayOf<Any?>(strokeId))
            .use { cursor -> cursor.moveToFirst(); cursor.getBlob(0) }

    private fun seedPageWithOneCorruptStroke() {
        db.pageDao().create(TestDb.page(pageId))
        val good1 = TestDb.stroke(pageId, id = "good-1")
        val good2 = TestDb.stroke(pageId, id = "good-2", dx = 40f)
        val bad = TestDb.stroke(pageId, id = "bad", dx = 80f)
        db.strokeDao().create(listOf(good1, good2, bad))
        // A partially written blob: valid SB1 header, last bytes of the body missing.
        val full = encodeStrokePoints(bad.points)
        val truncated = full.copyOf(full.size - 4)
        db.openHelper.writableDatabase.execSQL(
            "UPDATE stroke SET points = ? WHERE id = ?",
            arrayOf<Any>(truncated, "bad")
        )
    }

    private fun checkGoodStrokes(query: String, ids: List<String>): List<String> {
        val missing = listOf("good-1", "good-2").filterNot { it in ids }
        return if (missing.isEmpty()) emptyList() else listOf("$query lost good strokes $missing (got $ids)")
    }

    /**
     * The page loader reads pages and strokes through pageRepository; route that to the in-memory
     * database. (Text annotations and attachments still read the app database - nothing exists
     * there for this random page ID, and those reads don't write.)
     */
    private fun repositoryOnTestDb(): AppRepository =
        AppRepository(context).also { it.pageRepository.db = db.pageDao() }

    /**
     * PageDataManager loads pages on its own scope without an exception handler, so a loader
     * failure reaches the thread's uncaught-exception handler (which kills the app). Record such
     * failures instead so the test reports them rather than crashing the test process.
     */
    private suspend fun recordUncaughtExceptions(block: suspend () -> Unit): List<Throwable> {
        val recorded = Collections.synchronizedList(mutableListOf<Throwable>())
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> recorded += e }
        try {
            block()
            delay(300)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
        return recorded.toList()
    }
}

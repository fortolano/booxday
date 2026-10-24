package com.boox.day.data.db

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boox.day.ocr.model.OcrSource
import com.boox.day.ocr.model.UnifiedOcrStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

/**
 * Pages quarantined only because they were read while offline are read again (Oct 2026). On the
 * owner's Go 10.3, 7 pages sat QUARANTINED with "AI read failed: Network error: …", and the memo
 * queue never picked them up again.
 *
 * Runs on the JVM (Robolectric) against the real Room database and DAO queries.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class UnifiedOcrNetworkQuarantineTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: UnifiedOcrDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(AppDatabase.FTS4_CALLBACK)
            .build()
        dao = db.unifiedOcrDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** A memo page of [DATE], with its OCR row in [status]. */
    private suspend fun memoPage(pageId: String, status: UnifiedOcrStatus, failCount: Int, lastError: String?) {
        val updated = Date(MODIFIED)
        db.pageDao().create(Page(id = pageId, createdAt = updated, updatedAt = updated))
        val day = db.dayPageDao().getByDate(DATE)
        db.dayPageDao().insert(DayPage(date = DATE, pageIds = (day?.pageIds ?: emptyList()) + pageId))
        dao.upsertOcrPage(
            UnifiedOcrPage.create(OcrSource.MEMO, DATE, pageId, MODIFIED, MODIFIED)
                .copy(status = status.name, failCount = failCount, lastError = lastError)
        )
    }

    private suspend fun row(pageId: String) = dao.getOcrPage(UnifiedOcrPage.generateId(OcrSource.MEMO, DATE, pageId))!!

    private suspend fun queuedMemoPages() = dao.getMemoPagesNeedingOcr(50).map { it.pageId }.toSet()

    @Test
    fun offlineQuarantinesAreFreedAndQueuedAgain() = runBlocking {
        memoPage("dns", UnifiedOcrStatus.QUARANTINED, 3,
            "AI read failed: Network error: Unable to resolve host \"openrouter.ai\": No address associated with hostname")
        memoPage("connect", UnifiedOcrStatus.QUARANTINED, 8,
            "AI read failed: Network error: Failed to connect to openrouter.ai/104.18.2.115:443")
        memoPage("timeout", UnifiedOcrStatus.QUARANTINED, 3, "AI read failed: timed out")
        memoPage("bad-page", UnifiedOcrStatus.QUARANTINED, 3, "AI read failed: unreadable answer")
        memoPage("read", UnifiedOcrStatus.SUCCESS, 0, null)
        assertTrue(queuedMemoPages().isEmpty())

        assertEquals(3, dao.releaseNetworkQuarantines())

        for (id in listOf("dns", "connect", "timeout")) {
            assertEquals(id, UnifiedOcrStatus.PENDING.name, row(id).status)
            assertEquals(id, 0, row(id).failCount)
        }
        assertEquals(UnifiedOcrStatus.QUARANTINED.name, row("bad-page").status)
        assertEquals(setOf("dns", "connect", "timeout"), queuedMemoPages())

        // Safe at every start: nothing left to free
        assertEquals(0, dao.releaseNetworkQuarantines())
    }

    @Test
    fun readingAPageClearsItsFailures() = runBlocking {
        memoPage("p1", UnifiedOcrStatus.IN_PROGRESS, 5, "AI read failed: unreadable answer")
        val id = UnifiedOcrPage.generateId(OcrSource.MEMO, DATE, "p1")

        dao.updateStatusWithFirstIndexed(id, UnifiedOcrStatus.SUCCESS.name, ocrAt = 1L, textHash = "h", semanticHash = null, now = 1L)

        assertEquals(0, row("p1").failCount)
        assertEquals(null, row("p1").lastError)
    }

    private companion object {
        const val DATE = "2026-10-24"
        const val MODIFIED = 1_792_863_347_602L
    }
}

package com.boox.day.ocr

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.UnifiedOcrText
import com.boox.day.ocr.model.OcrSource
import com.boox.day.ocr.semantic.SemanticGeniusService
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
 * Verifies Phase 1 H7: filtering getDateContext strictly to TASK + MEMO prevents CALENDAR_EVENT
 * and BOOX_NOTE keywords from entering Convert to Tasks prompts without CalendarAiNotice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SemanticContextPrivacyFilterTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val day = "2026-11-24"

    @Before
    fun setUp() {
        AppDatabase.getDatabase(context)
    }

    @After
    fun tearDown() {
        AppDatabase.closeDatabase()
    }

    @Test
    fun taskAndMemoFilterExcludesCalendarEventsAndBooxNotes() = runBlocking {
        val dao = AppDatabase.getDatabase(context).unifiedOcrDao()
        dao.upsertOcrText(
            UnifiedOcrText(
                id = UnifiedOcrText.generateId(OcrSource.CALENDAR_EVENT, "evt-secret", "p1"),
                sourceType = OcrSource.CALENDAR_EVENT.name,
                sourceId = "evt-secret",
                pageId = "p1",
                text = "Confidential Cardiologist Oncology Appointment",
                textNormalized = "confidential cardiologist oncology appointment",
                cleanText = "Confidential Cardiologist Oncology Appointment",
                wordCount = 4,
                confidence = 1f,
                keywordsJson = """[{"term":"cardiologist","score":1.0},{"term":"oncology","score":0.9}]""",
                sourceDate = day,
                updatedAt = 1L,
            )
        )
        dao.upsertOcrText(
            UnifiedOcrText(
                id = UnifiedOcrText.generateId(OcrSource.BOOX_NOTE, "note-secret", "p1"),
                sourceType = OcrSource.BOOX_NOTE.name,
                sourceId = "note-secret",
                pageId = "p1",
                text = "Private journal tax audit",
                textNormalized = "private journal tax audit",
                cleanText = "Private journal tax audit",
                wordCount = 4,
                confidence = 1f,
                keywordsJson = """[{"term":"tax","score":1.0},{"term":"audit","score":0.9}]""",
                sourceDate = day,
                updatedAt = 1L,
            )
        )

        val service = SemanticGeniusService(context)
        // Unfiltered would have pulled both CALENDAR_EVENT and BOOX_NOTE keywords
        val unfiltered = service.getDateContext(day)
        assertTrue(unfiltered.allKeywords.isNotEmpty())

        // Filtered strictly to TASK + MEMO returns zero calendar or BOOX Note signals
        val filtered = service.getDateContext(day, includeSourceTypes = setOf(OcrSource.TASK, OcrSource.MEMO))
        assertTrue("calendar & BOOX note keywords must not leak: ${filtered.allKeywords}", filtered.allKeywords.isEmpty())
        assertTrue(filtered.allEntities.isEmpty())
        assertEquals(0, filtered.memoCount)
        assertEquals(0, filtered.taskCount)
        assertEquals(0, filtered.booxNoteCount)
    }
}

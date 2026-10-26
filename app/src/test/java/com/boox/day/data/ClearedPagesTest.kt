package com.boox.day.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.Stroke
import com.boox.day.data.db.StrokePoint
import com.boox.day.data.db.UnifiedOcrText
import com.boox.day.editor.utils.Pen
import com.boox.day.ocr.model.OcrSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * "Clear All Memos Today" cleared the other pages of the day only in the database: their ink stayed
 * in memory and came back when the user went to them, and search kept finding their text (seen on
 * the Go 10.3, 26 Oct 2026). Robolectric, real Room database with its search index.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ClearedPagesTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    @After
    fun tearDown() {
        listOf(SHOWN, CLEARED).forEach { PageDataManager.clearPageData(it) }
        AppDatabase.closeDatabase()
    }

    @Test
    fun aClearedPageNotOnScreenKeepsNothingInMemoryOrOnDisk() {
        PageDataManager.setStrokes(SHOWN, listOf(stroke(SHOWN)))
        PageDataManager.setStrokes(CLEARED, listOf(stroke(CLEARED), stroke(CLEARED)))
        val full = ensurePreviewsFullFolder(context)
        val shownImage = File(full, "$SHOWN-sy0.png").apply { writeText("png") }
        val clearedImage = File(full, "$CLEARED-sy0.png").apply { writeText("png") }
        val clearedThumb = File(context.filesDir, "pages/previews/thumbs/$CLEARED").apply {
            parentFile?.mkdirs()
            writeText("jpg")
        }

        ClearedPages.forgetLoadedContent(context, listOf(CLEARED))

        assertTrue(PageDataManager.getStrokes(CLEARED).isEmpty())
        assertFalse(clearedImage.exists())
        assertFalse(clearedThumb.exists())
        // The page on screen is left to PageView.clearAllContent
        assertEquals(1, PageDataManager.getStrokes(SHOWN).size)
        assertTrue(shownImage.exists())
    }

    @Test
    fun searchForgetsTheClearedPagesOfThatDayOnly() = runBlocking {
        val dao = AppDatabase.getDatabase(context).unifiedOcrDao()
        dao.upsertOcrText(memoText(DAY, SHOWN, "lunch with mary"))
        dao.upsertOcrText(memoText(DAY, CLEARED, "mary called back"))
        dao.upsertOcrText(memoText(OTHER_DAY, CLEARED_ELSEWHERE, "mary at the office"))

        ClearedPages.forgetSearchText(context, DAY, listOf(SHOWN, CLEARED))

        assertEquals(
            listOf(UnifiedOcrText.generateId(OcrSource.MEMO, OTHER_DAY, CLEARED_ELSEWHERE)),
            dao.searchText("\"mary\"").map { it.id }
        )
    }

    private fun stroke(pageId: String) = Stroke(
        size = 5f, pen = Pen.BALLPEN, top = 10f, bottom = 20f, left = 10f, right = 40f,
        points = listOf(StrokePoint(10f, 10f), StrokePoint(40f, 20f)), pageId = pageId
    )

    private fun memoText(date: String, pageId: String, body: String) = UnifiedOcrText(
        id = UnifiedOcrText.generateId(OcrSource.MEMO, date, pageId),
        sourceType = OcrSource.MEMO.name,
        sourceId = date,
        pageId = pageId,
        text = body,
        textNormalized = body,
        cleanText = body,
        wordCount = body.split(' ').size,
        confidence = 1f,
        synopsis = body,
        sourceDate = date,
        updatedAt = 1_792_900_000_000
    )

    private companion object {
        const val DAY = "2026-11-08"
        const val OTHER_DAY = "2026-11-09"
        const val SHOWN = "15196da3-0000-4000-8000-000000000001"
        const val CLEARED = "c32b1629-0000-4000-8000-000000000002"
        const val CLEARED_ELSEWHERE = "aa11bb22-0000-4000-8000-000000000003"
    }
}

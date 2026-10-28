package com.boox.day.ai

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.boox.day.SCREEN_WIDTH
import com.boox.day.data.PageDataManager
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.Page
import com.boox.day.data.db.Stroke
import com.boox.day.data.db.StrokePoint
import com.boox.day.data.db.TextAnnotation
import com.boox.day.editor.utils.Pen
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.UUID

/**
 * The AI features read whole memo pages from their data (Oct 2026): every page of the day, opened
 * this session or not, and everything on it, wherever it is on the page.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MemoPageContentTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @After
    fun closeDatabase() {
        AppDatabase.closeDatabase()
    }

    private fun newPageId() = "ai-test-" + UUID.randomUUID()

    /** A horizontal line of ink from ([x1], [y]) to ([x2], [y]). */
    private fun line(pageId: String, x1: Float, x2: Float, y: Float) = Stroke(
        size = 6f, pen = Pen.BALLPEN, top = y - 3f, bottom = y + 3f, left = x1 - 3f, right = x2 + 3f,
        points = (0..20).map { StrokePoint(x1 + (x2 - x1) * it / 20f, y) }, pageId = pageId
    )

    private fun saveToDatabase(pageId: String, strokes: List<Stroke>, textBoxes: List<TextAnnotation> = emptyList()) = runBlocking {
        val db = AppDatabase.getDatabase(context)
        db.pageDao().create(Page(id = pageId))
        if (strokes.isNotEmpty()) db.strokeDao().create(strokes)
        textBoxes.forEach { db.textAnnotationDao().insert(it) }
    }

    private fun isDark(bitmap: Bitmap, x: Int, y: Int, radius: Int = 3): Boolean =
        (x - radius..x + radius).any { px ->
            (y - radius..y + radius).any { py ->
                px in 0 until bitmap.width && py in 0 until bitmap.height &&
                    bitmap.getPixel(px, py).let { Color.red(it) < 100 && Color.green(it) < 100 && Color.blue(it) < 100 }
            }
        }

    @Test fun `a page not opened this session is read from the database`() = runBlocking {
        val pageId = newPageId()
        saveToDatabase(
            pageId,
            listOf(line(pageId, 100f, 400f, 300f)),
            listOf(TextAnnotation(content = "Call the bank", x = 100f, y = 500f, width = 500f, height = 80f, pageId = pageId))
        )
        assertFalse(PageDataManager.isPageLoaded(pageId))

        val page = MemoPageContent.load(context, pageId)
        assertEquals(1, page.strokes.size)
        assertEquals("Call the bank", page.textAnnotations.single().content)
    }

    @Test fun `a page open this session is read from memory, with strokes not saved yet`() = runBlocking {
        val pageId = newPageId()
        PageDataManager.setStrokes(pageId, listOf(line(pageId, 100f, 400f, 300f)))
        val page = MemoPageContent.load(context, pageId)
        assertEquals(1, page.strokes.size)
    }

    @Test fun `the picture holds ink below the screen and to its right`() = runBlocking {
        val pageId = newPageId()
        val farX = SCREEN_WIDTH + 300f
        saveToDatabase(pageId, listOf(line(pageId, 100f, 400f, 200f), line(pageId, farX - 200f, farX, 2600f)))

        val picture = checkNotNull(MemoPageContent.renderWholePage(context, pageId))
        assertTrue("width ${picture.width}", picture.width >= farX.toInt())
        assertTrue("height ${picture.height}", picture.height >= 2600)
        assertTrue(isDark(picture, 250, 200))
        assertTrue(isDark(picture, (farX - 100f).toInt(), 2600))
    }

    @Test fun `text boxes are in the picture`() = runBlocking {
        val pageId = newPageId()
        saveToDatabase(
            pageId, emptyList(),
            listOf(TextAnnotation(content = "Buy milk", x = 100f, y = 100f, width = 600f, height = 120f, fontSize = 64f, pageId = pageId))
        )
        val picture = checkNotNull(MemoPageContent.renderWholePage(context, pageId))
        val darkInBox = (100 until 700 step 4).sumOf { x -> (100 until 220 step 4).count { y -> isDark(picture, x, y, radius = 0) } }
        assertTrue("dark pixels in the text box: $darkInBox", darkInBox > 20)
    }

    @Test fun `ink above the page corner is moved into the picture`() = runBlocking {
        val pageId = newPageId()
        saveToDatabase(pageId, listOf(line(pageId, 100f, 400f, -60f)))
        val picture = checkNotNull(MemoPageContent.renderWholePage(context, pageId))
        // The picture starts 40 px (the padding) above the ink's top edge
        assertTrue(isDark(picture, 250, 40 + 3))
    }

    @Test fun `an empty page gives no picture`() = runBlocking {
        val pageId = newPageId()
        saveToDatabase(pageId, emptyList())
        assertNull(MemoPageContent.renderWholePage(context, pageId))
    }

    @Test fun `every page of the day comes back in order, empty ones included for the caller to drop`() = runBlocking {
        val first = newPageId()
        val second = newPageId()
        val third = newPageId()
        saveToDatabase(first, listOf(line(first, 100f, 400f, 300f)))
        saveToDatabase(second, emptyList())
        saveToDatabase(third, listOf(line(third, 100f, 400f, 300f)))

        val pages = MemoPageContent.loadAll(context, listOf(first, second, third))
        assertEquals(listOf(first, second, third), pages.map { it.pageId })
        assertEquals(listOf(false, true, false), pages.map { it.isEmpty })
    }
}

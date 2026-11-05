package com.boox.day.io

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import com.boox.day.SCREEN_WIDTH
import com.boox.day.ai.MemoPageContent
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.Page
import com.boox.day.data.db.Stroke
import com.boox.day.data.db.StrokePoint
import com.boox.day.data.db.TextAnnotation
import com.boox.day.editor.utils.Pen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.UUID

/**
 * A memo export holds what the page holds (Nov 2026): text boxes were missing from every export, so
 * a meeting's title and typed text never reached the file. Checked on pictures: Robolectric has no
 * PDF classes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MemoPageExportTest {
    @get:Rule val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val day = "2026-11-04"

    @After
    fun closeDatabase() {
        AppDatabase.closeDatabase()
    }

    private fun newPageId() = "export-test-" + UUID.randomUUID()

    private fun line(pageId: String, x1: Float, x2: Float, y: Float) = Stroke(
        size = 6f, pen = Pen.BALLPEN, top = y - 3f, bottom = y + 3f, left = x1 - 3f, right = x2 + 3f,
        points = (0..20).map { StrokePoint(x1 + (x2 - x1) * it / 20f, y) }, pageId = pageId
    )

    private fun textBox(pageId: String, x: Float, y: Float) =
        TextAnnotation(content = "Team sync", x = x, y = y, width = 600f, height = 120f, fontSize = 64f, pageId = pageId)

    private fun save(pageId: String, strokes: List<Stroke>, textBoxes: List<TextAnnotation> = emptyList()) = runBlocking {
        val db = AppDatabase.getDatabase(context)
        db.pageDao().create(Page(id = pageId))
        if (strokes.isNotEmpty()) db.strokeDao().create(strokes)
        textBoxes.forEach { db.textAnnotationDao().insert(it) }
    }

    private fun darkPixels(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Int =
        (left until right.coerceAtMost(bitmap.width) step 2).sumOf { x ->
            (top until bottom.coerceAtMost(bitmap.height) step 2).count { y ->
                Color.red(bitmap.getPixel(x, y)) < 128
            }
        }

    /** Exports must run off the main thread. */
    private fun <T> offMain(block: suspend () -> T): T = runBlocking { withContext(Dispatchers.IO) { block() } }

    // ---- The renderer

    @Test fun `text boxes land in the right place on a scaled canvas with a corner`() = runBlocking {
        val pageId = newPageId()
        val page = MemoPageContent.Page(pageId, emptyList(), emptyList(), listOf(textBox(pageId, 400f, 300f)))
        val bitmap = Bitmap.createBitmap(500, 300, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.scale(0.5f, 0.5f)

        MemoPageRenderer.drawMemoPage(context, canvas, page, Offset(100f, 100f))

        // Page (400, 300) is (300, 200) past the corner, and (150, 100) at half size
        assertTrue(darkPixels(bitmap, 150, 100, 450, 160) > 20)
        assertEquals("nothing above or left of it", 0, darkPixels(bitmap, 0, 0, 148, 300) + darkPixels(bitmap, 0, 0, 500, 98))
    }

    @Test fun `bounds take in a title above the ink`() {
        val pageId = newPageId()
        val page = MemoPageContent.Page(pageId, listOf(line(pageId, 100f, 400f, 600f)), emptyList(), listOf(textBox(pageId, 50f, 20f)))
        val bounds = checkNotNull(MemoPageRenderer.contentBounds(page))
        assertEquals(20f, bounds.top, 0f)
        assertEquals(50f, bounds.left, 0f)
    }

    @Test fun `the frame grows for ink past the screen and stays the screen otherwise`() {
        val pageId = newPageId()
        val onScreen = MemoPageContent.Page(pageId, listOf(line(pageId, 100f, SCREEN_WIDTH - 10f, 200f)), emptyList(), emptyList())
        assertEquals(SCREEN_WIDTH, MemoPageRenderer.frame(onScreen).width)

        val wider = MemoPageContent.Page(pageId, listOf(line(pageId, 100f, SCREEN_WIDTH + 400f, 200f)), emptyList(), emptyList())
        assertTrue(MemoPageRenderer.frame(wider).width > SCREEN_WIDTH + 400)

        val leftOfPage = MemoPageContent.Page(pageId, listOf(line(pageId, -300f, 100f, 200f)), emptyList(), emptyList())
        assertTrue(MemoPageRenderer.frame(leftOfPage).left < -300f)
    }

    // ---- One memo per page, PNG and JPG

    @Test fun `a meeting title reaches the exported picture`() {
        val pageId = newPageId()
        save(pageId, listOf(line(pageId, 100f, 400f, 600f)), listOf(textBox(pageId, 100f, 100f)))

        val picture = checkNotNull(offMain { ExportEngine(context).renderBitmapForPage(pageId) })

        assertTrue("title", darkPixels(picture, 100, 100, 700, 220) > 20)
        assertTrue("ink", darkPixels(picture, 100, 590, 400, 610) > 20)
    }

    @Test fun `the picture read for search keeps out typed text`() {
        val pageId = newPageId()
        save(pageId, listOf(line(pageId, 100f, 400f, 600f)), listOf(textBox(pageId, 100f, 100f)))

        val picture = checkNotNull(offMain { ExportEngine(context).renderBitmapForPagePublic(pageId) })

        assertEquals(0, darkPixels(picture, 100, 100, 700, 220))
        assertTrue(darkPixels(picture, 100, 590, 400, 610) > 20)
    }

    @Test fun `an empty page gives no picture and says so`() {
        val pageId = newPageId()
        save(pageId, emptyList())
        assertNull(offMain { ExportEngine(context).renderBitmapForPage(pageId) })

        val outcome = offMain {
            ExportEngine(context).export(
                ExportTarget.Page(pageId), ExportFormat.PNG,
                ExportOptions(copyToClipboard = false, targetFolderUri = folder.root.toUri(), fileName = "page")
            )
        }
        assertTrue(outcome is ExportOutcome.Empty)
        assertEquals(0, folder.root.list()!!.size)
    }

    @Test fun `a picture that can't be saved is reported as failed`() {
        val pageId = newPageId()
        save(pageId, listOf(line(pageId, 100f, 400f, 600f)))
        File(folder.root, "page.png").mkdirs()  // A folder in the file's place

        for (format in listOf(ExportFormat.PNG)) {
            val outcome = offMain {
                ExportEngine(context).export(
                    ExportTarget.Page(pageId), format,
                    ExportOptions(copyToClipboard = false, targetFolderUri = folder.root.toUri(), fileName = "page", overwrite = true)
                )
            }
            assertTrue("was ${outcome::class.simpleName}: ${outcome.message}", outcome is ExportOutcome.Failed)
        }
    }

    @Test fun `a saved picture is reported with its folder`() {
        val pageId = newPageId()
        save(pageId, listOf(line(pageId, 100f, 400f, 600f)))

        val outcome = offMain {
            ExportEngine(context).export(
                ExportTarget.Page(pageId), ExportFormat.PNG,
                ExportOptions(copyToClipboard = false, targetFolderUri = folder.root.toUri(), fileName = "page")
            )
        }

        assertTrue(outcome is ExportOutcome.Saved)
        assertTrue(outcome.message, outcome.message.contains("page.png"))
        assertTrue(File(folder.root, "page.png").length() > 0)
    }

    @Test fun `a bad folder is a failure, not a crash`() {
        val notAFolder = folder.newFile("not-a-folder.txt")
        val pageId = newPageId()
        save(pageId, listOf(line(pageId, 100f, 400f, 600f)))

        val outcome = offMain {
            ExportEngine(context).export(
                ExportTarget.Page(pageId), ExportFormat.PDF,
                ExportOptions(copyToClipboard = false, targetFolderUri = notAFolder.toUri(), layout = ExportLayout.ONE_PER_PAGE)
            )
        }

        assertEquals(ExportOutcome.FAILED_MESSAGE, outcome.message)
    }

    // ---- Compact layout

    @Test fun `a page of typed text alone is exported`() {
        val pageId = newPageId()
        save(pageId, emptyList(), listOf(textBox(pageId, 100f, 100f)))

        val engine = CompactExportEngine(context)
        val measured = offMain { engine.measureAllContent(day, listOf(pageId)) }

        assertEquals(1, measured.size)
        val block = engine.contentBlocks(measured, engine.scaleFactorFor(measured)).single()
        val picture = offMain { engine.renderBlock(block) }
        assertTrue(darkPixels(picture, 0, 0, picture.width, picture.height) > 20)
    }

    @Test fun `empty pages are left out`() {
        val empty = newPageId()
        val full = newPageId()
        save(empty, emptyList())
        save(full, listOf(line(full, 100f, 400f, 600f)))

        val measured = offMain { CompactExportEngine(context).measureAllContent(day, listOf(empty, full)) }

        assertEquals(listOf(full), measured.map { it.pageId })
    }

    @Test fun `a meeting title above the notes is not cropped off`() {
        val pageId = newPageId()
        save(pageId, listOf(line(pageId, 100f, 400f, 900f)), listOf(textBox(pageId, 100f, 40f)))

        val engine = CompactExportEngine(context)
        val measured = offMain { engine.measureAllContent(day, listOf(pageId)) }.single()

        assertTrue("top ${measured.contentTop}", measured.contentTop <= 40)
    }

    @Test fun `a page too tall for one PDF page is split, and nothing is cut`() {
        val pageId = newPageId()
        save(pageId, listOf(line(pageId, 100f, 400f, 60f), line(pageId, 100f, 400f, 9000f)))

        val engine = CompactExportEngine(context)
        val measured = offMain { engine.measureAllContent(day, listOf(pageId)) }
        val scale = engine.scaleFactorFor(measured)
        val blocks = engine.contentBlocks(measured, scale)
        val pages = engine.packContentBlocks(blocks)

        assertTrue("blocks ${blocks.size}", blocks.size > 1)
        assertEquals(measured.single().contentTop, blocks.first().top)
        assertEquals(measured.single().contentBottom, blocks.last().bottom)
        blocks.zipWithNext { a, b -> assertEquals(a.bottom, b.top) }
        assertEquals(blocks.size, pages.sumOf { it.blocks.size })
        pages.forEach { assertTrue("used ${it.usedHeight}", it.remainingHeight >= 0) }

        val last = offMain { engine.renderBlock(blocks.last()) }
        assertTrue("the far ink is in the last part", darkPixels(last, 0, 0, last.width, last.height) > 20)
    }

    @Test fun `ink wider than this screen fits the page`() {
        val pageId = newPageId()
        save(pageId, listOf(line(pageId, 100f, SCREEN_WIDTH + 800f, 300f)))

        val engine = CompactExportEngine(context)
        val measured = offMain { engine.measureAllContent(day, listOf(pageId)) }
        val widest = measured.single().width

        assertTrue(widest > SCREEN_WIDTH + 800)
        assertTrue(engine.scaleFactorFor(measured) * widest <= 556f)
    }

    // ---- Saving

    @Test fun `a second export gets its own name`() = runBlocking {
        val first = ExportFiles.save(context, folder.root.toUri(), "day.pdf", "application/pdf", overwrite = false) { it.write(1) }
        val second = ExportFiles.save(context, folder.root.toUri(), "day.pdf", "application/pdf", overwrite = false) { it.write(2) }

        assertEquals("day.pdf", first.displayName)
        assertEquals("day-2.pdf", second.displayName)
        assertEquals(1, File(folder.root, "day.pdf").readBytes().single().toInt())
    }

    @Test fun `a failed export leaves the earlier file whole and nothing new`() = runBlocking {
        val earlier = File(folder.root, "day.pdf").apply { writeText("earlier") }

        val failed = runCatching {
            ExportFiles.save(context, folder.root.toUri(), "day.pdf", "application/pdf", overwrite = true) {
                it.write(1)
                error("render failed")
            }
        }

        assertTrue(failed.isFailure)
        assertEquals("earlier", earlier.readText())
        assertEquals(listOf("day.pdf"), folder.root.list()!!.toList())
        assertFalse("no temp file left", File(context.cacheDir, "exports").list().orEmpty().any { it.startsWith("writing-") })
    }

    @Test fun `a name without an extension is numbered too`() {
        assertEquals("notes-3", ExportFiles.uniqueName("notes") { it == "notes" || it == "notes-2" })
        assertNotNull(ExportFiles.uniqueName("a.b.pdf") { false })
    }
}

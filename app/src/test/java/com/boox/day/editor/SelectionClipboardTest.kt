package com.boox.day.editor

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import com.boox.day.data.db.Attachment
import com.boox.day.editor.state.ClipboardContent
import com.boox.day.editor.state.SelectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies attachment-only and empty selections survive clipboard serialization without
 * dropping attachments or creating empty pasteable clipboards (Phase 1 C4).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SelectionClipboardTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun attachmentOnlySelectionPreservesAttachmentInClipboard() {
        val attachment = Attachment(
            id = "att-1",
            x = 120,
            y = 240,
            width = 180,
            height = 60,
            filePath = "/sdcard/Documents/report.pdf",
            displayName = "report.pdf",
            fileType = "pdf",
            pageId = "page-1",
        )
        val selection = SelectionState().apply {
            selectedAttachments = listOf(attachment)
        }

        val clip = selection.selectionToClipboard(Offset(20f, 40f), context)

        assertFalse(clip.isEmpty)
        assertTrue(clip.strokes.isEmpty())
        assertTrue(clip.images.isEmpty())
        assertEquals(1, clip.attachments.size)
        assertEquals("att-1", clip.attachments[0].id)
        assertEquals(100, clip.attachments[0].x)
        assertEquals(200, clip.attachments[0].y)
    }

    @Test
    fun emptyClipboardIsReportedAsEmpty() {
        assertTrue(ClipboardContent().isEmpty)
        assertTrue(SelectionState().selectionToClipboard(Offset.Zero, context).isEmpty)
    }

    @Test
    fun panoramaSampleSizeAndExtremeAspectRatioAreBounded() {
        // 8000x2000 panorama: must downsample by 4x so max dim <= 2500 (2000x500)
        assertEquals(4, com.boox.day.io.calculateInSampleSize(8000, 2000))
        // Normal 1860x2480 tablet portrait fits within 2500 maxDim and 6.25M pixels at sample 1
        assertEquals(1, com.boox.day.io.calculateInSampleSize(1860, 2480))
        // Extreme 10000x1 image fitted into 500x500 must clamp height to >= 1 (never 0)
        val (w, h) = com.boox.day.io.fittedImageSize(10000, 1, 500, 500)
        assertEquals(500, w)
        assertEquals(1, h)
    }

    @Test
    fun canvasLimitRectExcludesTasksControlColumn() {
        val tasksRect = com.boox.day.editor.utils.computeCanvasLimitRect(
            viewWidth = 1000, viewHeight = 1400, toolbarHeight = 80, toolbarAtTop = true, controlColumnLeftPx = 240
        )
        assertEquals(240, tasksRect.left)
        assertEquals(80, tasksRect.top)
        assertEquals(1000, tasksRect.right)
        assertEquals(1400, tasksRect.bottom)

        val memosRect = com.boox.day.editor.utils.computeCanvasLimitRect(
            viewWidth = 1000, viewHeight = 1400, toolbarHeight = 80, toolbarAtTop = false, controlColumnLeftPx = 0
        )
        assertEquals(0, memosRect.left)
        assertEquals(0, memosRect.top)
        assertEquals(1320, memosRect.bottom)
    }
}

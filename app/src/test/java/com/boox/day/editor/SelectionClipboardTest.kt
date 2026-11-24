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
}

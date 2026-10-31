package com.boox.day.editor.ui.toolbar

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Owner's decision (31 Oct 2026): on the Go 10.3's Tasks tab, Task AI is on the bar and the full screen
 * switch goes into ≡; Memos keeps the full screen switch on the bar.
 */
class RegularToolbarOverflowTest {

    private val go103Width = 773.dp  // 385 dpi

    @Test
    fun go103TasksBarHasTaskAiAndFullScreenInMenu() {
        assertEquals(
            setOf(BarTool.FullScreen),
            regularToolbarOverflow(barWidth = go103Width, isMemos = false, hasPageNav = true)
        )
    }

    @Test
    fun go103MemosBarKeepsFullScreen() {
        assertEquals(
            setOf(BarTool.MeetingNotes, BarTool.Insert),
            regularToolbarOverflow(barWidth = go103Width, isMemos = true, hasPageNav = true)
        )
    }
}

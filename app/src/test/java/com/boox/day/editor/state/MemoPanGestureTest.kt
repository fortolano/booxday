package com.boox.day.editor.state

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Memos two-finger drag starts only when both fingers move together (BOOX Notes' rule). */
class MemoPanGestureTest {
    private val start = 59f  // 5 mm on a 300 ppi panel

    @Test fun `two fingers moving the same way start a drag`() {
        assertTrue(isTwoFingerPan(listOf(Offset(0f, -70f), Offset(8f, -64f)), start))
    }

    @Test fun `a pinch never starts a drag`() {
        assertFalse(isTwoFingerPan(listOf(Offset(-70f, 0f), Offset(70f, 0f)), start))
    }

    @Test fun `a still finger next to a moving one never starts a drag`() {
        assertFalse(isTwoFingerPan(listOf(Offset(0f, 200f), Offset(3f, 4f)), start))
    }

    @Test fun `fingers that moved less than 5 mm don't start a drag yet`() {
        assertFalse(isTwoFingerPan(listOf(Offset(0f, 40f), Offset(0f, 45f)), start))
    }

    @Test fun `one or three fingers never start a two-finger drag`() {
        assertFalse(isTwoFingerPan(listOf(Offset(0f, 200f)), start))
        assertFalse(isTwoFingerPan(listOf(Offset(0f, 200f), Offset(0f, 200f), Offset(0f, 200f)), start))
    }

    @Test fun `paths a right angle apart still count, wider ones don't`() {
        assertTrue(isTwoFingerPan(listOf(Offset(80f, 0f), Offset(0f, 80f)), start))
        assertFalse(isTwoFingerPan(listOf(Offset(80f, 0f), Offset(-10f, 80f)), start))
    }

    @Test fun `a mostly vertical drag is locked to the vertical axis`() {
        assertEquals(PanAxis.Vertical, panAxis(Offset(20f, -150f)))
        assertEquals(Offset(0f, -12f), PanAxis.Vertical.constrain(Offset(3f, -12f)))
    }

    @Test fun `a mostly horizontal drag is locked to the horizontal axis`() {
        assertEquals(PanAxis.Horizontal, panAxis(Offset(-150f, 30f)))
        assertEquals(Offset(-9f, 0f), PanAxis.Horizontal.constrain(Offset(-9f, 2f)))
    }

    @Test fun `a diagonal drag moves freely`() {
        assertEquals(PanAxis.Free, panAxis(Offset(100f, 100f)))
        assertEquals(Offset(4f, 5f), PanAxis.Free.constrain(Offset(4f, 5f)))
    }
}

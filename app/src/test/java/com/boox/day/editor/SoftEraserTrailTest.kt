package com.boox.day.editor

import com.boox.day.editor.utils.usesSoftEraserTrail
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner's choice, Nov 2026: only the hardware display controller gets BOOX's soft eraser trail. */
class SoftEraserTrailTest {
    @Test fun onlyTheHardwareDisplayControllerGetsTheSoftTrail() {
        assertTrue(usesSoftEraserTrail(hardwareDisplayController = true, androidPen = false))
        // Go tablets keep the white line.
        assertFalse(usesSoftEraserTrail(hardwareDisplayController = false, androidPen = false))
        // The Android pen backend draws its own trail.
        assertFalse(usesSoftEraserTrail(hardwareDisplayController = true, androidPen = true))
    }
}

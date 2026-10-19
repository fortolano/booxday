package com.boox.day.compat

import org.junit.Assert.assertEquals
import org.junit.Test

class TouchPressureRangeTest {
    @Test fun validDeviceRangeIsPreserved() {
        for (range in listOf(1f, 1024f, 4095f, 4096f, 8192f)) {
            assertEquals(range, TouchPressureRange.resolve(range), 0f)
        }
    }

    @Test fun invalidVendorValuesUseTheRequestedFallback() {
        for (range in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(4096f, TouchPressureRange.resolve(range, 4096f), 0f)
        }
    }

    @Test fun anInvalidFallbackCannotReachPressureDivisionOrNativeBrushes() {
        for (fallback in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(4095f, TouchPressureRange.resolve(Float.NaN, fallback), 0f)
        }
    }
}

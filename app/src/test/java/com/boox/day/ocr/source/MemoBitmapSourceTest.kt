package com.boox.day.ocr.source

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Full screen (Oct 2026): memo OCR reads the whole page from the database when ink reaches below the
 * bitmap saved from the normal view (Go 10.3: normal canvas 1386 px, full screen about 2380 px).
 */
class MemoBitmapSourceTest {

    @Test
    fun savedBitmapIsEnoughWhenAllContentIsOnIt() {
        assertFalse(MemoBitmapSource.contentBelowBitmap(contentBottom = 0f, bitmapHeight = 1386))
        assertFalse(MemoBitmapSource.contentBelowBitmap(contentBottom = 900f, bitmapHeight = 1386))
        // A stroke written right at the bottom edge ends a few pixels under it
        assertFalse(MemoBitmapSource.contentBelowBitmap(contentBottom = 1394f, bitmapHeight = 1386))
    }

    @Test
    fun wholePageIsReadWhenContentIsBelowTheSavedBitmap() {
        assertTrue(MemoBitmapSource.contentBelowBitmap(contentBottom = 1420f, bitmapHeight = 1386))
        assertTrue(MemoBitmapSource.contentBelowBitmap(contentBottom = 2350f, bitmapHeight = 1386))
    }

    @Test
    fun fullScreenBitmapCoversTheWholePage() {
        // Saved while in full screen: the bitmap is the whole page
        assertFalse(MemoBitmapSource.contentBelowBitmap(contentBottom = 2350f, bitmapHeight = 2380))
    }
}

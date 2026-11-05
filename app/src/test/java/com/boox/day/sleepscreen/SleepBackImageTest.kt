package com.boox.day.sleepscreen

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

/** The user's way back to their own sleep image (Nov 2026): it must survive a bad pick and BOOX's size limit. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SleepBackImageTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    private fun noise(width: Int, height: Int): Bitmap {
        val random = Random(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val row = IntArray(width)
        for (y in 0 until height) {
            for (x in 0 until width) row[x] = Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
            bitmap.setPixels(row, 0, width, 0, y, width, 1)
        }
        return bitmap
    }

    @Test
    fun evenTheHardestImageAtGo103SizeStaysUnderBooxsLimit() {
        assertTrue(SleepBackImage.store(context) { _, _ -> noise(1860, 2480) })
        assertTrue("BOOX refuses files over 10 MB", SleepBackImage.file(context).length() < 10L * 1024 * 1024)
    }

    @Test
    fun aPictureFitsThePanelEitherWayRoundAndIsNeverEnlarged() {
        assertEquals(1860 to 2480, SleepBackImage.fitted(3000, 4000, 1860, 2480))
        assertEquals(2480 to 1860, SleepBackImage.fitted(4000, 3000, 1860, 2480))
        assertEquals(300 to 200, SleepBackImage.fitted(300, 200, 1860, 2480))
    }

    @Test
    fun aBadPickKeepsTheImageSavedEarlier() {
        assertTrue(SleepBackImage.store(context) { _, _ -> noise(40, 60) })
        val before = SleepBackImage.file(context).readBytes()
        assertFalse(SleepBackImage.store(context) { _, _ -> error("not an image") })
        assertArrayEquals(before, SleepBackImage.file(context).readBytes())
    }

    @Test
    fun cleanUpTakesOnlyBooxDaysOwnFiles() {
        assertTrue(SleepScreenPublisher.isOurImage("booxday-today-1793904683043.png"))
        assertTrue(SleepScreenPublisher.isOurImage("booxday-back-1793904683043.jpg"))
        assertFalse("the user's pictures in Pictures/ stay", SleepScreenPublisher.isOurImage("holiday.jpg"))
    }
}

package com.boox.day.ui.dialogs

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The close X keeps its full 44 dp at the header widths of a Go 6, Go 7 and Go 10.3, also with a long
 * date: it used to be squeezed to 36 dp, and to nothing while a date in another year was showing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w1500dp-h1500dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BooxNotesHeaderFitTest {

    private fun closeWidthDp(headerDp: Int, date: String): Float {
        var widthPx = -1
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                Box(Modifier.width(headerDp.dp)) {
                    BooxNotesHeader(
                        showAll = false,
                        onShowAllChange = {},
                        currentDate = date,
                        datesWithNotes = listOf(date),
                        noteCountByDate = mapOf(date to 1),
                        onDateChange = {},
                        onClose = {},
                        closeModifier = Modifier.onGloballyPositioned { widthPx = it.size.width }
                    )
                }
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        return widthPx.toFloat()
    }

    @Test
    fun theCloseButtonKeepsItsSize() {
        for (headerDp in listOf(530, 621, 718)) {
            for (date in listOf("2026-11-05", "2025-09-30")) {
                assertEquals("$headerDp dp, $date", 44f, closeWidthDp(headerDp, date), 0.5f)
            }
        }
    }
}

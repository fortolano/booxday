package com.boox.day.ui.dialogs

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The "Reminders and sync on BOOX" tip (Nov 2026, owner decision 73) drawn at the Go 10.3's and the
 * Go 6's density, saved to app/build/freeze-tip-previews/ for review on the PC. Checks it fits the
 * Go 6's screen without scrolling.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w1500dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BooxFreezeTipRenderTest {

    private data class Device(val name: String, val density: Float, val screenHeightDp: Int)

    private val devices = listOf(
        Device("go10.3", 385 / 160f, 1031),
        Device("go6", 300 / 160f, 772)
    )

    @Test
    fun popupOnBothTablets() {
        val out = File("build/freeze-tip-previews").apply { mkdirs() }
        for (device in devices) {
            var bounds = Rect.Zero
            val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
            activity.setContent {
                CompositionLocalProvider(LocalDensity provides Density(device.density, 1f)) {
                    Box(Modifier.background(Color.White).onGloballyPositioned { bounds = it.boundsInRoot() }) {
                        BooxFreezeTipCard(onClose = {})
                    }
                }
            }
            shadowOf(Looper.getMainLooper()).idle()

            val root = activity.findViewById<View>(android.R.id.content)
            val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(image))
            val crop = Bitmap.createBitmap(
                image, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt()
            )
            File(out, "${device.name}-freeze-tip.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }

            // Fits well inside the screen, so the text never needs scrolling
            val heightDp = bounds.height / device.density
            assertTrue("${device.name} popup is $heightDp dp tall", heightDp < device.screenHeightDp * 0.8f)
        }
    }
}

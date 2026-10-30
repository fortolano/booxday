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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/**
 * The About header with the Support and Check for Updates buttons (Oct 2026), drawn at the Go 10.3's and the Go 6's
 * dialog width and density, saved to app/build/about-header-previews/ for review on the PC.
 * Robolectric native graphics: real text and shapes, as on the tablet. Checks the button is a full
 * touch target and the header stays on its two lines on the Go 6.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w1500dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AboutHeaderRenderTest {

    /** The About dialog's width (ModernDialog width fraction, at most 570 dp) and BooxDay's density. */
    private data class Device(val name: String, val dialogWidthDp: Int, val density: Float)

    private val devices = listOf(
        Device("go10.3", 570, 385 / 160f),  // 773 dp × 0.82, capped at 570 dp
        Device("go6", 515, 300 / 160f)      // 572 dp × 0.9
    )

    @Test
    fun headerAndButtonOnBothTablets() {
        val out = File("build/about-header-previews").apply { mkdirs() }
        for (device in devices) {
            val bounds = mutableMapOf<String, Rect>()
            val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
            activity.setContent {
                CompositionLocalProvider(LocalDensity provides Density(device.density, 1f)) {
                    Column(Modifier.background(Color.White)) {
                        Box(
                            Modifier
                                .width(device.dialogWidthDp.dp)
                                .background(Color.White)
                                .onGloballyPositioned { bounds["header"] = it.boundsInRoot() }
                        ) {
                            AboutHeader(versionLabel = "Version 1.0.48", onDismiss = {}, appName = "BooxDay", onSupport = {})
                        }
                        Box(Modifier.padding(16.dp).onGloballyPositioned { bounds["support"] = it.boundsInRoot() }) {
                            SupportButton(onClick = {})
                        }
                        for (checking in listOf(false, true)) {
                            Box(Modifier.padding(16.dp).onGloballyPositioned { bounds["button-$checking"] = it.boundsInRoot() }) {
                                CheckForUpdatesButton(checking = checking, onClick = {})
                            }
                        }
                    }
                }
            }
            shadowOf(Looper.getMainLooper()).idle()

            val root = activity.findViewById<View>(android.R.id.content)
            val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(image))
            for ((name, rect) in bounds) {
                val crop = Bitmap.createBitmap(
                    image, rect.left.toInt(), rect.top.toInt(), rect.width.toInt(), rect.height.toInt()
                )
                File(out, "${device.name}-$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }

            // The button is the standard 44 dp touch target (bounds are measured inside the padding)
            for (checking in listOf(false, true)) {
                val height = bounds.getValue("button-$checking").height
                assertEquals("${device.name} button height (checking=$checking)", 44 * device.density, height, 1.5f)
            }
            assertEquals("${device.name} support button height", 44 * device.density, bounds.getValue("support").height, 1.5f)
            // Two lines (name, version) beside the buttons: about 81 dp; a wrapped name would add ~27 dp
            val headerDp = bounds.getValue("header").height / device.density
            assertTrue("${device.name} header is $headerDp dp tall", headerDp < 95f)
            // The button is drawn: dark fill (#3A3A3A) just inside its left edge
            val button = bounds.getValue("button-false")
            val fill = image.getPixel((button.left + 6 * device.density).toInt(), button.center.y.toInt())
            assertTrue("${device.name} button fill", abs(android.graphics.Color.red(fill) - 0x3A) < 24)
        }
    }
}

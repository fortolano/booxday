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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.boox.day.api.OpenRouterConnect
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
 * The OpenRouter introduction and the "AI is on" result (Oct 2026), drawn at the Go 10.3's and the
 * Go 6's density and screen size, saved to app/build/openrouter-intro-previews/ for review on the PC.
 * The introduction must fit the Go 6's popup height without scrolling (plan B1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w1500dp-h2000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OpenRouterIntroRenderTest {

    private data class Device(val name: String, val heightDp: Int, val density: Float)

    private val devices = listOf(
        Device("go10.3", 1031, 385 / 160f),
        Device("go6", 772, 300 / 160f)
    )

    private fun render(device: Device, name: String, content: @Composable () -> Unit): Float {
        val bounds = mutableMapOf<String, Rect>()
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            CompositionLocalProvider(LocalDensity provides Density(device.density, 1f)) {
                Column(Modifier.background(Color.White)) {
                    Box(Modifier.onGloballyPositioned { bounds["card"] = it.boundsInRoot() }) { content() }
                }
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        val root = activity.findViewById<View>(android.R.id.content)
        val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(image))
        val rect = bounds.getValue("card")
        val crop = Bitmap.createBitmap(image, rect.left.toInt(), rect.top.toInt(), rect.width.toInt(), rect.height.toInt())
        val out = File("build/openrouter-intro-previews").apply { mkdirs() }
        File(out, "${device.name}-$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return rect.height / device.density
    }

    @Test
    fun introFitsBothTabletsWithoutScrolling() {
        for (device in devices) {
            for (rejected in listOf(false, true)) {
                val heightDp = render(device, if (rejected) "intro-rejected" else "intro") {
                    OpenRouterIntroCard(rejected = rejected, onNotNow = {}, onContinue = {}, onHaveKey = {})
                }
                // The popup's height limit on the tablet (EInkTheme.PopupOverlay.maxHeight, 82% of the
                // screen: Go 6 633 dp, Go 10.3 845 dp); taller would scroll. Its width is 460 dp on both.
                val limit = device.heightDp * 0.82f
                assertTrue("${device.name} intro (rejected=$rejected) is $heightDp dp, limit $limit", heightDp < limit)
            }
        }
    }

    @Test
    fun resultCards() {
        val states = listOf(
            "connected-waiting" to (OpenRouterConnect.Status.Connected(true, false, null) to true),
            "connected-free" to (OpenRouterConnect.Status.Connected(true, true, null) to false),
            "connected-unchecked" to (OpenRouterConnect.Status.Connected(false, false, null) to false),
            "failed-retry" to (OpenRouterConnect.Status.Failed("Couldn't reach OpenRouter. Check the Wi-Fi, then tap Try Again.", canRetry = true) to true),
            "failed-final" to (OpenRouterConnect.Status.Failed("OpenRouter says this connection code has expired or was already used. Tap Connect OpenRouter again.") to false)
        )
        for (device in devices) {
            for ((name, state) in states) {
                render(device, name) {
                    ConnectResultCard(
                        status = state.first, aiEnabled = true, featureWaiting = state.second, retrying = false,
                        onClose = {}, onContinue = {}, onTryAgain = {}, onConnectAgain = {}
                    )
                }
            }
        }
    }
}

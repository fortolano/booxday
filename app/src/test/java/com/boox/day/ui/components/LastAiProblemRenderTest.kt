package com.boox.day.ui.components

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
import com.boox.day.api.ai.LastAiProblem
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The "Last AI problem" line under AI Models (2 Nov 2026, owner's choice), drawn at the Go 6's and
 * the Go 10.3's AI Settings width and density, saved to app/build/ai-problem-previews/ for review.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w1500dp-h2000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LastAiProblemRenderTest {

    private data class Device(val name: String, val cardWidthDp: Int, val density: Float)

    private val devices = listOf(
        Device("go10.3", 540, 385 / 160f),
        Device("go6", 480, 300 / 160f)
    )

    @Test
    fun lastProblemUnderAiModels() {
        val message = "The AI model couldn't answer this. Try again, or pick another model in AI Settings."
        LastAiProblem.noteShown("Convert to Tasks", message)
        val problem = checkNotNull(LastAiProblem.last)
        assertEquals(message, LastAiProblem.summary(problem).substringAfter(": "))

        val out = File("build/ai-problem-previews").apply { mkdirs() }
        for (device in devices) {
            var bounds = Rect.Zero
            val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
            activity.setContent {
                CompositionLocalProvider(LocalDensity provides Density(device.density, 1f)) {
                    Column(Modifier.background(Color.White)) {
                        Box(
                            Modifier.width(device.cardWidthDp.dp).padding(16.dp)
                                .onGloballyPositioned { bounds = it.boundsInRoot() }
                        ) { AiModelsCard(aiEnabled = true) }
                    }
                }
            }
            shadowOf(Looper.getMainLooper()).idle()
            val root = activity.findViewById<View>(android.R.id.content)
            val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(image))
            val crop = Bitmap.createBitmap(image, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
            File(out, "${device.name}-ai-models.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}

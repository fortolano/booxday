package com.boox.day.sleepscreen

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Pictures of the turn-on popup and the "When turned off" row, for the owner's review (Nov 2026). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w1500dp-h3000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SleepBackImageRenderTest {

    private val tablets = listOf(Triple("go10.3", 385, 730.dp), Triple("go6", 300, 530.dp))

    @Test
    fun pictures() {
        val sample = Bitmap.createBitmap(270, 360, Bitmap.Config.ARGB_8888).apply {
            val canvas = Canvas(this)
            canvas.drawColor(android.graphics.Color.WHITE)
            val paint = Paint().apply { color = android.graphics.Color.BLACK }
            canvas.drawCircle(135f, 150f, 80f, paint)
            canvas.drawRect(40f, 280f, 230f, 300f, paint)
        }.asImageBitmap()
        for ((name, dpi, cardWidth) in tablets) {
            render("$name-popup", dpi, null) { SleepTurnOnCard({}, {}, {}) }
            render("$name-row-none", dpi, cardWidth) { SleepBackImageRow(null) {} }
            render("$name-row-image", dpi, cardWidth) { SleepBackImageRow(sample) {} }
        }
    }

    private fun render(name: String, dpi: Int, width: Dp?, content: @Composable () -> Unit) {
        var box = Rect.Zero
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            CompositionLocalProvider(LocalDensity provides Density(dpi / 160f, 1f)) {
                val size = if (width != null) Modifier.width(width) else Modifier
                Box(size.background(Color.White).padding(8.dp).onGloballyPositioned { box = it.boundsInRoot() }) {
                    Column { content() }
                }
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        val root = activity.findViewById<View>(android.R.id.content)
        val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(image))
        val crop = Bitmap.createBitmap(image, box.left.toInt(), box.top.toInt(), box.width.toInt(), box.height.toInt())
        val out = File("build/sleep-agenda-previews/back-image").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

package com.boox.day.sleepscreen

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Pictures of the "What to show" rows in Preferences, for the owner's review (Nov 2026). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w1500dp-h3000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SleepContentRowsRenderTest {

    @Test
    fun rows() {
        render("rows-default", SleepScreenContent(), withMemo = true)
        render("rows-month-events", SleepScreenContent(tasks = false, memo = false, month = true), withMemo = true)
        render("rows-last-one", SleepScreenContent(tasks = false, memo = false, month = false), withMemo = false)
    }

    private fun render(name: String, choice: SleepScreenContent, withMemo: Boolean) {
        var box = Rect.Zero
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            CompositionLocalProvider(LocalDensity provides Density(300 / 160f, 1f)) {
                Box(Modifier.width(500.dp).background(Color.White).padding(12.dp).onGloballyPositioned { box = it.boundsInRoot() }) {
                    SleepContentRows(choice, withMemo) {}
                }
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        val root = activity.findViewById<View>(android.R.id.content)
        val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(image))
        val crop = Bitmap.createBitmap(image, box.left.toInt(), box.top.toInt(), box.width.toInt(), box.height.toInt())
        val out = File("build/sleep-agenda-previews/choices").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

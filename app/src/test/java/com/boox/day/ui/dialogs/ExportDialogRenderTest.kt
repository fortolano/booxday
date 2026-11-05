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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.boox.day.io.ExportChoices
import com.boox.day.io.ExportKind
import com.boox.day.io.ExportWhat
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
 * The Export dialog fits with no scrolling on every tablet size, at the app's own density, in every
 * format, also with larger text (Nov 2026). Pictures go to build/export-dialog-previews/.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w2600dp-h3400dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExportDialogRenderTest {

    private data class Device(val name: String, val width: Int, val height: Int, val density: Float, val dialog: Int, val preview: Int)

    private val devices = listOf(
        Device("go6", 572, 772, 300 / 160f, 538, 124),
        Device("go7", 674, 896, 300 / 160f, 573, 124),
        Device("go10", 773, 1031, 385 / 160f, 657, 130),
        Device("max13", 997, 1330, 385 / 160f, 700, 130)
    )

    private val cases = listOf(
        "pdf" to ExportChoices(),
        "images" to ExportChoices(kind = ExportKind.IMAGES),
        "markdown" to ExportChoices(kind = ExportKind.MARKDOWN),
        "dates" to ExportChoices(what = ExportWhat.DATES, typedText = true, eventsAndTasks = true)
    )

    @Test
    fun fitsEveryTabletInEveryFormat() {
        for (device in devices) for ((name, choices) in cases) for (fontScale in listOf(1f, 1.15f)) {
            val height = render(device, name, choices, fontScale)
            assertTrue("${device.name} $name at $fontScale: $height of ${device.height}", height <= device.height * 0.92f)
        }
    }

    /** Returns the dialog's height in dp. */
    private fun render(device: Device, name: String, choices: ExportChoices, fontScale: Float): Float {
        var dialog = Rect.Zero
        var screen = Rect.Zero
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            CompositionLocalProvider(LocalDensity provides Density(device.density, fontScale)) {
                Box(
                    Modifier
                        .size(device.width.dp, device.height.dp)
                        .background(Color.White)
                        .onGloballyPositioned { screen = it.boundsInRoot() },
                    contentAlignment = Alignment.Center
                ) {
                    ExportDialogContent(
                        modifier = Modifier.width(device.dialog.dp).onGloballyPositioned { dialog = it.boundsInRoot() },
                        ui = ExportDialogUi(
                            choices = choices,
                            showPage = choices.kind != ExportKind.MARKDOWN,
                            showDays = true,
                            from = "Mon 26 Oct",
                            to = "Sun 1 Nov",
                            rangeProblem = null,
                            summary = "1 note, 12 pictures · about 700 KB",
                            picture = null,
                            previewWidth = device.preview.dp,
                            folder = "Documents/BooxDay/Daily Notes",
                            canExport = true
                        ),
                        actions = ExportDialogActions()
                    )
                }
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        val root = activity.findViewById<View>(android.R.id.content)
        val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(image))
        val crop = Bitmap.createBitmap(image, screen.left.toInt(), screen.top.toInt(), screen.width.toInt(), screen.height.toInt())
        val out = File("build/export-dialog-previews").apply { mkdirs() }
        File(out, "${device.name}-$name-$fontScale.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return dialog.height / device.density
    }
}

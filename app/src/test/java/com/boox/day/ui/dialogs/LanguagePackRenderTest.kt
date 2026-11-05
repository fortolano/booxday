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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
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
import com.boox.day.ocr.DigitalInkModelManager
import com.boox.day.ocr.DigitalInkModelManager.DownloadState
import com.boox.day.ocr.HandwritingLanguages
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

/** The nine languages and the Other language card fit each tablet's panel (Nov 2026). Pictures in build/language-previews/. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w1500dp-h3000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LanguagePackRenderTest {

    private data class Device(val name: String, val width: Int, val height: Int, val density: Float, val panelWidth: Int, val panelHeight: Int)

    private val devices = listOf(
        Device("go6", 572, 772, 300 / 160f, 538, 664),
        Device("go10", 773, 1031, 385 / 160f, 657, 866)
    )

    private val nine = DigitalInkModelManager.getAvailableLanguages().filter { it.tag in HandwritingLanguages.FIXED }
        .map { if (it.tag == "en-US") it.copy(isDownloaded = true, isActive = true, downloadState = DownloadState.READY) else it }

    private fun other(active: Boolean) = DigitalInkModelManager.LanguageInfo(
        "uk", "Ukrainian", "~14MB", isDownloaded = true, isActive = active, downloadState = DownloadState.READY
    )

    @Test
    fun theLanguagesFitBothTablets() {
        assertEquals(9, nine.size)
        for (device in devices) {
            val five = nine.mapIndexed { i, language ->
                if (i % 2 == 0 && i > 0) language.copy(isDownloaded = true, downloadState = DownloadState.READY) else language
            }
            val cases = listOf(
                Triple("1-none", nine, null), Triple("2-chosen", nine, other(false)),
                Triple("3-active", nine, other(true)), Triple("6-five-downloaded", five, other(false))
            )
            for ((name, cards, chosen) in cases) {
                val height = render(device, name) { modifier ->
                    LanguagePackPanel(
                        modifier = modifier, languageStates = cards, other = chosen, downloadedCount = 2,
                        onDismiss = {}, onChooseOther = {}, onDownload = {}, onDelete = {}, onSetActive = {}
                    )
                }
                assertTrue("${device.name} $name: $height of ${device.panelHeight}", height <= device.panelHeight)
            }
        }
    }

    @Test
    fun theLanguageListFitsBothTablets() {
        val all = HandwritingLanguages.others
        val report = StringBuilder("${all.size} languages\n")
        all.groupBy { it.name.first().uppercaseChar() }.toSortedMap().forEach { (letter, list) -> report.appendLine("$letter ${list.size}") }
        all.forEach { report.appendLine("${it.tag}\t${it.name}") }
        File("build/language-previews").mkdirs()
        File("build/language-previews/names.txt").writeText(report.toString())

        val letters = HandwritingLanguages.letters()
        for (device in devices) {
            for ((name, letter) in listOf("4-list" to 'U', "5-list-long" to 'S')) {
                val matching = all.filter { it.name.startsWith(letter) }
                val height = render(device, name) { modifier ->
                    LanguagePickerPanel(
                        modifier = modifier, letters = letters, letter = letter, languages = matching.take(16), chosenTag = "uk",
                        page = 0, pageCount = (matching.size + 15) / 16, onLetter = {}, onPage = {}, onPick = {}, onCancel = {}
                    )
                }
                assertTrue("${device.name} $name: $height of ${device.panelHeight}", height <= device.panelHeight)
            }
        }
    }

    /** Returns the panel's height in dp. */
    private fun render(device: Device, name: String, content: @Composable (Modifier) -> Unit): Float {
        var panel = Rect.Zero
        var screen = Rect.Zero
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            CompositionLocalProvider(LocalDensity provides Density(device.density, 1f)) {
                Box(
                    Modifier
                        .size(device.width.dp, device.height.dp)
                        .background(Color.White)
                        .onGloballyPositioned { screen = it.boundsInRoot() },
                    contentAlignment = Alignment.Center
                ) {
                    content(
                        Modifier
                            .width(device.panelWidth.dp)
                            .wrapContentHeight(Alignment.Top)
                            .onGloballyPositioned { panel = it.boundsInRoot() }
                    )
                }
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        val root = activity.findViewById<View>(android.R.id.content)
        val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(image))
        val crop = Bitmap.createBitmap(image, screen.left.toInt(), screen.top.toInt(), screen.width.toInt(), screen.height.toInt())
        val out = File("build/language-previews").apply { mkdirs() }
        File(out, "${device.name}-$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return panel.height / device.density
    }
}

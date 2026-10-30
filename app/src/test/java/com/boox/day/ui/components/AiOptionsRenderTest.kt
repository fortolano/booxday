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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.boox.day.data.ai.AiPreferences
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
 * The AI options card (29 Oct 2026) and the reminders setup step (plan B2), drawn at the Go 6's and
 * the Go 10.3's AI Settings width and density, saved to app/build/ai-options-previews/ for review.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w1500dp-h2000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AiOptionsRenderTest {

    private data class Device(val name: String, val cardWidthDp: Int, val density: Float)

    private val devices = listOf(
        Device("go10.3", 540, 385 / 160f),
        Device("go6", 480, 300 / 160f)
    )

    private fun render(device: Device, name: String, content: @Composable () -> Unit): Float {
        val bounds = mutableMapOf<String, Rect>()
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            CompositionLocalProvider(LocalDensity provides Density(device.density, 1f)) {
                Column(Modifier.background(Color.White)) {
                    Box(
                        Modifier.width(device.cardWidthDp.dp).padding(16.dp)
                            .onGloballyPositioned { bounds["card"] = it.boundsInRoot() }
                    ) { content() }
                }
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        val root = activity.findViewById<View>(android.R.id.content)
        val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(image))
        val rect = bounds.getValue("card")
        val crop = Bitmap.createBitmap(image, rect.left.toInt(), rect.top.toInt(), rect.width.toInt(), rect.height.toInt())
        val out = File("build/ai-options-previews").apply { mkdirs() }
        File(out, "${device.name}-$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return rect.height / device.density
    }

    @Test
    fun aiOptionsCardOnBothTablets() {
        for (device in devices) {
            val heightDp = render(device, "ai-options") {
                AiOptionsContent(AiPreferences(includeSocialReminders = false), enabled = true) {}
            }
            // A short card: the questionnaire it replaces had five tabs
            assertTrue("${device.name} AI options is $heightDp dp", heightDp < 420f)
            render(device, "ai-options-off") { AiOptionsContent(AiPreferences(), enabled = false) {} }
            val nextWeek = java.time.LocalDate.now().plusDays(6).toString()
            render(device, "ai-options-vacation") {
                AiOptionsContent(AiPreferences(vacationMode = true, vacationUntil = nextWeek), enabled = true) {}
            }
            render(device, "vacation-notice") { VacationNoticeText(vacationNoticeText(nextWeek)) }
        }
    }

    @Test
    fun vacationNoticeWords() {
        val today = java.time.LocalDate.of(2026, 10, 29)
        assertTrue(vacationNoticeText("2026-11-05", today).startsWith("Vacation mode is on until Thu 5 Nov"))
        assertTrue(vacationNoticeText("2027-01-04", today).contains("Mon 4 Jan 2027"))
        assertTrue(vacationNoticeText(null, today).contains("until you turn it off"))
    }

    @Test
    fun reminderStepsOnBothTablets() {
        for (device in devices) {
            render(device, "reminders-step1") {
                SetupStep(
                    step = 1, steps = 2,
                    title = "Reminders need notifications",
                    why = "Allow BooxDay to show notifications, then choose which reminders you want.",
                    buttonText = "Allow Notifications",
                    onClick = {}
                )
            }
            render(device, "reminders-step2") {
                SetupStep(
                    step = 2, steps = 2,
                    title = "Let reminders arrive on time",
                    why = "BOOX pauses apps in the background to save battery. Allow BooxDay to run in the background so reminders come on time.",
                    buttonText = "Allow in Android",
                    onClick = {}
                )
            }
        }
    }
}

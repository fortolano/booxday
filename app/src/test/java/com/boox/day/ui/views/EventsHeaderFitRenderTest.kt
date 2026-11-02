package com.boox.day.ui.views

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.test.core.app.ApplicationProvider
import com.boox.day.R
import com.boox.day.ui.components.CalendarSourceFilterIcons
import com.boox.day.ui.state.CalendarSourceFilter
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The events panel's header (Events and Due tabs, then the calendar filter icons) with all four
 * sources, at the panel width of the Go 10.3 (40 % of 773 dp) and the Go 6 (44 % of 572 dp), the
 * same row as MainScreen's collapsed panel. Prints whether the icons fit and saves pictures to
 * app/build/events-header/. Picture runs only (needs the app's resources).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w1500dp-h3000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EventsHeaderFitRenderTest {

    private data class Device(val name: String, val panelDp: Float, val density: Float)

    @Test
    fun theFourFilterIconsFitBesideTheTabs() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        assumeTrue(runCatching { context.resources.getDrawable(R.drawable.ic_google_calendar, null) }.isSuccess)
        for (device in listOf(Device("go10.3", 773 * 0.4f, 385 / 160f), Device("go6", 572 * 0.44f, 300 / 160f))) {
            for ((label, counts) in listOf("one-digit" to (7 to 3), "two-digit" to (99 to 99))) {
                val (events, due) = counts
                val bounds = mutableMapOf<String, Rect>()
                val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
                activity.setContent {
                    CompositionLocalProvider(LocalDensity provides Density(device.density, 1f)) {
                        Column(Modifier.background(Color.White)) {
                            Box(Modifier.width(device.panelDp.dp).background(Color(0xFFEEEEEE)).onGloballyPositioned { bounds["panel"] = it.boundsInRoot() }) {
                                Row(
                                    Modifier.fillMaxWidth().padding(start = 10.dp, end = 7.dp, top = 8.dp, bottom = 4.dp).background(Color.White),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(Modifier.onGloballyPositioned { bounds["tabs"] = it.boundsInRoot() }) {
                                        EventsPanelTabs(selectedTab = "events", onTabSelected = {}, eventsCount = events, dueCount = due)
                                    }
                                    Box(Modifier.onGloballyPositioned { bounds["icons"] = it.boundsInRoot() }) {
                                        CalendarSourceFilterIcons(
                                            CalendarSourceFilter(), { true },
                                            hasGoogleAccount = true, hasOutlookAccount = true, hasCalDavAccount = true,
                                            selectedDayOfMonth = 2, isCompact = true
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                shadowOf(Looper.getMainLooper()).idle()
                val dp = { px: Float -> px / device.density }
                val panel = bounds.getValue("panel")
                val tabs = bounds.getValue("tabs")
                val icons = bounds.getValue("icons")
                val inner = dp(panel.width) - 17f
                println(
                    "${device.name} $label: panel inner %.1f dp, tabs %.1f dp, icons %.1f dp, gap %.1f dp, icons end %.1f of %.1f dp"
                        .format(inner, dp(tabs.width), dp(icons.width), dp(icons.left - tabs.right), dp(icons.right - panel.left), dp(panel.width) - 7f)
                )
                val root = activity.findViewById<View>(android.R.id.content)
                val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(image))
                val crop = Bitmap.createBitmap(image, panel.left.toInt(), panel.top.toInt(), panel.width.toInt(), panel.height.toInt())
                val out = File("build/events-header").apply { mkdirs() }
                File(out, "${device.name}-$label-${System.getProperty("variant") ?: "now"}.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    /** The Go 6 draft (owner's option A): one filter button, and the list it opens. */
    @Test
    fun theGo6FilterButtonDraft() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        assumeTrue(runCatching { context.resources.getDrawable(R.drawable.ic_google_calendar, null) }.isSuccess)
        val density = 300 / 160f
        val panelDp = 572 * 0.44f
        val states = listOf(
            "1-all-shown" to Pair(CalendarSourceFilter(), false),
            "2-outlook-hidden" to Pair(CalendarSourceFilter(showOutlook = false), false),
            "3-list-open" to Pair(CalendarSourceFilter(showOutlook = false), true)
        )
        for ((name, state) in states) {
            val (filter, open) = state
            val bounds = mutableMapOf<String, Rect>()
            val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
            activity.setContent {
                CompositionLocalProvider(LocalDensity provides Density(density, 1f)) {
                    Column(Modifier.background(Color.White)) {
                        Column(Modifier.width(panelDp.dp).background(Color(0xFFEEEEEE)).onGloballyPositioned { bounds["panel"] = it.boundsInRoot() }) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 10.dp, end = 7.dp, top = 8.dp, bottom = 4.dp).background(Color.White),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                EventsPanelTabs(selectedTab = "events", onTabSelected = {}, eventsCount = 99, dueCount = 99)
                                com.boox.day.ui.components.CalendarSourceFilterButton(filtered = !filter.showOutlook) {}
                            }
                            if (open) {
                                // Where the popup opens: under the button, right-aligned
                                Box(Modifier.fillMaxWidth().padding(end = 7.dp), contentAlignment = Alignment.TopEnd) {
                                    com.boox.day.ui.components.CalendarSourceFilterList(
                                        filter, hasGoogleAccount = true, hasOutlookAccount = true, hasCalDavAccount = true
                                    ) {}
                                }
                            }
                        }
                    }
                }
            }
            shadowOf(Looper.getMainLooper()).idle()
            val panel = bounds.getValue("panel")
            val root = activity.findViewById<View>(android.R.id.content)
            val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(image))
            val crop = Bitmap.createBitmap(image, panel.left.toInt(), panel.top.toInt(), panel.width.toInt(), panel.height.toInt())
            File(File("build/events-header").apply { mkdirs() }, "go6-button-$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}

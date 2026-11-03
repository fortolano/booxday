package com.boox.day.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.boox.day.data.model.AccountType
import com.boox.day.data.model.CalendarSelection
import com.boox.day.data.model.ConnectedAccount
import com.boox.day.data.model.ProviderId
import com.boox.day.ui.theme.EInkTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Owner decision 78 (Nov 2026): a CalDAV account used for what its server has. Pictures of the new
 * screens at the Go 10.3's and the Go 6's size and density, saved to app/build/caldav-uses-previews/
 * for the owner's review.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w1500dp-h3000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CalDavUsesRenderTest {

    private data class Device(val name: String, val dialogDp: Int, val density: Float)

    private val devices = listOf(Device("go10.3", 657, 385 / 160f), Device("go6", 538, 300 / 160f))

    private fun render(device: Device, name: String, widthDp: Int, content: @Composable () -> Unit) {
        val bounds = mutableMapOf<String, Rect>()
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            CompositionLocalProvider(LocalDensity provides Density(device.density, 1f)) {
                Column(Modifier.background(Color.White)) {
                    Box(Modifier.width(widthDp.dp).onGloballyPositioned { bounds["it"] = it.boundsInRoot() }) { content() }
                }
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        val root = activity.findViewById<View>(android.R.id.content)
        val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(image))
        val rect = bounds.getValue("it")
        val crop = Bitmap.createBitmap(image, rect.left.toInt(), rect.top.toInt(), rect.width.toInt(), rect.height.toInt())
        val out = File("build/caldav-uses-previews").apply { mkdirs() }
        File(out, "${device.name}-$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun theNewScreensOnBothTablets() {
        for (device in devices) {
            val popup = 460
            render(device, "1-use-for", popup) {
                PopupFrame { CalDavUseForContent("cloud.example.com", calendarCount = 4, taskListCount = 2, useCalendars = true, useTasks = true) }
            }
            render(device, "2-switch-task-sync", popup) {
                PopupFrame { TaskSyncSwitchContent("Google Tasks", "cloud.example.com") }
            }
            render(device, "3-row-both", device.dialogDp - 48) {
                Row(CalDavUses(4, 2, calendarsOn = true, tasksOn = true, tasksNote = "Task Sync: Tasks, Errands"))
            }
            render(device, "4-row-calendars-off", device.dialogDp - 48) {
                Row(CalDavUses(4, 2, calendarsOn = false, tasksOn = true, tasksNote = "Task Sync: Tasks, Errands"))
            }
            render(device, "5-row-tasks-only", device.dialogDp - 48) {
                Row(CalDavUses(0, 1, calendarsOn = true, tasksOn = false, tasksNote = "Off: Task Sync uses Google Tasks"), calendars = emptyList(), server = "radicale.home.lan")
            }
        }
    }

    @Composable
    private fun Row(uses: CalDavUses, calendars: List<CalendarSelection> = CALENDARS, server: String = "cloud.example.com") {
        AccountCalendarList(
            account = ConnectedAccount("a2", ProviderId.CALDAV, if (server == "cloud.example.com") AccountType.CALDAV_NEXTCLOUD else AccountType.CALDAV_OTHER, "fort", serverUrl = "https://$server/"),
            calendars = calendars,
            onDisconnectAccount = {}, onToggleCalendar = {}, onSetDefaultCalendar = {}, onRefreshCalendars = {},
            isExpanded = true,
            lastSyncMessage = "Synced 2 min ago",
            uses = uses
        )
    }

    @Composable
    private fun PopupFrame(content: @Composable () -> Unit) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                .padding(EInkTheme.PopupOverlay.padding)
        ) { content() }
    }

    private companion object {
        val CALENDARS = listOf("Personal" to "#1BADF8", "Work" to "#CC73E1", "Family" to "#FF9500", "Birthdays" to "#63DA38")
            .mapIndexed { i, (name, color) ->
                CalendarSelection("s$i", "a2", "/cal/$i/", name, color, isFetchEnabled = i != 3, isDefaultForAccount = i == 0, displayOrder = i)
            }
    }
}

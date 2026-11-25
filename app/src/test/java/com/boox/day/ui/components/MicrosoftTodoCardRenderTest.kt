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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The Task Sync card with Microsoft To Do (Nov 2026, owner decisions 79-84), made-up accounts, at
 * the Preferences card width and density of the Go 10.3 and the Go 6, for the owner's review.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w1500dp-h3000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MicrosoftTodoCardRenderTest {

    private data class Device(val name: String, val cardWidthDp: Int, val density: Float)

    // As TaskSyncCardRenderTest: 85 % of 773 dp on the Go 10.3, 94 % of 572 dp on the Go 6, less the dialog's padding
    private val devices = listOf(Device("go10.3", 630, 385 / 160f), Device("go6", 510, 300 / 160f))

    private val out = File(
        "C:\\Users\\fort\\AppData\\Local\\Temp\\claude\\3288566c5756dbd2\\54140f61-22f4-4b11-b16c-ca9ba841f56d\\scratchpad\\screens\\todo"
    )

    private fun render(device: Device, name: String, content: @Composable () -> Unit) {
        val bounds = mutableMapOf<String, Rect>()
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            CompositionLocalProvider(LocalDensity provides Density(device.density, 1f)) {
                Column(Modifier.background(Color.White)) {
                    Box(Modifier.width(device.cardWidthDp.dp).onGloballyPositioned { bounds["card"] = it.boundsInRoot() }) {
                        content()
                    }
                }
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        val root = activity.findViewById<View>(android.R.id.content)
        val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(image))
        val rect = bounds.getValue("card")
        val crop = Bitmap.createBitmap(image, rect.left.toInt(), rect.top.toInt(), rect.width.toInt(), rect.height.toInt())
        out.mkdirs()
        File(out, "${device.name}-$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun card(state: TaskSyncCardState): @Composable () -> Unit = {
        SettingCard(title = "Task Sync", description = TASK_SYNC_DESCRIPTION) { TaskSyncCardContent(state) }
    }

    private val google = TaskSyncChoice("google:sam@gmail.com", "Google Tasks", "sam@gmail.com", isGoogle = true)
    private val microsoft = TaskSyncChoice("microsoft:o", "Microsoft To Do", "sam.lee@outlook.com", isGoogle = false, isMicrosoft = true)
    private val nextcloud = TaskSyncChoice("caldav:n", "Nextcloud", "sam", isGoogle = false)
    private val lists = listOf("Tasks", "Groceries", "Work", "House")
        .mapIndexed { i, title -> TaskSyncCardList("t-$i", title, ticked = title in setOf("Tasks", "Work")) }

    @Test
    fun theCardWithMicrosoftToDoOnBothTablets() {
        for (device in devices) {
            render(device, "1-off-outlook-chosen", card(
                TaskSyncCardState(on = false, chosen = microsoft, choices = listOf(google, microsoft, nextcloud))
            ))
            render(device, "2-on-synced-with-todo", card(
                TaskSyncCardState(on = true, chosen = microsoft, lists = lists, newTasksListId = "t-0", status = "Last synced today at 10:42")
            ))
            render(device, "3-reconnect", card(
                TaskSyncCardState(
                    on = true, chosen = microsoft, lists = lists, newTasksListId = "t-0",
                    status = "Waiting for you", needsReconnect = true, deletesWaiting = 25
                )
            ))
            render(device, "4-account-removed", card(
                TaskSyncCardState(
                    on = true, chosen = microsoft, lists = lists, newTasksListId = "t-0",
                    status = "Waiting for you", needsReconnect = true, accountGone = true
                )
            ))
            render(device, "5-off-no-todo", card(
                TaskSyncCardState(
                    on = false, chosen = microsoft, choices = listOf(google, microsoft),
                    status = "This Microsoft account has no Microsoft To Do. At work or school, your admin may have turned it off."
                )
            ))
            render(device, "6-off-consent-cancelled", card(
                TaskSyncCardState(
                    on = false, chosen = microsoft, choices = listOf(microsoft),
                    status = "Microsoft To Do wasn't allowed, so Task Sync stays off."
                )
            ))
        }
    }
}

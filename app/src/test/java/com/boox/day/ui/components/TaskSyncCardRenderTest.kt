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
 * The Task Sync card draft (plan step 2.6) at the Preferences card width and density of the Go 10.3
 * and the Go 6, saved to app/build/task-sync-previews/ for the owner's review.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w1500dp-h3000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TaskSyncCardRenderTest {

    private data class Device(val name: String, val cardWidthDp: Int, val density: Float)

    // Preferences: 85 % of 773 dp on the Go 10.3, 94 % of 572 dp on the Go 6, less the dialog's padding
    private val devices = listOf(Device("go10.3", 630, 385 / 160f), Device("go6", 510, 300 / 160f))

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
        val out = File("build/task-sync-previews").apply { mkdirs() }
        File(out, "${device.name}-$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun card(state: TaskSyncCardState): @Composable () -> Unit = {
        SettingCard(
            title = "Task Sync",
            description = "Keep your tasks in step with Google Tasks on your phone. Off until you turn it on."
        ) { TaskSyncCardContent(state) }
    }

    // The owner's own Google Tasks lists (30 Oct 2026), plus the test list
    private val lists = listOf("My Tasks", "Tasks", "Personal", "Work", "Husband", "Church", "Dad", "Professional", "BooxDay test")
        .mapIndexed { i, title -> TaskSyncCardList("l-$i", title, ticked = title in setOf("My Tasks", "Work", "Church", "BooxDay test")) }

    // Many lists with long names: they wrap within their column
    private val manyLists = (lists.map { it.title } + listOf("Kids' school forms and trips", "House repairs", "Garden"))
        .mapIndexed { i, title -> TaskSyncCardList("m-$i", title, ticked = i % 3 == 0) }

    @Test
    fun theCardOnBothTablets() {
        for (device in devices) {
            render(device, "1-off", card(TaskSyncCardState(on = false)))
            render(device, "1b-off-two-accounts", card(
                TaskSyncCardState(on = false, account = "ortolano@gmail.com", accounts = listOf("ortolano@gmail.com", "work@example.com"))
            ))
            render(device, "2-off-no-account", card(
                TaskSyncCardState(on = false, cantTurnOn = "Connect a Google account first: Settings → Calendar Accounts.")
            ))
            render(device, "3-on", card(
                TaskSyncCardState(
                    on = true, account = "ortolano@gmail.com", lists = lists, newTasksListId = "l-8",
                    status = "Last synced today at 10:42"
                )
            ))
            render(device, "4-on-needs-you", card(
                TaskSyncCardState(
                    on = true, account = "ortolano@gmail.com", lists = lists, newTasksListId = "l-8",
                    status = "Waiting for you", needsReconnect = true, deletesWaiting = 25,
                    fullDays = listOf("Mon 3 Nov"), keptChangedHere = 2
                )
            ))
            render(device, "5-twelve-lists", card(
                TaskSyncCardState(
                    on = true, account = "ortolano@gmail.com", lists = manyLists, newTasksListId = "m-9",
                    status = "Last synced today at 10:42"
                )
            ))
            render(device, "6-new-tasks-to-open") {
                Box(Modifier.background(Color.White).border(1.dp, Color.Black).width(260.dp)) {
                    NewTasksToChoices(lists.filter { it.ticked }, "l-8") {}
                }
            }
        }
    }
}

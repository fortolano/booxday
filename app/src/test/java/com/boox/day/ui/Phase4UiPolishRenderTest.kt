package com.boox.day.ui

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.boox.day.data.db.Task
import com.boox.day.editor.ui.ChildTaskDialogContent
import com.boox.day.editor.ui.ConfirmationDialogContent
import com.boox.day.editor.ui.LassoSelectionBar
import com.boox.day.editor.ui.TasksControlColumn
import com.boox.day.editor.ui.toolbar.UnifiedAiMenuItems
import com.boox.day.ui.attention.AttentionItem
import com.boox.day.ui.attention.SettingsAttention
import com.boox.day.ui.attention.SettingsPlace
import com.boox.day.ui.calendar.SettingsMenuContent
import com.boox.day.ui.dialogs.ClearSyncedDataDialogContent
import com.boox.day.ui.theme.EInkTheme
import com.boox.day.ui.views.EditorTab
import com.boox.day.ui.views.WelcomeViewContent
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
 * Multi-device E-Ink render verification for Phase 4 UI/UX Polish (Decision D3):
 * Renders LassoSelectionBar (P1-B), UnifiedAiMenuItems (P3-B), SettingsMenuContent (P4-A),
 * WelcomeViewContent (P5-A), Dialog Rulebook dialogs (P6), and TasksControlColumn (P7-A)
 * across all 4 BOOX screen profiles × fontScale 1.0 & 1.15 into build/phase4-previews/.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w2600dp-h3400dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Phase4UiPolishRenderTest {

    private data class Device(val name: String, val width: Int, val height: Int, val density: Float)

    private val devices = listOf(
        Device("go6", 572, 772, 300 / 160f),
        Device("go7", 674, 896, 300 / 160f),
        Device("go10", 773, 1031, 385 / 160f),
        Device("max13", 997, 1330, 385 / 160f)
    )

    private val scales = listOf(1.0f, 1.15f)

    @Test
    fun lassoSelectionBarFitsAllDevicesAndScales() {
        for (device in devices) for (scale in scales) {
            val availableDp = (device.width - 16).dp
            val compactTwoRow = availableDp < 470.dp || (availableDp < 540.dp && scale > 1.1f)
            val bounds = renderScreen(device, "lasso", scale) { measure ->
                LassoSelectionBar(
                    onCut = {},
                    onCopy = {},
                    onDuplicate = {},
                    onDelete = {},
                    onRead = {},
                    onConvertToTasks = {},
                    onSchedule = {},
                    compactTwoRow = compactTwoRow,
                    modifier = measure
                )
            }
            val widthDp = bounds.width / device.density
            val heightDp = bounds.height / device.density
            assertTrue(
                "Lasso bar width ${widthDp}dp exceeds ${device.name} (${device.width - 16}dp) at scale=$scale",
                widthDp <= device.width - 16f
            )
            assertTrue(
                "Lasso bar height ${heightDp}dp must be >= 44dp touch target",
                heightDp >= 44f
            )
        }
    }

    @Test
    fun unifiedAiMenuFitsAllDevicesAndScales() {
        val cases = listOf(
            Triple("memos-on", EditorTab.Memos, true),
            Triple("memos-off", EditorTab.Memos, false),
            Triple("tasks-on", EditorTab.Tasks, true),
            Triple("tasks-off", EditorTab.Tasks, false)
        )
        for (device in listOf(devices[0], devices[2])) for ((label, tab, aiOn) in cases) for (scale in scales) {
            val bounds = renderScreen(device, "ai-menu-$label", scale) { measure ->
                Column(
                    modifier = measure
                        .width(EInkTheme.PopupMenu.settingsWidth)
                        .background(EInkTheme.PopupMenu.background)
                        .border(EInkTheme.PopupMenu.borderWidth, EInkTheme.PopupMenu.borderColor)
                ) {
                    UnifiedAiMenuItems(
                        tab = tab,
                        aiEnabled = aiOn,
                        hasSelectedStrokes = false,
                        hasPageContent = true,
                        hasTextAnnotations = false,
                        pageId = null,
                        onDone = {}
                    )
                }
            }
            val heightDp = bounds.height / device.density
            assertTrue(
                "AI menu $label height ${heightDp}dp exceeds ${device.name} (${device.height}dp) at scale=$scale",
                heightDp <= device.height * 0.90f
            )
        }
    }

    @Test
    fun settingsMenuFitsWithTaskSyncAndMergedBooxNotes() {
        val attention = SettingsAttention(
            listOf(
                AttentionItem(SettingsPlace.TASK_SYNC, "Task Sync needs you to sign in again"),
                AttentionItem(SettingsPlace.BOOX_NOTES, "Boox Notes lost its file access")
            )
        )
        for (device in listOf(devices[0], devices[2])) for (scale in scales) {
            val bounds = renderScreen(device, "settings-menu", scale) { measure ->
                SettingsMenuContent(
                    onPreferences = {},
                    onCalendarSettings = {},
                    onCalendarAccounts = {},
                    onAiSettings = {},
                    onVoiceSettings = {},
                    onDataBackup = {},
                    onBooxNotesSettings = {},
                    onHelp = {},
                    onAbout = {},
                    onClose = {},
                    attention = attention,
                    taskStreak = if (device.name == "go6") 5 else null,
                    modifier = measure
                )
            }
            val heightDp = bounds.height / device.density
            assertTrue(
                "Settings menu height ${heightDp}dp exceeds ${device.name} (${device.height}dp) at scale=$scale",
                heightDp <= device.height * 0.92f
            )
        }
    }

    @Test
    fun rebuiltRulebookDialogsFitAllDevicesAndScales() {
        for (device in devices) for (scale in scales) {
            val childBounds = renderScreen(device, "dialog-subtask", scale) { measure ->
                ChildTaskDialogContent(
                    onDelete = {},
                    onEdit = {},
                    onCopy = {},
                    onDismiss = {},
                    modifier = measure
                )
            }
            assertTrue("ChildTaskDialog width fits ${device.name}", childBounds.width / device.density <= device.width * 0.96f)

            val confirmBounds = renderScreen(device, "dialog-delete-series", scale) { measure ->
                ConfirmationDialogContent(
                    title = "Delete Series",
                    message = "Stop future repeats and keep current tasks as one-off items?",
                    confirmText = "Delete Series",
                    isDestructive = true,
                    onConfirm = {},
                    onDismiss = {},
                    modifier = measure
                )
            }
            assertTrue("ConfirmationDialog width fits ${device.name}", confirmBounds.width / device.density <= device.width * 0.96f)

            val clearBounds = renderScreen(device, "dialog-clear-synced", scale) { measure ->
                ClearSyncedDataDialogContent(
                    onDismiss = {},
                    onClearOnly = {},
                    onClearAndResync = {},
                    modifier = measure.width(minOf(460.dp, (device.width * 0.9f).dp))
                )
            }
            assertTrue("ClearSyncedDataDialog fits ${device.name}", clearBounds.height / device.density <= device.height * 0.85f)
        }
    }

    @Test
    fun welcomeViewAndTasksControlsRenderCleanly() {
        val sampleTasks = List(5) { idx ->
            Task(
                id = "t-$idx",
                dayDate = "2026-11-25",
                taskPageIndex = 0,
                pageId = "p-1",
                rowIndex = idx,
                isCompleted = idx == 0,
                priority = listOf("A", "B", "C", "", "A")[idx],
                dueDay = when (idx) {
                    1 -> "2020-01-01" // overdue -> monochrome "!" badge
                    2 -> "2099-12-31" // future due date
                    else -> null
                },
                typedText = "Task ${idx + 1}"
            )
        }

        for (device in listOf(devices[0], devices[2])) for (scale in scales) {
            val welcomeBounds = renderScreen(device, "welcome-view", scale) { measure ->
                WelcomeViewContent(
                    onGetStarted = {},
                    modifier = measure.size(device.width.dp, device.height.dp)
                )
            }
            assertTrue(welcomeBounds.height > 0f)

            val colBounds = renderScreen(device, "tasks-controls", scale) { measure ->
                Box(
                    modifier = measure
                        .height(300.dp)
                        .background(Color.White)
                ) {
                    TasksControlColumn(
                        tasks = sampleTasks,
                        taskHasContent = { true },
                        onCheckboxToggle = {},
                        onCheckboxLongPress = {},
                        onPriorityChange = { _, _ -> },
                        onDueDateTap = {},
                        onChevronTap = {},
                        onChevronLongPress = {},
                        onLongPressRow = {}
                    )
                }
            }
            assertTrue(colBounds.width > 0f && colBounds.height > 0f)
        }
    }

    private fun renderScreen(
        device: Device,
        name: String,
        fontScale: Float,
        content: @Composable (Modifier) -> Unit
    ): Rect {
        var subject = Rect.Zero
        var screen = Rect.Zero
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        var image: Bitmap? = null
        var crop: Bitmap? = null
        try {
            val activity = controller.get()
            activity.setContent {
                CompositionLocalProvider(LocalDensity provides Density(device.density, fontScale)) {
                    Box(
                        Modifier
                            .size(device.width.dp, device.height.dp)
                            .background(Color.White)
                            .onGloballyPositioned { screen = it.boundsInRoot() },
                        contentAlignment = Alignment.Center
                    ) {
                        content(Modifier.onGloballyPositioned { subject = it.boundsInRoot() })
                    }
                }
            }
            shadowOf(Looper.getMainLooper()).idle()
            val root = activity.findViewById<View>(android.R.id.content)
            image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(image))
            crop = Bitmap.createBitmap(
                image,
                screen.left.toInt().coerceAtLeast(0),
                screen.top.toInt().coerceAtLeast(0),
                screen.width.toInt().coerceAtMost(image.width),
                screen.height.toInt().coerceAtMost(image.height)
            )
            val out = File("build/phase4-previews").apply { mkdirs() }
            File(out, "${device.name}-$name-$fontScale.png").outputStream().use {
                crop.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            crop?.recycle()
            image?.recycle()
            runCatching { controller.pause().stop().destroy() }
        }
        return subject
    }
}

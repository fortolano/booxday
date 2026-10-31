package com.boox.day.editor.ui

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.boox.day.data.db.RecurringTaskTemplate
import com.boox.day.data.db.Task
import com.boox.day.ui.theme.EInkTheme
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * The task card (31 Oct 2026, option B) drawn at each tablet's real size and density, every screen
 * saved to app/build/task-card-previews/ for review. Every screen must fit the popup height without
 * scrolling (the old Due Date dialog ran off the Go 6 screen). One class per tablet: the screen size
 * is read once per test process (LayoutProfiles), so run them in separate Gradle runs.
 */
abstract class TaskCardRenderBase(private val device: String) {

    private val today = LocalDate.of(2026, 10, 31)
    private val typed = Task(
        dayDate = "2026-10-31", taskPageIndex = 0, rowIndex = 2, pageId = "p",
        priority = "B", typedText = "Call the dentist about Tuesday", dueDay = "2026-11-03"
    )
    private val handwritten = Task(
        dayDate = "2026-10-31", taskPageIndex = 0, rowIndex = 4, pageId = "p",
        recognizedText = "Buy bread and milk", dueDay = "2026-11-01", reminderMinuteOfDay = 7 * 60 + 30
    )
    private val series = RecurringTaskTemplate(
        title = "Call the dentist about Tuesday", recurrenceType = "CUSTOM_WEEKDAYS", daysOfWeek = "[1,3,5]", anchorDate = "2026-10-31"
    )
    private val subtasks = listOf(
        TaskCardSubtask("Find the insurance card", false),
        TaskCardSubtask("Look up the office number", true),
        TaskCardSubtask("Call before noon", false)
    )
    private val actions = TaskCardActions(
        onEditText = {}, onConvertToText = {}, onPriorityChange = {}, onSaveDue = {}, onClearDue = {}, onShowDay = {},
        onReminderChange = {}, onSaveRepeat = {}, onPauseRepeat = {}, onStopRepeat = {}, onBreakDown = {},
        onDelete = {}, onMove = {}, onDismiss = {}
    )

    private fun render(
        name: String, data: TaskCardData, screen: TaskCardScreen, titleText: String?, recognizedText: String?,
        actions: TaskCardActions = this.actions
    ) {
        val bounds = mutableMapOf<String, Rect>()
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        activity.setContent {
            Column(Modifier.fillMaxWidth().background(Color.White), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.padding(16.dp).onGloballyPositioned { bounds["card"] = it.boundsInRoot() }) {
                    TaskCardContent(data, titleText, recognizedText, 9 * 60, screen, {}, actions, today = today)
                }
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        val root = activity.findViewById<View>(android.R.id.content)
        val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(image))
        val r = bounds.getValue("card")
        val crop = Bitmap.createBitmap(image, r.left.toInt(), r.top.toInt(), r.width.toInt(), r.height.toInt())
        val out = File("build/task-card-previews").apply { mkdirs() }
        File(out, "$device-$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val density = activity.resources.displayMetrics.density
        val heightDp = r.height / density
        controller.pause().stop().destroy()
        // Content at the popup's height limit would be scrolling: the buttons must show without it
        assertTrue("$device $name is $heightDp dp, popup limit ${EInkTheme.PopupOverlay.maxHeight}",
            heightDp < EInkTheme.PopupOverlay.maxHeight.value - 1f)
    }

    @Test
    fun everyScreenFitsWithoutScrolling() {
        val typedCard = TaskCardData(typed, null, null, subtasks, null)
        val seriesCard = TaskCardData(typed.copy(templateId = series.id), series, null, emptyList(), null)
        val handCard = TaskCardData(handwritten, null, null, emptyList(), null)
        val title = typed.typedText
        render("01-main", typedCard, TaskCardScreen.Main, title, null)
        render("02-main-handwritten", handCard, TaskCardScreen.Main, null, handwritten.recognizedText)
        render("03-text", handCard, TaskCardScreen.Text, null, handwritten.recognizedText)
        render("04-due", typedCard, TaskCardScreen.Due, title, null)
        render("05-due-none", TaskCardData(typed.copy(dueDay = null), null, null, emptyList(), null), TaskCardScreen.Due, title, null)
        render("06-reminder", handCard, TaskCardScreen.Reminder, null, handwritten.recognizedText)
        render("07-reminder-time", handCard, TaskCardScreen.ReminderTime, null, handwritten.recognizedText)
        render("08-repeat-new", typedCard.copy(subtasks = emptyList()), TaskCardScreen.Repeat, title, null)
        render("09-repeat-series", seriesCard, TaskCardScreen.Repeat, title, null)
        render("10-repeat-start", seriesCard, TaskCardScreen.RepeatStart, title, null)
        render("11-repeat-end", seriesCard, TaskCardScreen.RepeatEnd, title, null)
        render("12-subtasks", typedCard, TaskCardScreen.Subtasks, title, null)
        render("13-main-subtask", TaskCardData(
            typed.copy(parentTaskId = "parent", typedText = "Find the insurance card", priority = "", dueDay = "2026-10-29"),
            null, "Subtasks cannot repeat on their own yet.", emptyList(), "Call the dentist about Tuesday"
        ), TaskCardScreen.Main, "Find the insurance card", null,
            // As MainScreen passes them for a subtask: no Break Down, so no Subtasks line
            TaskCardActions(
                onEditText = {}, onConvertToText = null, onPriorityChange = {}, onSaveDue = {}, onClearDue = {}, onShowDay = {},
                onReminderChange = {}, onSaveRepeat = {}, onPauseRepeat = null, onStopRepeat = null, onBreakDown = null,
                onDelete = {}, onMove = {}, onDismiss = {}, moveLabel = "Copy"
            )
        )
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w773dp-h1031dp-385dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TaskCardRenderGo103Test : TaskCardRenderBase("go10.3")

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w572dp-h772dp-300dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TaskCardRenderGo6Test : TaskCardRenderBase("go6")

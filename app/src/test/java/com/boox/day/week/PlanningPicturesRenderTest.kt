package com.boox.day.week

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.boox.day.proposals.ProposalCardUi
import com.boox.day.proposals.ProposalNothingCard
import com.boox.day.proposals.ProposalReadingCard
import com.boox.day.proposals.ProposalReviewCard
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

/** Pictures of Week view and Write It, Schedule It at every tablet size, made-up data (Nov 2026). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w2600dp-h3400dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlanningPicturesRenderTest {

    private data class Device(val name: String, val width: Int, val height: Int, val density: Float, val sheet: Int)

    private val devices = listOf(
        Device("go6", 572, 772, 300 / 160f, 515),
        Device("go7", 674, 896, 300 / 160f, 553),
        Device("go10", 773, 1031, 385 / 160f, 634),
        Device("max13", 997, 1330, 385 / 160f, 700)
    )

    private val ink by lazy {
        Bitmap.createBitmap(420, 56, Bitmap.Config.ARGB_8888).apply {
            val paint = Paint().apply { color = android.graphics.Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true }
            val path = Path().apply {
                moveTo(4f, 34f)
                var x = 4f
                var up = true
                while (x < 300f) {
                    quadTo(x + 9f, if (up) 4f else 52f, x + 18f, 30f)
                    x += 18f
                    up = !up
                }
            }
            Canvas(this).drawPath(path, paint)
        }.asImageBitmap()
    }

    private fun busyWeek(today: Boolean = true) = WeekPageData(
        rangeLong = "Nov 2 – 8, 2026 · Week 45",
        rangeShort = "Nov 2 – 8",
        isThisWeek = today,
        cells = listOf(
            WeekCell(
                "Mon 2", doneCount = 2, lines = listOf(
                    WeekLine("Team sync", minute = 9 * 60),
                    WeekLine("Lunch with Sam at the new Italian place", minute = 12 * 60 + 30),
                    WeekLine("Call Anna about the quarterly budget", mark = WeekMark.DUE),
                    WeekLine("Water plants", mark = WeekMark.REPEATS)
                )
            ),
            WeekCell(
                "Tue 3", isToday = today, isSelected = today, lines = listOf(
                    WeekLine("School holiday", allDay = true),
                    WeekLine("Dentist", minute = 8 * 60 + 15),
                    WeekLine("Design review with the whole product team", minute = 10 * 60),
                    WeekLine("Budget planning", minute = 13 * 60),
                    WeekLine("Client call", minute = 15 * 60 + 30),
                    WeekLine("Send report to finance before the end of day"),
                    WeekLine("", ink = ink),
                    WeekLine("Book train tickets"),
                    WeekLine("Order printer paper"),
                    WeekLine("Reply to Maria"),
                    WeekLine("Tidy the shared folder"),
                    WeekLine("Plan the offsite")
                )
            ),
            WeekCell("Wed 4", lines = listOf(WeekLine("Quarterly planning workshop, day one", minute = 9 * 60 + 30), WeekLine("", ink = ink))),
            WeekCell("Thu 5", doneCount = 1, lines = listOf(WeekLine("Water plants", mark = WeekMark.REPEATS))),
            WeekCell(
                "Fri 6", lines = listOf(
                    WeekLine("Conference", allDay = true),
                    WeekLine("Keynote: the future of paper notebooks", minute = 9 * 60),
                    WeekLine("Pay the car insurance", mark = WeekMark.DUE)
                )
            ),
            WeekCell("Sat 7", lines = listOf(WeekLine("Farmers market", minute = 8 * 60), WeekLine("Soccer game", minute = 14 * 60))),
            WeekCell("Sun 8"),
            WeekCell(
                "Overdue", count = 3, lines = listOf(
                    WeekLine("Renew library card"),
                    WeekLine("Fix the garden gate"),
                    WeekLine("Send thank-you note to the Hendersons")
                )
            )
        )
    )

    private val daySheetRows = listOf(
        WeekSheetRow("Dentist", time = "8:15 – 9:00 AM"),
        WeekSheetRow("Design review with the team", time = "10:00 – 11:00 AM"),
        WeekSheetRow("Send report to finance"),
        WeekSheetRow("Call Anna", ticked = true),
        WeekSheetRow("Book train tickets", note = "Due"),
        WeekSheetRow("Order printer paper")
    )

    private val overdueRows = listOf(
        WeekSheetRow("Renew library card", note = "Oct 26"),
        WeekSheetRow("Fix the garden gate", note = "Oct 29"),
        WeekSheetRow("Thank-you note to the Hendersons", note = "Nov 1")
    )

    @Test
    fun weekPages() {
        for (d in devices) {
            val spec = WeekLayoutSpec.of(d.width, d.height)
            assertTrue("${d.name}: ${spec.linesPerDay} lines", spec.linesPerDay >= 5)
            render("week-${d.name}", d) { WeekPage(busyWeek(), spec) }
            render("week-${d.name}-other", d) { WeekPage(busyWeek(today = false), spec) }
        }
    }

    /** D7: the Go 6 as seven stacked rows, to compare with the grid. */
    @Test
    fun go6OptionA() {
        val d = devices.first()
        val spec = WeekLayoutSpec.of(d.width, d.height).copy(linesPerDay = 3)
        val page = busyWeek()
        render("week-go6-option-a", d) {
            Column(Modifier.fillMaxSize().background(Color.White)) {
                Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("‹  Nov 2 – 8  ›", fontSize = EInkTheme.Typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                page.cells.forEach { cell ->
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black))
                    Row(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(cell.title, fontSize = EInkTheme.Typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.width(78.dp))
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            val fits = cell.lines.size <= 3
                            (if (fits) cell.lines else cell.lines.take(2)).forEach { WeekLineView(it, spec, false) }
                            if (!fits) Text("+${cell.lines.size - 2} more", fontSize = EInkTheme.Typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun daySheets() {
        for (d in devices) {
            render("sheet-${d.name}", d, dimmed = true) { WeekDaySheetCard("Tuesday, Nov 3", daySheetRows, 1, 2) }
            render("overdue-${d.name}", d, dimmed = true) { WeekDaySheetCard("Overdue", overdueRows, 1, 1, showDayButtons = false) }
        }
    }

    @Test
    fun writeItScheduleIt() {
        val event = ProposalCardUi("Event", "Lunch with Sam", "Tue, Nov 10 · 1:00 – 2:00 PM", "Work (Google)", "Overlaps Team sync")
        val task = ProposalCardUi("Task", "Pay the car insurance", "Fri, Nov 13 · reminder 9:00 AM")
        val checkDate = ProposalCardUi("Event", "Dentist", "Thu, Nov 12 · 3:00 – 4:00 PM", "Personal (iCloud)", check = "Check the date: you wrote Wednesday")
        for (d in devices) {
            val perPage = if (d.name == "go6") 2 else if (d.name == "go7") 3 else 4
            val all = listOf(event, task, checkDate, ProposalCardUi("Event", "Team dinner", "Fri, Nov 13 · 7:00 – 8:00 PM", "Work (Google)", ticked = false))
            val shown = all.take(perPage)
            val w = Modifier.width(d.sheet.dp)
            render("write-${d.name}-one", d, dimmed = true) { ProposalReviewCard("Lunch with Sam Tue 1pm", listOf(event), 1, 1, "Add Event", w) }
            render("write-${d.name}-many", d, dimmed = true) {
                ProposalReviewCard("Lunch with Sam Tue 1pm, pay car insurance Friday, dentist Wed 3", shown, 1, if (shown.size < all.size) 2 else 1, "Add 3", w, showMoreDetails = false)
            }
            render("write-${d.name}-reading", d, dimmed = true) { ProposalReadingCard(w) }
            render("write-${d.name}-nodate", d, dimmed = true) { ProposalNothingCard("No date or time found.", w) }
            render("write-${d.name}-offline", d, dimmed = true) { ProposalNothingCard("BooxDay needs a connection to read this. Your notes are safe.", w) }
        }
    }

    private fun render(name: String, d: Device, dimmed: Boolean = false, content: @Composable () -> Unit) {
        var screen = Rect.Zero
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            CompositionLocalProvider(LocalDensity provides Density(d.density, 1f)) {
                Box(
                    Modifier.size(d.width.dp, d.height.dp).background(if (dimmed) Color(0xFFE8E8E8) else Color.White).onGloballyPositioned { screen = it.boundsInRoot() },
                    contentAlignment = Alignment.Center
                ) { content() }
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        val root = activity.findViewById<View>(android.R.id.content)
        val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(image))
        val crop = Bitmap.createBitmap(image, screen.left.toInt(), screen.top.toInt(), screen.width.toInt(), screen.height.toInt())
        val out = File("build/planning-previews").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

package com.boox.day.meetings

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Looper
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.asImageBitmap
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

/** Pictures of Meeting Recap, Meeting Prep and Share to BooxDay at every tablet size, made-up data (Nov 2026). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w2600dp-h3400dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MeetingPicturesRenderTest {
    private data class Device(val name: String, val width: Int, val height: Int, val density: Float, val panel: Int, val sheet: Int)

    private val devices = listOf(
        Device("go6", 572, 772, 300 / 160f, 538, 515),
        Device("go7", 674, 896, 300 / 160f, 573, 553),
        Device("go10", 773, 1031, 385 / 160f, 657, 634),
        Device("max13", 997, 1330, 385 / 160f, 700, 700)
    )

    private val summary = "The team agreed the 2027 budget with one change: travel is cut by 10%. The launch moves to March so that the new pricing can be tested first."
    private val decisions = listOf("Move the launch to March", "Cut travel by 10%")
    private val actions = listOf(
        RecapActionUi("Send budget to Anna", "Fri, Nov 6"),
        RecapActionUi("Book the room for the review"),
        RecapActionUi("Draft the pricing test", "Nov 20", ticked = false)
    )
    private val followUps = listOf("Ask Tom about the contract")
    private val questions = listOf("Who signs off the pricing?")
    private val when3 = "Tue, Nov 3 · 9:00 AM · from 3 pages"

    private val prep = PrepUi(
        title = "Team sync",
        line = "Tue, Nov 10 · 9:00 – 10:00 AM · Room 4",
        people = "With Anna, Tom and 3 others",
        lastTime = "Nov 3",
        lastPoints = listOf("The team agreed the 2027 budget", "Move the launch to March"),
        tasks = listOf("Send budget to Anna", "Draft the pricing test"),
        notes = listOf("Budget 2027 (BOOX note)")
    )

    @Test
    fun recap() {
        for (d in devices) {
            val w = Modifier.width(d.panel.dp)
            if (d.name == "go6") {
                render("recap-go6-1", d) { MeetingRecapCard("Team sync", when3, RecapPageUi(summary, decisions), 1, 2, w) }
                render("recap-go6-2", d) { MeetingRecapCard("Team sync", when3, RecapPageUi(actions = actions, followUps = followUps, questions = questions), 2, 2, w) }
            } else {
                render("recap-${d.name}", d) { MeetingRecapCard("Team sync", when3, RecapPageUi(summary, decisions, actions, followUps, questions), 1, 1, w) }
            }
            render("recap-${d.name}-changed", d) { MeetingRecapCard("Team sync", when3, RecapPageUi(summary, decisions), 1, 2, w, changedNotes = true, saved = true) }
            render("recap-${d.name}-reading", d) { MeetingReadingCard("Recap · Team sync", "Your AI is reading 3 pages of notes…", w) }
            render("recap-${d.name}-off", d) { MeetingRecapBlockedCard("Team sync", "Recap needs your AI, and AI is off in Settings. Your notes are safe, and Action Items and Export work without it.", w) }
        }
    }

    @Test
    fun cards() {
        for (d in listOf(devices[0], devices[2])) {
            val w = Modifier.width(d.panel.dp)
            render("card-${d.name}-none", d) { MeetingCardActions(hasNotes = false, beforeEnd = true, modifier = w) }
            render("card-${d.name}-notes", d) { MeetingCardActions(hasNotes = true, beforeEnd = false, modifier = w) }
            render("card-${d.name}-both", d) { MeetingCardActions(hasNotes = true, beforeEnd = true, modifier = w) }
        }
    }

    @Test
    fun prepare() {
        for (d in devices) {
            val w = Modifier.width(d.panel.dp)
            render("prep-${d.name}", d) { MeetingPrepCard(prep, 1, 1, w) }
            render("prep-${d.name}-empty", d) {
                MeetingPrepCard(PrepUi("Kickoff with Priya", "Thu, Nov 12 · 2:00 – 3:00 PM"), 1, 1, w)
            }
            render("prep-${d.name}-brief", d) {
                MeetingPrepCard(
                    prep.copy(
                        brief = listOf("Budget was agreed last time, with travel cut by 10%", "The launch moved to March", "Anna is still waiting for the budget"),
                        worthAsking = listOf("Who signs off the pricing?")
                    ),
                    1, 1, w
                )
            }
        }
    }

    @Test
    fun share() {
        val text = "Dinner at Sofia's on Friday at 7. Bring the photos from the trip, and tell Sam about the Saturday hike. The address is 1200 Ocean Road, near the market."
        for (d in devices) {
            val w = Modifier.width(d.sheet.dp)
            render("share-${d.name}", d) { ShareCard(text, w) }
            render("share-${d.name}-short", d) { ShareCard(text, w, findLabel = "Find Dates") }
            render("share-${d.name}-image", d) {
                ShareCard(
                    "", w,
                    picture = {
                        Image(bitmap = sample().asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth().height(180.dp))
                    }
                )
            }
        }
    }

    private fun sample() = Bitmap.createBitmap(400, 220, Bitmap.Config.ARGB_8888).apply {
        val canvas = Canvas(this)
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = Paint().apply { color = android.graphics.Color.BLACK; isAntiAlias = true }
        canvas.drawCircle(110f, 80f, 40f, paint)
        canvas.drawRect(30f, 170f, 370f, 190f, paint)
    }

    /** A4 at 150 dpi: the meeting PDF's first page and a notes page. */
    @Test
    fun meetingPdf() {
        val width = 1240
        val height = 1754
        val margin = 110f
        val page = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(page)
        canvas.drawColor(android.graphics.Color.WHITE)
        val line = Paint().apply { color = android.graphics.Color.BLACK; strokeWidth = 2f }
        var y = margin
        fun text(value: String, size: Float, bold: Boolean = false, gapAfter: Float = 14f) {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK
                textSize = size
                typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            val layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, (width - 2 * margin).toInt()).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
            canvas.save()
            canvas.translate(margin, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + gapAfter
        }
        text("Team sync", 52f, bold = true, gapAfter = 8f)
        text("Tuesday, November 3, 2026 · 9:00 – 10:00 AM · Room 4", 28f, gapAfter = 26f)
        canvas.drawLine(margin, y, width - margin, y, line)
        y += 30f
        text("Summary", 32f, bold = true)
        text(summary, 28f, gapAfter = 30f)
        text("Decisions", 32f, bold = true)
        decisions.forEach { text("•  $it", 28f, gapAfter = 8f) }
        y += 22f
        text("Action items", 32f, bold = true)
        actions.forEach { text("•  ${it.text}${it.due?.let { due -> " · $due" }.orEmpty()}", 28f, gapAfter = 8f) }
        y += 22f
        text("Follow-ups", 32f, bold = true)
        followUps.forEach { text("•  $it", 28f, gapAfter = 8f) }
        y += 22f
        text("Open questions", 32f, bold = true)
        questions.forEach { text("•  $it", 28f, gapAfter = 8f) }
        val footer = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; textSize = 22f }
        canvas.drawText("Page 1 of 4", width - margin - 120f, height - 70f, footer)
        save("pdf-page1", page)

        val notes = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val notesCanvas = Canvas(notes)
        notesCanvas.drawColor(android.graphics.Color.WHITE)
        val ink = Paint().apply { color = android.graphics.Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true }
        for (row in 0 until 9) {
            val path = Path().apply {
                var x = margin
                val base = margin + 80f + row * 120f
                moveTo(x, base)
                var up = true
                val end = width - margin - (row % 3) * 180f
                while (x < end) {
                    quadTo(x + 16f, if (up) base - 42f else base + 30f, x + 32f, base)
                    x += 32f
                    up = !up
                }
            }
            notesCanvas.drawPath(path, ink)
        }
        notesCanvas.drawText("Page 2 of 4", width - margin - 120f, height - 70f, footer)
        save("pdf-page2", notes)
    }

    private fun save(name: String, bitmap: Bitmap) {
        val out = File("build/meeting-previews").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun render(name: String, d: Device, content: @Composable () -> Unit) {
        var screen = Rect.Zero
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            CompositionLocalProvider(LocalDensity provides Density(d.density, 1f)) {
                Box(
                    Modifier.size(d.width.dp, d.height.dp).background(Color(0xFFE8E8E8)).onGloballyPositioned { screen = it.boundsInRoot() },
                    contentAlignment = Alignment.Center
                ) { Column(Modifier.padding(0.dp)) { content() } }
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        val root = activity.findViewById<View>(android.R.id.content)
        val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(image))
        save(name, Bitmap.createBitmap(image, screen.left.toInt(), screen.top.toInt(), screen.width.toInt(), screen.height.toInt()))
    }
}

package com.boox.day.sleepscreen

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.boox.day.today.DayDigest
import com.boox.day.today.DigestEvent
import com.boox.day.today.DigestTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/**
 * Renders the sleep-screen agenda for sample days at the four BOOX sizes and saves the images to
 * app/build/sleep-agenda-previews/ for review on the PC (Oct 2026). Robolectric native graphics:
 * real text and shapes, as on the tablet. Also checks size, pure black and white, and the margins.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SleepAgendaPreviewTest {

    private val zone = ZoneId.of("America/Toronto")
    private val today = LocalDate.of(2026, 10, 24)
    private val now = today.atTime(11, 15).atZone(zone).toInstant().toEpochMilli()

    private data class Device(val name: String, val width: Int, val height: Int, val ppi: Int)

    private val devices = listOf(
        Device("go6", 1072, 1448, 300),
        Device("go7", 1264, 1680, 300),
        Device("go10.3", 1860, 2480, 300),
        Device("notemax", 2400, 3200, 300),
    )

    @Test
    fun rendersEverySampleOnEveryDevice() {
        val out = File("build/sleep-agenda-previews").apply { mkdirs() }
        val samples = mapOf(
            "busy" to busyDay(),
            "light" to lightDay(),
            "empty" to DayDigest(today, emptyList(), emptyList(), streakDays = 0),
            "overloaded" to overloadedDay(),
        )
        for (device in devices) {
            val sizeClass = SleepSizeClass.of(minOf(device.width, device.height), device.ppi)
            val renderer = SleepAgendaRenderer(SleepAgendaStyle.forClass(sizeClass, device.ppi))
            for ((name, digest) in samples) {
                val memo = if (name == "busy" || name == "overloaded") fakeMemo() else null
                val bitmap = renderer.render(
                    SleepAgendaRenderer.Input(
                        digest = digest, widthPx = device.width, heightPx = device.height, nowMs = now,
                        zone = zone, use24Hour = false, memo = memo, locale = Locale.US
                    )
                )
                assertEquals(device.width, bitmap.width)
                assertEquals(device.height, bitmap.height)
                assertPureBlackAndWhite(bitmap)
                assertMarginsBlank(bitmap, SleepAgendaStyle.forClass(sizeClass, device.ppi))
                File(out, "${device.name}-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    @Test
    fun sizeClassesFollowThePanelsShortSide() {
        assertEquals(SleepSizeClass.COMPACT, SleepSizeClass.of(1072, 300))
        assertEquals(SleepSizeClass.COMPACT, SleepSizeClass.of(1264, 300))
        assertEquals(SleepSizeClass.MEDIUM, SleepSizeClass.of(1860, 300))
        assertEquals(SleepSizeClass.LARGE, SleepSizeClass.of(2400, 300))
    }

    private fun assertPureBlackAndWhite(bitmap: Bitmap) {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue(pixels.all { it == Color.BLACK || it == Color.WHITE })
    }

    /** Nothing drawn in the outer margin (half of it, to allow for text side bearings). */
    private fun assertMarginsBlank(bitmap: Bitmap, style: SleepAgendaStyle) {
        val m = (style.mm(style.marginMm) / 2).toInt()
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if (x < m || y < m || x >= bitmap.width - m || y >= bitmap.height - m) {
                assertEquals("pixel ($x,$y) in the margin", Color.WHITE, bitmap.getPixel(x, y))
            }
        }
    }

    // ── Sample days ───────────────────────────────────────────────────────────────────────────
    private fun at(h: Int, m: Int, day: LocalDate = today) = day.atTime(LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()

    private fun event(id: String, title: String, sh: Int, sm: Int, eh: Int, em: Int, location: String? = null,
                      notes: List<String> = emptyList(), day: LocalDate = today) =
        DigestEvent(id, title, at(sh, sm, day), at(eh, em, day), allDay = false, location = location, booxNoteTitles = notes)

    private fun allDay(id: String, title: String) = DigestEvent(id, title, at(0, 0), null, allDay = true)

    private fun busyDay() = DayDigest(
        date = today,
        events = listOf(
            allDay("a1", "Mum's birthday"),
            allDay("a2", "Library books due"),
            event("e1", "Morning run", 7, 0, 7, 45, "Riverside park"),
            event("e2", "Team stand-up", 9, 30, 9, 45, "Zoom"),
            event("e3", "Design review: sleep screen", 11, 0, 12, 0, "Room 4B", notes = listOf("Review notes")),
            event("e4", "Lunch with Mary", 12, 30, 13, 30, "Café Olé"),
            event("e5", "Dentist", 15, 0, 15, 45, "Dr. Patel, 22 King St"),
            event("e6", "Piano lesson for Sam", 17, 30, 18, 15),
        ),
        tasks = listOf(
            DigestTask("t1", "Send the quarterly report", completed = true, priority = "A"),
            DigestTask("t2", "Book flights for the conference", completed = false, priority = "A"),
            DigestTask("t3", "Compare prices", completed = true, isSubtask = true),
            DigestTask("t4", "Pick seats", completed = false, isSubtask = true),
            DigestTask("t5", "Buy bread", completed = true),
            DigestTask("t6", "Call the plumber about the kitchen tap", completed = false, priority = "B"),
            DigestTask("t7", null, completed = false),
            DigestTask("t8", "Water the plants", completed = false),
            DigestTask("t9", "Pay 50% deposit for the venue", completed = false, priority = "C"),
        ),
        streakDays = 6,
        nextDays = listOf(
            today.plusDays(1) to listOf(event("n1", "Brunch with Alex", 10, 30, 12, 0, day = today.plusDays(1))),
            today.plusDays(2) to listOf(
                event("n2", "Team planning", 9, 0, 10, 0, day = today.plusDays(2)),
                event("n3", "1:1 with Jordan", 14, 0, 14, 30, day = today.plusDays(2)),
                event("n4", "Yoga", 18, 0, 19, 0, day = today.plusDays(2)),
                event("n5", "Call Grandma", 20, 0, 20, 30, day = today.plusDays(2)),
            ),
            today.plusDays(3) to emptyList(),
        ),
    )

    private fun lightDay() = DayDigest(
        date = today,
        events = listOf(event("e1", "Coffee with Priya", 10, 0, 11, 0, "Corner café"), event("e2", "Yoga", 18, 0, 19, 0)),
        tasks = listOf(
            DigestTask("t1", "Return library books", completed = true),
            DigestTask("t2", "Plan next week", completed = false),
        ),
        streakDays = 2,
        nextDays = listOf(
            today.plusDays(1) to listOf(event("n1", "Farmers market", 9, 0, 10, 0, day = today.plusDays(1))),
            today.plusDays(2) to emptyList(),
            today.plusDays(3) to listOf(
                allDay("n2", "Recycling day").copy(startMs = at(0, 0, today.plusDays(3))),
                event("n3", "Haircut", 16, 0, 16, 45, day = today.plusDays(3)),
            ),
        ),
    )

    private fun overloadedDay() = DayDigest(
        date = today,
        events = listOf(allDay("a1", "Conference day 2: International Symposium on E-Ink Interfaces"), allDay("a2", "Holiday"), allDay("a3", "Payday")) +
            (0 until 14).map { i ->
                event("e$i", "Session ${i + 1}: a rather long talk title that will not fit on one line", 8 + i / 2, (i % 2) * 30,
                    8 + i / 2, (i % 2) * 30 + 25, "Hall ${'A' + i % 4}")
            },
        tasks = (0 until 18).map { i ->
            DigestTask("t$i", "Task number ${i + 1} with some words to see how it is cut", completed = i % 5 == 0,
                priority = listOf("", "A", "", "B", "")[i % 5], isSubtask = i % 7 == 3)
        },
        streakDays = 41,
        nextDays = listOf(today.plusDays(1) to emptyList(), today.plusDays(2) to emptyList(), today.plusDays(3) to emptyList()),
    )

    /** A stand-in for a handwritten memo page: wavy lines of "writing". */
    private fun fakeMemo(): Bitmap {
        val bmp = Bitmap.createBitmap(1400, 1800, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 5f; strokeCap = Paint.Cap.ROUND
        }
        val random = java.util.Random(7)
        for (line in 0 until 14) {
            val y = 120f + line * 115f
            var x = 90f
            val end = 1300f - random.nextInt(500)
            while (x < end) {
                val wordW = 60f + random.nextInt(150)
                val path = Path().apply {
                    moveTo(x, y)
                    var px = x
                    while (px < x + wordW) {
                        quadTo(px + 10f, y - 30f - random.nextInt(25), px + 20f, y)
                        px += 20f
                    }
                }
                c.drawPath(path, ink)
                x += wordW + 35f
            }
        }
        return bmp
    }
}

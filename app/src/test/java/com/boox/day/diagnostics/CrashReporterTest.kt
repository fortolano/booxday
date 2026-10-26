package com.boox.day.diagnostics

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The crash report kept for the next start (diagnostics/, Oct 2026): written by the uncaught
 * exception handler, handed on to the previous handler, read back once and cleared; and the
 * GitHub issue built from it stays short enough for a link.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CrashReporterTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val handlerBefore = Thread.getDefaultUncaughtExceptionHandler()

    @Before
    fun setUp() {
        CrashReporter.clear(context)
    }

    @After
    fun tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(handlerBefore)
        CrashReporter.clear(context)
    }

    @Test
    fun aCrashIsSavedAndHandedOnToThePreviousHandler() {
        var handedOn: Throwable? = null
        Thread.setDefaultUncaughtExceptionHandler { _, e -> handedOn = e }
        CrashReporter.install(context)

        val error = IllegalStateException("page missing")
        Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), error)

        assertSame(error, handedOn)
        val report = CrashReporter.pending(context)
        assertNotNull(report)
        assertTrue(report!!.text, report.text.startsWith("Boox Day stopped unexpectedly."))
        assertTrue(report.text.contains("Exception: java.lang.IllegalStateException"))
        assertTrue(report.text.contains("Message: page missing"))
        assertTrue(report.text.contains("Stack trace:"))
        assertTrue(report.text.contains("Pen: not started")) // no canvas in this test
        assertTrue(report.crashedAtMs > 0)

        CrashReporter.clear(context)
        assertNull(CrashReporter.pending(context))
    }

    @Test
    fun reportNamesFirmwareAndPenNearTheTopAndMasksEmails() {
        val text = CrashReporter.buildReport(
            "main",
            IllegalStateException("no calendar for jane.doe@example.com"),
            listOf("10-26 10:13:10.807 I GoogleCalendarProvider: Google sign-in successful: jane.doe@example.com"),
            0L,
            penBackend = "Android pen / EPD"
        )

        assertTrue(text.contains("\nFirmware: "))
        assertTrue(text.contains("\nPen: Android pen / EPD\n"))
        assertTrue(text.indexOf("Pen: ") < text.indexOf("Stack trace:"))
        assertFalse(text.contains("jane.doe@example.com"))
        assertTrue(text.contains("Message: no calendar for j…@example.com"))
        assertTrue(text.contains("Google sign-in successful: j…@example.com"))
        // The shortened GitHub issue keeps the pen line
        assertTrue(CrashReportGate.issueBody(text + "x".repeat(20_000)).contains("Pen: Android pen / EPD"))
    }

    @Test
    fun maskingLeavesEverythingButEmailsAlone() {
        val other = "com.boox.day.editor.PageView@5f3a2b, 10 @ 3pm, user@host"
        assertEquals(other, CrashReporter.maskEmails(other))
        assertEquals("a…@b.co and x…@mail.example.org", CrashReporter.maskEmails("a@b.co and x.y+z@mail.example.org"))
    }

    @Test
    fun reportListsLogLinesAfterTheStack() {
        val text = CrashReporter.buildReport("main", RuntimeException("boom"), listOf("line one", "line two"), 0L)
        assertTrue(text.contains("Thread: main"))
        assertTrue(text.indexOf("Stack trace:") < text.indexOf("Recent app log (2 lines):"))
        assertTrue(text.trimEnd().endsWith("line two"))
    }

    @Test
    fun githubIssueNamesTheExceptionAndCutsALongReport() {
        val short = CrashReporter.buildReport("main", IllegalArgumentException("bad row"), emptyList(), 0L)
        assertEquals("Crash: IllegalArgumentException", CrashReportGate.issueTitle(short))
        assertTrue(CrashReportGate.issueBody(short).contains("bad row"))

        val long = short + "x".repeat(20_000)
        val body = CrashReportGate.issueBody(long)
        assertTrue(body.length < 4_500)
        assertTrue(body.contains("full report is on your clipboard"))
    }
}

package com.boox.day.diagnostics

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
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
        assertTrue(report.crashedAtMs > 0)

        CrashReporter.clear(context)
        assertNull(CrashReporter.pending(context))
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

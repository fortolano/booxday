package com.boox.day.sync.caldav

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.CalDavResource
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

/**
 * Reading a CalDAV calendar (plan step 3.4): only new or changed files are downloaded, files that
 * left the calendar are forgotten, a calendar removed on the server says so. A local https server
 * stands in for iCloud and the others.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CalDavReaderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("America/Toronto")
    private val serverCert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
    private val server = MockWebServer()
    private lateinit var db: AppDatabase
    private lateinit var savedZone: TimeZone

    /** The server's calendar /cal/: path → (change tag, file). */
    private val files = linkedMapOf<String, Pair<String, String>>()
    private val downloadsAsked = mutableListOf<String>()

    @Before
    fun setUp() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO connected_accounts (id, providerId, accountType, email, displayName, isActive, createdAt, lastSyncAt, serverUrl) " +
                "VALUES ('acc-1', 'CALDAV', 'CALDAV_OTHER', 'me', NULL, 1, 1, NULL, 'https://localhost')"
        )
        server.useHttps(HandshakeCertificates.Builder().heldCertificate(serverCert).build().sslSocketFactory(), false)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = request.body.readUtf8()
                return when {
                    request.method != "REPORT" || request.path != "/cal/" -> MockResponse().setResponseCode(404)
                    "calendar-query" in body -> multistatus(files.entries.joinToString("") { (href, file) ->
                        response(href, "<d:getetag>\"${file.first}\"</d:getetag>")
                    })
                    else -> {
                        val asked = Regex("<[^>]*href>([^<]+)</").findAll(body).map { it.groupValues[1] }.toList()
                        downloadsAsked += asked
                        multistatus(asked.mapNotNull { href ->
                            files[href]?.let { (tag, ics) ->
                                response(href, "<d:getetag>\"$tag\"</d:getetag><c:calendar-data>${xml(ics)}</c:calendar-data>")
                            }
                        }.joinToString(""))
                    }
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
        TimeZone.setDefault(savedZone)
    }

    @Test
    fun onlyNewAndChangedFilesAreDownloadedAndRemovedOnesForgotten(): Unit = runBlocking<Unit> {
        files["/cal/a.ics"] = "1" to event("a", "Dentist", "20261102T150000Z")
        files["/cal/b.ics"] = "1" to event("b", "Yoga", "20261103T230000Z", rule = "RRULE:FREQ=WEEKLY;COUNT=3")

        val first = read() as CalDavReader.Outcome.Read
        assertEquals(2, first.downloaded)
        assertEquals(listOf("Dentist", "Yoga", "Yoga", "Yoga"), first.events.sortedBy { it.startTime }.map { it.title })

        // On the server: a changed, b deleted, c added
        files["/cal/a.ics"] = "2" to event("a", "Dentist (moved)", "20261104T150000Z")
        files.remove("/cal/b.ics")
        files["/cal/c.ics"] = "1" to event("c", "Lunch", "20261105T170000Z")
        downloadsAsked.clear()

        val second = read() as CalDavReader.Outcome.Read
        assertEquals(listOf("/cal/a.ics", "/cal/c.ics"), downloadsAsked.sorted())
        assertEquals(1, second.removed)
        assertEquals(listOf("Dentist (moved)" to "2026-11-04", "Lunch" to "2026-11-05"), second.events.sortedBy { it.startTime }.map { it.title to it.dayId })
        val dav = db.calDavDao()
        assertNull("deleted on the server: forgotten", dav.getResource("acc-1", "/cal/b.ics"))
        assertEquals("2", dav.getResource("acc-1", "/cal/a.ics")?.etag)
        assertEquals("c", dav.getResource("acc-1", "/cal/c.ics")?.uid)

        // Nothing changed: one listing request, no download, the same rows
        downloadsAsked.clear()
        val third = read() as CalDavReader.Outcome.Read
        assertTrue(downloadsAsked.isEmpty())
        assertEquals(second.events.map { it.id }.sorted(), third.events.map { it.id }.sorted())
    }

    @Test
    fun aCalendarRemovedOnTheServerIsGoneWithItsFiles(): Unit = runBlocking<Unit> {
        db.calDavDao().upsertResource(CalDavResource("acc-1", "/old/a.ics", "/old/", "1", event("a", "Old", "20261102T150000Z"), "a", 1))

        assertEquals(CalDavReader.Outcome.Gone, read(calendarHref = "/old/"))
        assertTrue(db.calDavDao().getResources("acc-1", "/old/").isEmpty())
    }

    // ── Helpers ──

    private suspend fun read(calendarHref: String = "/cal/") = CalDavReader(db).read(
        CalDavConnection("me", "app-password", CalDavConnection.sha256(serverCert.certificate), OkHttpClient()),
        accountId = "acc-1", calendarHomeUrl = server.url("/").toString(), calendarHref = calendarHref, readOnly = false,
        from = LocalDate.parse("2026-10-01"), to = LocalDate.parse("2026-11-30"), zone = zone
    )

    private fun event(uid: String, title: String, startUtc: String, rule: String? = null) = listOfNotNull(
        "BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//test//EN", "BEGIN:VEVENT", "UID:$uid",
        "DTSTART:$startUtc", "DURATION:PT1H", rule, "SUMMARY:$title", "END:VEVENT", "END:VCALENDAR"
    ).joinToString("\r\n", postfix = "\r\n")

    private fun xml(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun response(href: String, props: String) =
        "<d:response><d:href>$href</d:href><d:propstat><d:prop>$props</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"

    private fun multistatus(responses: String) = MockResponse().setResponseCode(207)
        .addHeader("Content-Type", "application/xml; charset=utf-8")
        .setBody("""<?xml version="1.0" encoding="utf-8"?><d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">$responses</d:multistatus>""")
}

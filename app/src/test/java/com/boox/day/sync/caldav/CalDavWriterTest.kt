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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone

/**
 * Writing CalDAV events from the tablet (plan step 3.5): a file changed on the server meanwhile is
 * read again and the phone's change is kept; a new event is made only where no file is; deleting
 * one day of a series keeps the series; a file already gone counts as deleted. A local https server
 * stands in for iCloud, with real change-tag checks (If-Match, If-None-Match).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CalDavWriterTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("America/Toronto")
    private val serverCert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
    private val server = MockWebServer()
    private lateinit var db: AppDatabase
    private lateinit var savedZone: TimeZone

    /** The server's calendar /cal/: path → (change tag, file). */
    private val files = linkedMapOf<String, Pair<Int, String>>()
    private val writes = mutableListOf<String>()

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
                val path = request.path!!
                val body = request.body.readUtf8()
                val current = files[path]
                val ifMatch = request.getHeader("If-Match")
                if (request.method == "PUT" || request.method == "DELETE") writes += "${request.method} $path"
                return when (request.method) {
                    "PUT" -> when {
                        request.getHeader("If-None-Match") == "*" && current != null -> MockResponse().setResponseCode(412)
                        ifMatch != null && ifMatch != "\"${current?.first}\"" -> MockResponse().setResponseCode(412)
                        else -> {
                            val tag = (current?.first ?: 0) + 1
                            files[path] = tag to body
                            MockResponse().setResponseCode(if (current == null) 201 else 204).addHeader("ETag", "\"$tag\"")
                        }
                    }
                    "DELETE" -> when {
                        current == null -> MockResponse().setResponseCode(404)
                        ifMatch != null && ifMatch != "\"${current.first}\"" -> MockResponse().setResponseCode(412)
                        else -> MockResponse().setResponseCode(204).also { files.remove(path) }
                    }
                    "REPORT" -> {
                        val asked = Regex("<[^>]*href>([^<]+)</").findAll(body).map { it.groupValues[1] }.toList()
                        multistatus(asked.mapNotNull { href ->
                            files[href]?.let { (tag, ics) ->
                                "<d:response><d:href>$href</d:href><d:propstat><d:prop><d:getetag>\"$tag\"</d:getetag>" +
                                    "<c:calendar-data>${xml(ics)}</c:calendar-data></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"
                            }
                        }.joinToString(""))
                    }
                    else -> MockResponse().setResponseCode(405)
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
    fun aPhoneEditMadeDuringTheTabletsSaveIsKept(): Unit = runBlocking<Unit> {
        // The tablet last read version 1 (Clinic A); then the phone moved it to Clinic B (version 2)
        stored("/cal/a.ics", 1, dentist(location = "Clinic A"))
        files["/cal/a.ics"] = 2 to dentist(location = "Clinic B")

        // On the tablet the user renames it; the dialog still shows Clinic A
        val id = CalDavEvents.eventId("acc-1", "/cal/a.ics", null)
        val row = writer().update(connection(), "acc-1", home(), "/cal/", id, fields("Dentist (Ana)", location = "Clinic A"), zone)

        assertEquals(listOf("PUT /cal/a.ics", "PUT /cal/a.ics"), writes)  // refused once (412), then on version 2
        val onServer = files.getValue("/cal/a.ics").second
        assertTrue("the tablet's rename" , "SUMMARY:Dentist (Ana)" in onServer)
        assertTrue("the phone's place stays", "LOCATION:Clinic B" in onServer)
        assertEquals("Dentist (Ana)" to "Clinic B", row.title to row.location)
        assertEquals(id, row.id)
        assertEquals("3" to onServer, db.calDavDao().getResource("acc-1", "/cal/a.ics")!!.let { it.etag to it.ics })
    }

    @Test
    fun aNewEventArrivesADeletedDayKeepsTheSeriesAndAGoneFileCountsAsDeleted(): Unit = runBlocking<Unit> {
        val made = writer().create(connection(), "acc-1", home(), "/cal/", fields("Lunch"), zone)
        val href = files.keys.single()
        assertTrue(href.startsWith("/cal/") && href.endsWith(".ics"))
        assertEquals(CalDavEvents.eventId("acc-1", href, null), made.id)
        assertEquals("1", db.calDavDao().getResource("acc-1", href)?.etag)

        // One day of a weekly series: the file stays, that day leaves
        stored("/cal/yoga.ics", 1, yoga())
        files["/cal/yoga.ics"] = 1 to yoga()
        val days = CalDavEvents.occurrences("acc-1", "/cal/", "/cal/yoga.ics", yoga(), DAY.minusDays(1), DAY.plusDays(30), false, zone)
        writer().delete(connection(), "acc-1", home(), "/cal/", days[1].id)
        assertEquals("PUT /cal/yoga.ics", writes.last())
        val left = CalDavEvents.occurrences("acc-1", "/cal/", "/cal/yoga.ics", files.getValue("/cal/yoga.ics").second, DAY.minusDays(1), DAY.plusDays(30), false, zone)
        assertEquals(listOf(days[0].id, days[2].id), left.map { it.id })

        // Deleted on the server already: done, and forgotten here
        stored("/cal/old.ics", 1, dentist(location = null))
        writer().delete(connection(), "acc-1", home(), "/cal/", CalDavEvents.eventId("acc-1", "/cal/old.ics", null))
        assertNull(db.calDavDao().getResource("acc-1", "/cal/old.ics"))
        assertFalse("/cal/old.ics" in files)
    }

    // ── Helpers ──

    private fun writer() = CalDavWriter(db)

    private fun connection() = CalDavConnection("me", "app-password", CalDavConnection.sha256(serverCert.certificate), OkHttpClient())

    private fun home() = server.url("/").toString()

    private fun stored(href: String, etag: Int, ics: String) =
        db.calDavDao().upsertResource(CalDavResource("acc-1", href, "/cal/", etag.toString(), ics, null, 1))

    private fun fields(title: String, location: String? = null) =
        CalDavEditor.Fields(title, DAY, LocalTime.of(10, 0), LocalTime.of(11, 0), isAllDay = false, location = location, description = null)

    /** 10:00-11:00 Toronto on [DAY], as the tablet shows it. */
    private fun dentist(location: String?) = ics(
        "BEGIN:VEVENT", "UID:a", "DTSTART:20261102T150000Z", "DTEND:20261102T160000Z", "SUMMARY:Dentist",
        location?.let { "LOCATION:$it" }, "END:VEVENT"
    )

    private fun yoga() = ics("BEGIN:VEVENT", "UID:y", "DTSTART:20261102T230000Z", "DURATION:PT1H", "RRULE:FREQ=WEEKLY;COUNT=3", "SUMMARY:Yoga", "END:VEVENT")

    private fun ics(vararg lines: String?) =
        (listOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//test//EN") + lines.filterNotNull() + "END:VCALENDAR").joinToString("\r\n", postfix = "\r\n")

    private fun xml(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun multistatus(responses: String) = MockResponse().setResponseCode(207)
        .addHeader("Content-Type", "application/xml; charset=utf-8")
        .setBody("""<?xml version="1.0" encoding="utf-8"?><d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">$responses</d:multistatus>""")

    private companion object {
        val DAY: LocalDate = LocalDate.parse("2026-11-02")
    }
}

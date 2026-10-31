package com.boox.day.sync.caldav

import android.app.Application
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
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Signing in to a CalDAV server (plan step 3.3): finding the calendars, a wrong password, a server
 * certificate Android doesn't trust and the user's "Trust This Server", https only. A local https
 * server with its own certificate stands in for iCloud, Fastmail or a home server. Robolectric for
 * Android's own XML parser, which dav4jvm uses (the app leaves out xpp3).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CalDavConnectionTest {

    private val serverCert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
    private val server = MockWebServer()
    private var password = "app-password"

    @Before
    fun setUp() {
        server.useHttps(HandshakeCertificates.Builder().heldCertificate(serverCert).build().sslSocketFactory(), false)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val auth = request.getHeader("Authorization")
                if (auth == null || auth != okhttp3.Credentials.basic("me", password)) {
                    return MockResponse().setResponseCode(401).addHeader("WWW-Authenticate", "Basic realm=\"test\"")
                }
                return when (request.path) {
                    "/.well-known/caldav" -> multistatus(
                        """<d:response><d:href>/.well-known/caldav</d:href><d:propstat><d:prop>
                           <d:current-user-principal><d:href>/principals/me/</d:href></d:current-user-principal>
                           </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"""
                    )
                    "/principals/me/" -> multistatus(
                        """<d:response><d:href>/principals/me/</d:href><d:propstat><d:prop>
                           <c:calendar-home-set><d:href>/calendars/me/</d:href></c:calendar-home-set>
                           </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"""
                    )
                    "/calendars/me/" -> multistatus(
                        calendar("/calendars/me/", "", collectionOnly = true) +
                            calendar("/calendars/me/work/", "Work", color = "#3366CCFF", write = true, components = "VEVENT", ctag = "w1") +
                            calendar("/calendars/me/todo/", "Reminders", write = true, components = "VTODO") +
                            calendar("/calendars/me/holidays/", "Holidays", write = false, components = "VEVENT")
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun findsTheEventCalendarsBehindTheWellKnownAddress(): Unit = runBlocking<Unit> {
        val found = connection(pin = CalDavConnection.sha256(serverCert.certificate)).discover(address())

        assertEquals(server.url("/principals/me/").toString(), found.principalUrl)
        assertEquals(listOf("Work", "Holidays"), found.calendars.map { it.name })  // tasks-only left out
        val work = found.calendars.first()
        assertEquals("/calendars/me/work/", work.href)
        assertEquals("#3366CC", work.colorHex)
        assertEquals("w1", work.ctag)
        assertTrue("no write privilege: read-only", found.calendars.last().readOnly)
    }

    @Test
    fun aWrongPasswordSaysSo(): Unit = runBlocking<Unit> {
        password = "the-real-one"
        expect<CalDavConnection.Failure.WrongPassword> { connection(pin = CalDavConnection.sha256(serverCert.certificate)).discover(address()) }
    }

    @Test
    fun anUntrustedCertificateIsShownAndOnlyThatOneCanBeTrusted(): Unit = runBlocking<Unit> {
        // Android doesn't trust a home server's own certificate: its fingerprint is shown
        val failure = expect<CalDavConnection.Failure.UntrustedCertificate> { connection(pin = null).discover(address()) }
        assertEquals(CalDavConnection.sha256(serverCert.certificate), failure.sha256)

        // Trusted by the user ("Trust This Server"): this one works, another certificate doesn't
        assertEquals(2, connection(pin = failure.sha256).discover(address()).calendars.size)
        val other = CalDavConnection.sha256(HeldCertificate.Builder().build().certificate)
        expect<CalDavConnection.Failure.UntrustedCertificate> { connection(pin = other).discover(address()) }
    }

    @Test
    fun httpIsRefused(): Unit = runBlocking<Unit> {
        expect<CalDavConnection.Failure.NotHttps> { connection(pin = null).discover("http://example.com/") }
    }

    // ── Helpers ──

    private fun address() = server.url("/").toString()

    private fun connection(pin: String?) = CalDavConnection("me", "app-password", pin, OkHttpClient())

    private suspend inline fun <reified T : Throwable> expect(crossinline block: suspend () -> Unit): T {
        try {
            block()
        } catch (e: Throwable) {
            if (e is T) return e
            fail("expected ${T::class.simpleName}, got $e")
        }
        fail("expected ${T::class.simpleName}, nothing thrown")
        error("unreachable")
    }

    private fun multistatus(responses: String) = MockResponse().setResponseCode(207)
        .addHeader("Content-Type", "application/xml; charset=utf-8")
        .setBody(
            """<?xml version="1.0" encoding="utf-8"?><d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav"
               xmlns:cs="http://calendarserver.org/ns/" xmlns:ic="http://apple.com/ns/ical/">$responses</d:multistatus>"""
        )

    private fun calendar(
        href: String, name: String, collectionOnly: Boolean = false, color: String? = null,
        write: Boolean = false, components: String = "VEVENT", ctag: String? = null
    ): String {
        val type = if (collectionOnly) "<d:collection/>" else "<d:collection/><c:calendar/>"
        val privileges = if (write) "<d:privilege><d:read/></d:privilege><d:privilege><d:write/></d:privilege>" else "<d:privilege><d:read/></d:privilege>"
        return """<d:response><d:href>$href</d:href><d:propstat><d:prop>
            <d:resourcetype>$type</d:resourcetype><d:displayname>$name</d:displayname>
            ${color?.let { "<ic:calendar-color>$it</ic:calendar-color>" } ?: ""}
            ${if (collectionOnly) "" else "<c:supported-calendar-component-set><c:comp name=\"$components\"/></c:supported-calendar-component-set>"}
            <d:current-user-privilege-set>$privileges</d:current-user-privilege-set>
            ${ctag?.let { "<cs:getctag>$it</cs:getctag>" } ?: ""}
            </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"""
    }
}

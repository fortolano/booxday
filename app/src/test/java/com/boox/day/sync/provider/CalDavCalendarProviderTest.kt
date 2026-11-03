package com.boox.day.sync.provider

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.CalendarSelectionEntity
import com.boox.day.data.model.AccountType
import com.boox.day.security.AccountSecretStore
import com.boox.day.sync.caldav.CalDavChoiceNeeded
import com.boox.day.sync.caldav.CalDavConnection
import com.boox.day.sync.caldav.CalDavUseChoice
import kotlinx.coroutines.runBlocking
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
import java.time.ZoneId
import javax.crypto.spec.SecretKeySpec

/**
 * CalDAV accounts as a calendar provider (plan stage 3): the password stays out of the database and
 * signing in again keeps the calendar choices; a sync where one calendar failed never looks complete
 * (the sync manager would delete that calendar's events), a calendar deleted on the server leaves the
 * selection, a refused password asks for signing in again. A local https server stands in for iCloud.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CalDavCalendarProviderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val serverCert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
    private val pin = CalDavConnection.sha256(serverCert.certificate)
    private val server = MockWebServer()
    private lateinit var db: AppDatabase
    private lateinit var secrets: AccountSecretStore
    private lateinit var provider: CalDavCalendarProvider

    /** What the server holds: its password, its calendars, whether Home fails. */
    private var serverPassword = "app-password"
    private val calendars = linkedMapOf("/calendars/me/work/" to "Work", "/calendars/me/home/" to "Home")
    /** Collections that take tasks only (owner decision 78). */
    private val taskLists = linkedMapOf<String, String>()
    private var homeBroken = false
    /** Work is shared (an iCloud Family calendar); the server names a default calendar for new events. */
    private var workShared = false
    private var serverDefault: String? = null

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
        secrets = AccountSecretStore(context.getSharedPreferences("test_secrets", Context.MODE_PRIVATE), { key })
        provider = CalDavCalendarProvider(db, secrets, zone = { ZoneId.of("America/Toronto") })
        server.useHttps(HandshakeCertificates.Builder().heldCertificate(serverCert).build().sslSocketFactory(), false)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.getHeader("Authorization") != okhttp3.Credentials.basic("me", serverPassword)) {
                    return MockResponse().setResponseCode(401).addHeader("WWW-Authenticate", "Basic realm=\"test\"")
                }
                val body = request.body.readUtf8()
                return when (request.path) {
                    "/.well-known/caldav" -> multistatus(response("/.well-known/caldav", "<d:current-user-principal><d:href>/principals/me/</d:href></d:current-user-principal>"))
                    // Like iCloud: the principal names no inbox, the calendar home holds one, and the
                    // default calendar's address comes as plain text
                    "/principals/me/" -> if ("schedule-inbox-URL" in body) multistatus(response("/principals/me/", ""))
                        else multistatus(response("/principals/me/", "<c:calendar-home-set><d:href>/calendars/me/</d:href></c:calendar-home-set>"))
                    "/calendars/me/inbox/" -> multistatus(response("/calendars/me/inbox/", "<c:schedule-default-calendar-URL>$serverDefault</c:schedule-default-calendar-URL>"))
                    "/calendars/me/" -> multistatus(calendars.entries.joinToString("") { (href, name) ->
                        response(
                            href,
                            "<d:resourcetype><d:collection/><c:calendar/>${if (workShared && name == "Work") "<cs:shared-owner/>" else ""}</d:resourcetype><d:displayname>$name</d:displayname>" +
                                "<ic:calendar-color>#3366CCFF</ic:calendar-color>" +
                                "<c:supported-calendar-component-set><c:comp name=\"VEVENT\"/></c:supported-calendar-component-set>" +
                                "<d:current-user-privilege-set><d:privilege><d:read/></d:privilege><d:privilege><d:write/></d:privilege></d:current-user-privilege-set>"
                        )
                    } + taskLists.entries.joinToString("") { (href, name) ->
                        response(
                            href,
                            "<d:resourcetype><d:collection/><c:calendar/></d:resourcetype><d:displayname>$name</d:displayname>" +
                                "<c:supported-calendar-component-set><c:comp name=\"VTODO\"/></c:supported-calendar-component-set>"
                        )
                    } + if (serverDefault != null) response("/calendars/me/inbox/", "<d:resourcetype><d:collection/><c:schedule-inbox/></d:resourcetype>") else "")
                    "/calendars/me/home/" -> if (homeBroken) MockResponse().setResponseCode(500) else multistatus("")
                    "/calendars/me/work/" -> multistatus(
                        response("/calendars/me/work/a.ics", "<d:getetag>\"1\"</d:getetag>" +
                            if ("calendar-multiget" in body) "<c:calendar-data>${DENTIST}</c:calendar-data>" else "")
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    @Test
    fun signingInKeepsThePasswordOutOfTheDatabaseAndSigningInAgainKeepsTheChoices(): Unit = runBlocking<Unit> {
        val account = signIn().getOrThrow()
        assertEquals("me" to address(), account.email to account.serverUrl)
        assertEquals("app-password", secrets.read(account.id))
        assertEquals(listOf("/calendars/me/home/", "/calendars/me/work/"), db.calDavDao().getCalendars(account.id).map { it.href }.sorted())
        val stored = db.connectedAccountDao().getById(account.id).toString() + db.calDavDao().getAccount(account.id).toString()
        assertFalse("never in the database", "app-password" in stored)

        // The user switched Work off, then the password changed on the server: signing in again
        // (the address typed another way) keeps the account and its choice, with the new password
        select(account.id, "/calendars/me/work/", enabled = false)
        serverPassword = "new-password"
        val again = signIn(password = "new-password", address = "${server.hostName.uppercase()}:${server.port}").getOrThrow()
        assertEquals(account.id, again.id)
        assertEquals("new-password", secrets.read(account.id))
        assertFalse(db.calendarSelectionDao().getByAccountAndCalendarId(account.id, "/calendars/me/work/")!!.isFetchEnabled)
    }

    /**
     * Owner decision 78: an account is used for what its server has. Calendars and task lists: the
     * user picks first; only task lists: tasks only (it was refused, "No calendar server found").
     */
    @Test
    fun anAccountIsUsedForWhatItsServerHas(): Unit = runBlocking<Unit> {
        taskLists["/calendars/me/tasks/"] = "Tasks"
        val asked = signInWith().exceptionOrNull()
        assertTrue("both kinds: the user picks", asked is CalDavChoiceNeeded)
        assertEquals(2 to 1, (asked as CalDavChoiceNeeded).let { it.calendarCount to it.taskListCount })

        val picked = signInWith(choice = CalDavUseChoice(calendars = false, tasks = true)).getOrThrow()
        assertTrue("Tasks picked: Task Sync starts", picked.startTasks)
        val dav = db.calDavDao().getAccount(picked.account.id)!!
        assertEquals(false to 1, dav.useCalendars to dav.taskListCount)

        // Signing in again keeps the choice and asks nothing
        val again = signInWith().getOrThrow()
        assertEquals(false to false, db.calDavDao().getAccount(again.account.id)!!.useCalendars to again.startTasks)

        // A server with only task lists connects, for its tasks
        db.connectedAccountDao().deleteById(picked.account.id)
        calendars.clear()
        val tasksOnly = signInWith().getOrThrow()
        assertTrue(tasksOnly.startTasks)
        assertTrue(db.calDavDao().getCalendars(tasksOnly.account.id).isEmpty())
    }

    @Test
    fun aFailedCalendarIsPartialAGoneOneLeavesAndARefusedPasswordAsksToSignIn(): Unit = runBlocking<Unit> {
        val id = signIn().getOrThrow().id
        select(id, "/calendars/me/work/")
        select(id, "/calendars/me/home/")
        val both = setOf("/calendars/me/work/", "/calendars/me/home/")

        // Home fails: never a complete answer, but Work's events come with its colour
        homeBroken = true
        val partial = provider.fetchEvents(id, both, FROM, TO).exceptionOrNull() as CalendarProviderException.PartialSyncException
        assertEquals(1 to 1, partial.syncResult.successCount to partial.syncResult.failureCount)
        assertEquals(listOf("Dentist" to "#3366CC"), partial.partialEvents.map { it.title to it.colorHex })

        // Home deleted on the server: off the selection, its markers forgotten, the sync complete
        calendars.remove("/calendars/me/home/")
        val events = provider.fetchEvents(id, both, FROM, TO).getOrThrow()
        assertEquals(listOf("Dentist"), events.map { it.title })
        assertNull(db.calendarSelectionDao().getByAccountAndCalendarId(id, "/calendars/me/home/"))
        assertNull(db.calDavDao().getCalendar(id, "/calendars/me/home/"))

        // The password was changed on the server: sign in again (Reconnect), not an error
        serverPassword = "changed-elsewhere"
        assertTrue(provider.fetchEvents(id, both, FROM, TO).exceptionOrNull() is CalendarProviderException.AuthenticationException)
    }

    @Test
    fun newEventsGoToTheServersDefaultElseTheFirstCalendarThatIsntShared(): Unit = runBlocking<Unit> {
        val id = signIn().getOrThrow().id
        // Work comes first but is shared with the family: Home
        workShared = true
        assertEquals(listOf("/calendars/me/home/"), provider.fetchCalendars(id).getOrThrow().filter { it.isPrimary }.map { it.id })
        // The server names Work as its default calendar (iCloud's way): Work
        serverDefault = "/calendars/me/work/"
        assertEquals(listOf("/calendars/me/work/"), provider.fetchCalendars(id).getOrThrow().filter { it.isPrimary }.map { it.id })
    }

    // ── Helpers ──

    private suspend fun signIn(password: String = "app-password", address: String = address()) =
        signInWith(password, address).map { it.account }

    private suspend fun signInWith(password: String = "app-password", address: String = address(), choice: CalDavUseChoice? = null) =
        provider.signIn(AccountType.CALDAV_OTHER, address, " me ", password, trustedCertSha256 = pin, choice = choice)

    private fun address() = server.url("/").toString()

    private suspend fun select(accountId: String, href: String, enabled: Boolean = true) =
        db.calendarSelectionDao().upsert(
            CalendarSelectionEntity(id = "$accountId$href", accountId = accountId, calendarId = href, calendarName = href, calendarColor = null, isFetchEnabled = enabled)
        )

    private fun response(href: String, props: String) =
        "<d:response><d:href>$href</d:href><d:propstat><d:prop>$props</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"

    private fun multistatus(responses: String) = MockResponse().setResponseCode(207)
        .addHeader("Content-Type", "application/xml; charset=utf-8")
        .setBody(
            """<?xml version="1.0" encoding="utf-8"?><d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav" """ +
                """xmlns:ic="http://apple.com/ns/ical/" xmlns:cs="http://calendarserver.org/ns/">$responses</d:multistatus>"""
        )

    private companion object {
        val FROM: LocalDate = LocalDate.parse("2026-10-01")
        val TO: LocalDate = LocalDate.parse("2026-11-30")
        val DENTIST = listOf(
            "BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//test//EN", "BEGIN:VEVENT", "UID:a",
            "DTSTART:20261102T150000Z", "DURATION:PT1H", "SUMMARY:Dentist", "END:VEVENT", "END:VCALENDAR"
        ).joinToString("\r\n", postfix = "\r\n")
    }
}

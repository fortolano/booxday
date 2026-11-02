package com.boox.day.tasksync.caldav

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.TaskSyncLink
import com.boox.day.data.db.TaskSyncProvider
import com.boox.day.sync.caldav.CalDavConnection
import com.boox.day.tasksync.OutgoingTask
import com.boox.day.tasksync.TaskExtras
import com.boox.day.tasksync.TaskSyncServiceException
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
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
import java.time.ZoneId

/**
 * CalDAV task lists as a task service (plan stage 4): a pull reports new, changed and deleted tasks
 * (not ticked or repeating ones it would never bring), and again while their merge waits; a change
 * made on the phone during a send is merged first, never overwritten; a new task is a file of its
 * own; a task moves between lists. A local https server stands in for Nextcloud or Radicale, with
 * real change tags (If-Match, If-None-Match, getctag).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CalDavTasksProviderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("America/Toronto")
    private val serverCert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
    private val server = MockWebServer()
    private lateinit var db: AppDatabase

    /** The server's collections: path → (file path → (change tag, text)). */
    private val collections = linkedMapOf(TASKS to linkedMapOf<String, Pair<Int, String>>(), WORK to linkedMapOf(), CAL to linkedMapOf())
    private var ctag = 1
    private val requests = mutableListOf<String>()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO connected_accounts (id, providerId, accountType, email, displayName, isActive, createdAt, lastSyncAt, serverUrl) " +
                "VALUES ('acc-1', 'CALDAV', 'CALDAV_NEXTCLOUD', 'me', NULL, 1, 1, NULL, 'https://localhost/')"
        )
        server.useHttps(HandshakeCertificates.Builder().heldCertificate(serverCert).build().sslSocketFactory(), false)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path!!
                val body = request.body.readUtf8()
                val kind = if (request.method == "REPORT") (if ("calendar-multiget" in body) "multiget" else "query") else request.method!!
                requests += "$kind $path"
                val list = collections.keys.firstOrNull { path.startsWith(it) && path != it }?.let(collections::getValue)
                val current = list?.get(path)
                val ifMatch = request.getHeader("If-Match")
                return when (kind) {
                    "PROPFIND" -> if (request.getHeader("Depth") == "1") home() else collection(path)
                    "query" -> multistatus(collections.getValue(path).entries.joinToString("") { (href, file) -> response(href, "<d:getetag>\"${file.first}\"</d:getetag>") })
                    "multiget" -> multistatus(Regex("<[^>]*href>([^<]+)</").findAll(body).map { it.groupValues[1] }.mapNotNull { href ->
                        collections.values.firstNotNullOfOrNull { it[href] }?.let { (tag, ics) ->
                            response(href, "<d:getetag>\"$tag\"</d:getetag><c:calendar-data>${xml(ics)}</c:calendar-data>")
                        }
                    }.joinToString(""))
                    "PUT" -> when {
                        list == null -> MockResponse().setResponseCode(409)
                        request.getHeader("If-None-Match") == "*" && current != null -> MockResponse().setResponseCode(412)
                        ifMatch != null && ifMatch != "\"${current?.first}\"" -> MockResponse().setResponseCode(412)
                        else -> {
                            val tag = (current?.first ?: 0) + 1
                            list[path] = tag to body
                            ctag++
                            MockResponse().setResponseCode(if (current == null) 201 else 204).addHeader("ETag", "\"$tag\"")
                        }
                    }
                    "DELETE" -> if (current == null) MockResponse().setResponseCode(404) else MockResponse().setResponseCode(204).also { list.remove(path); ctag++ }
                    "MOVE" -> {
                        val to = request.getHeader("Destination")!!.toHttpUrl().encodedPath
                        val target = collections.keys.first { to.startsWith(it) }
                        list!!.remove(path)?.let { collections.getValue(target)[to] = it }
                        ctag++
                        MockResponse().setResponseCode(201)
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
    }

    @Test
    fun aPullBringsNewChangedAndDeletedTasksAndAWaitingMergeComesAgain(): Unit = runBlocking<Unit> {
        put(TASKS, "a.ics", task("a", "Milk"))
        put(TASKS, "b.ics", task("b", "Old", extra = "STATUS:COMPLETED"))
        put(TASKS, "c.ics", task("c", "Pills", extra = "RRULE:FREQ=DAILY"))
        put(TASKS, "d.ics", task("d", "Bread"))

        val first = provider().changes(TASKS, null)
        assertEquals("not ticked or repeating ones (owner decisions 27, 69)", listOf("Milk", "Bread"), first.tasks.map { it.title })
        assertEquals(TaskExtras("", null), first.tasks.first().extras)
        val cursor = first.nextCursor!!
        assertTrue("an unchanged list costs one request", provider().changes(TASKS, cursor).tasks.isEmpty())

        // Both linked; then on the phone Milk changes and Bread goes
        link("t-a", "$TASKS" + "a.ics", "1")
        link("t-d", "$TASKS" + "d.ics", "1")
        put(TASKS, "a.ics", task("a", "Milk 2L"))
        collections.getValue(TASKS).remove("$TASKS" + "d.ics")
        ctag++
        val second = provider().changes(TASKS, cursor)
        assertEquals(listOf("Milk 2L" to false, "Bread" to true), second.tasks.map { it.title to it.deleted })

        // Their merge waited (a full-screen day): the pull keeps its cursor, and they come again
        // from the stored file, without downloading it again
        val downloads = requests.count { it.startsWith("multiget") }
        val again = provider().changes(TASKS, cursor)
        assertEquals(listOf("Milk 2L" to false, "Bread" to true), again.tasks.map { it.title to it.deleted })
        assertEquals(downloads, requests.count { it.startsWith("multiget") })
    }

    @Test
    fun aPhoneChangeDuringASendIsMergedFirstAndOnlyBooxDaysFieldsAreWritten(): Unit = runBlocking<Unit> {
        put(TASKS, "a.ics", task("a", "Milk"))
        provider().changes(TASKS, null)
        link("t-a", "$TASKS" + "a.ics", "1")
        // On the phone meanwhile: new words and a note
        put(TASKS, "a.ics", task("a", "Milk 2L", extra = "DESCRIPTION:the blue one"))

        // The tablet ticks it: refused (changed since), handed to the merge, nothing written
        try {
            provider().upsert(outgoing(title = "Milk", completed = true), db.taskSyncDao().getLink("t-a"))
            fail("a change on the server must be merged first")
        } catch (e: TaskSyncServiceException.RemoteChanged) {
            assertEquals("Milk 2L" to "2", e.remote.title to e.remote.version)
        }
        assertEquals(2, collections.getValue(TASKS).getValue("$TASKS" + "a.ics").first)

        // After the merge (the link at version 2, the phone's words taken), sent once more
        db.taskSyncDao().setMovedVersion("t-a", "2", 1)
        provider().upsert(outgoing(title = "Milk 2L", completed = true), db.taskSyncDao().getLink("t-a"))
        val onServer = collections.getValue(TASKS).getValue("$TASKS" + "a.ics").second
        for (line in listOf("SUMMARY:Milk 2L", "STATUS:COMPLETED", "DESCRIPTION:the blue one", "UID:a")) assertTrue("$line\n$onServer", line in onServer)
    }

    @Test
    fun aNewTaskIsAFileOfItsOwnAndMovesToAnotherList(): Unit = runBlocking<Unit> {
        assertEquals("task lists only, not calendars", listOf("Tasks", "Work"), provider().lists().map { it.title })

        val made = provider().upsert(outgoing(title = "Call mom", extras = TaskExtras("A", 15 * 60)), link = null)
        val file = collections.getValue(TASKS).getValue(made.remoteId).second
        val uid = made.remoteId.removePrefix(TASKS).removeSuffix(".ics")
        assertTrue("its own name, not the row's (a row is reused after a clear)", uid != "t-1" && "UID:$uid" in file)
        for (line in listOf("PRIORITY:1", "DUE;VALUE=DATE:20261105", "TRIGGER;VALUE=DATE-TIME:20261105T200000Z")) assertTrue("$line\n$file", line in file)

        val moved = provider().moveToList(TASKS, made.remoteId, WORK)
        assertEquals(WORK + "$uid.ics", moved.remoteId)
        assertTrue(collections.getValue(TASKS).isEmpty())
        assertEquals("Call mom", moved.title)
    }

    // ── Helpers ──

    private fun provider() = CalDavTasksProvider(
        db, "acc-1", "me", "https://localhost/", server.url("/home/").toString(),
        CalDavConnection("me", "app-password", CalDavConnection.sha256(serverCert.certificate), OkHttpClient()),
        zone = { zone }, now = { NOW }
    )

    private fun outgoing(title: String, completed: Boolean = false, extras: TaskExtras = TaskExtras("", null)) =
        OutgoingTask("t-1", TASKS, title, completed, "2026-11-05", null, extras)

    private fun link(taskId: String, href: String, version: String) = db.taskSyncDao().insertLink(
        TaskSyncLink(
            taskId = taskId, provider = TaskSyncProvider.CALDAV, accountEmail = "me", serverUrl = "https://localhost/",
            listId = TASKS, remoteId = href, remoteVersion = version, syncedAt = 1
        )
    )

    private fun put(list: String, name: String, ics: String) {
        val files = collections.getValue(list)
        files["$list$name"] = ((files["$list$name"]?.first ?: 0) + 1) to ics
        ctag++
    }

    private fun task(uid: String, title: String, extra: String? = null) = listOfNotNull(
        "BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//test//EN", "BEGIN:VTODO", "UID:$uid", "SUMMARY:$title",
        "DUE;VALUE=DATE:20261105", extra, "END:VTODO", "END:VCALENDAR"
    ).joinToString("\r\n", postfix = "\r\n")

    private fun home() = multistatus(
        listOf(TASKS to "Tasks" to "VTODO", WORK to "Work" to "VTODO", CAL to "Calendar" to "VEVENT").joinToString("") { (pathName, comp) ->
            response(
                pathName.first,
                "<d:resourcetype><d:collection/><c:calendar/></d:resourcetype><d:displayname>${pathName.second}</d:displayname>" +
                    "<c:supported-calendar-component-set><c:comp name=\"$comp\"/></c:supported-calendar-component-set>"
            )
        }
    )

    private fun collection(path: String) = multistatus(response(path, "<cs:getctag>$ctag</cs:getctag>"))

    private fun response(href: String, props: String) =
        "<d:response><d:href>$href</d:href><d:propstat><d:prop>$props</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"

    private fun xml(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun multistatus(responses: String) = MockResponse().setResponseCode(207)
        .addHeader("Content-Type", "application/xml; charset=utf-8")
        .setBody(
            """<?xml version="1.0" encoding="utf-8"?><d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav" """ +
                """xmlns:cs="http://calendarserver.org/ns/">$responses</d:multistatus>"""
        )

    private companion object {
        const val TASKS = "/home/tasks/"
        const val WORK = "/home/work/"
        const val CAL = "/home/cal/"
        const val NOW = 1_793_620_800_000L  // 2 Nov 2026
    }
}

package com.boox.day.sync

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.boox.day.APP_SETTINGS_KEY
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.APP_DATABASE_FILE_NAME
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.CalDavAccount
import com.boox.day.data.db.CalDavCalendar
import com.boox.day.data.db.CalendarEventRepository
import com.boox.day.data.db.ConnectedAccountEntity
import com.boox.day.data.db.KvProxy
import com.boox.day.data.db.LocalEvent
import com.boox.day.data.db.LocalEventRepository
import com.boox.day.data.db.SyncStatus
import com.boox.day.data.getDbDir
import com.boox.day.data.model.AccountType
import com.boox.day.data.model.ConnectedAccount
import com.boox.day.data.model.ProviderId
import com.boox.day.security.AccountSecretStore
import com.boox.day.sync.caldav.CalDavConnection
import com.boox.day.sync.outlook.OutlookAuthManager
import com.boox.day.sync.outlook.OutlookCalendarService
import com.boox.day.sync.provider.CalDavCalendarProvider
import com.boox.day.sync.provider.GoogleCalendarProvider
import com.boox.day.sync.provider.OutlookCalendarProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.ZoneId
import java.util.TimeZone
import javax.crypto.spec.SecretKeySpec

/**
 * Events made on the tablet while offline reach a CalDAV calendar later (plan step 3.7): the push
 * runs with no screen open (the saved Cloud Sync mode, not the in-memory defaults), an answer lost
 * on the way back doesn't make a second copy when the push is tried again, and no connection is
 * reported as no network (so the push is retried). A local https server stands in for iCloud.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CalDavOfflinePushTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val serverCert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
    private val server = MockWebServer()
    private lateinit var savedZone: TimeZone
    private var settingsWereLoaded = false
    private lateinit var settingsBefore: AppSettings

    /** The server's calendar /cal/: path → (change tag, file). The first PUT's answer is lost. */
    private val files = linkedMapOf<String, Pair<Int, String>>()
    private var loseNextAnswer = true

    @Before
    fun setUp() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("America/Toronto")))
        settingsWereLoaded = GlobalAppSettings.loaded
        settingsBefore = GlobalAppSettings.current
        deleteDatabaseFiles()
        // Saving an event refreshes its Smart Links in the background (WorkManager)
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        server.useHttps(HandshakeCertificates.Builder().heldCertificate(serverCert).build().sslSocketFactory(), false)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path!!
                val body = request.body.readUtf8()
                val current = files[path]
                return when (request.method) {
                    "PUT" -> when {
                        request.getHeader("If-None-Match") == "*" && current != null -> MockResponse().setResponseCode(412)
                        else -> {
                            val tag = (current?.first ?: 0) + 1
                            files[path] = tag to body
                            if (loseNextAnswer) {
                                // Saved on the server, but the tablet never hears back
                                loseNextAnswer = false
                                MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                            } else {
                                MockResponse().setResponseCode(if (current == null) 201 else 204).addHeader("ETag", "\"$tag\"")
                            }
                        }
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
        GlobalAppSettings.update(settingsBefore)
        GlobalAppSettings.loaded = settingsWereLoaded
        AppDatabase.closeDatabase()
        deleteDatabaseFiles()
        TimeZone.setDefault(savedZone)
    }

    @Test
    fun anOfflineEventReachesTheServerOnceAndNoConnectionIsReportedAsSuch(): Unit = runBlocking<Unit>(Dispatchers.IO) {
        // Cloud Sync is on, but no screen has loaded the settings (the push runs from a worker)
        KvProxy(context).setKv(APP_SETTINGS_KEY, AppSettings(version = 1, syncMode = AppSettings.SyncMode.HYBRID), AppSettings.serializer())
        GlobalAppSettings.update(AppSettings(version = 1))
        GlobalAppSettings.loaded = false

        val db = AppDatabase.getDatabase(context)
        val account = ConnectedAccount("acc-1", ProviderId.CALDAV, AccountType.CALDAV_OTHER, "me", serverUrl = server.url("/").toString())
        db.connectedAccountDao().upsert(ConnectedAccountEntity.fromDomainModel(account))
        db.calDavDao().upsertAccount(CalDavAccount("acc-1", server.url("/").toString(), server.url("/").toString(), "me", CalDavConnection.sha256(serverCert.certificate)))
        db.calDavDao().upsertCalendar(CalDavCalendar("acc-1", "/cal/", null, null))
        val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
        val secrets = AccountSecretStore(context.getSharedPreferences("test_secrets", Context.MODE_PRIVATE), { key })
        secrets.save("acc-1", "app-password").getOrThrow()

        // Made on the tablet while offline (EventsViewModel.createEvent): waiting to be sent
        val localEvents = LocalEventRepository(context)
        val start = java.time.LocalDate.parse("2026-11-03").atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pending = localEvents.createEvent(
            LocalEvent(
                title = "Dentist", dayId = "2026-11-03", endDayId = "2026-11-03", startTime = start, endTime = start + HOUR,
                providerId = ProviderId.CALDAV.key, accountId = "acc-1", providerCalendarId = "/cal/",
                syncEnabled = true, syncStatus = SyncStatus.PENDING_CREATE
            )
        )

        val service = service(CalDavCalendarProvider(db, secrets))
        service.syncPendingChanges()  // the answer is lost: still waiting
        service.syncPendingChanges()  // tried again

        assertEquals("one copy on the server", 1, files.size)
        val sent = localEvents.getEventById(pending.id)!!
        assertEquals(SyncStatus.SYNCED, sent.syncStatus)
        assertTrue("linked to the server's event", sent.providerEventId!!.startsWith("caldav:acc-1:"))

        // No connection at all: reported as no network, so the worker tries again later
        server.shutdown()
        localEvents.createEvent(pending.copy(id = "second", title = "Haircut", syncStatus = SyncStatus.PENDING_CREATE, providerEventId = null))
        val offline = service.syncPendingChanges()
        assertTrue(offline.errors.toString(), offline.errors.single().contains("no network", ignoreCase = true))
    }

    private fun service(calDav: CalDavCalendarProvider): LocalEventService {
        val auth = GoogleAuthManager(context)
        val events = CalendarEventRepository(context)
        val calendarSync = CalendarSyncManager(context, auth, events)
        val db = AppDatabase.getDatabase(context)
        val outlookAuth = OutlookAuthManager(context)
        val manager = MultiAccountSyncManager(
            context,
            GoogleCalendarProvider(context, auth, calendarSync, db.connectedAccountDao(), events),
            OutlookCalendarProvider(context, outlookAuth, OutlookCalendarService(outlookAuth), db.connectedAccountDao()),
            db.connectedAccountDao(), db.calendarSelectionDao(), events, calDav
        )
        return LocalEventService(context, LocalEventRepository(context), calendarSync, manager, auth)
    }

    private fun xml(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun multistatus(responses: String) = MockResponse().setResponseCode(207)
        .addHeader("Content-Type", "application/xml; charset=utf-8")
        .setBody("""<?xml version="1.0" encoding="utf-8"?><d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">$responses</d:multistatus>""")

    private fun deleteDatabaseFiles() {
        val file = File(getDbDir(context), APP_DATABASE_FILE_NAME)
        listOf("", "-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
    }

    private companion object {
        const val HOUR = 3_600_000L
    }
}

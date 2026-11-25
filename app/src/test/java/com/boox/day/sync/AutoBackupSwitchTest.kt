package com.boox.day.sync

import com.boox.day.sync.microsoft.MicrosoftGraph
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * One backup cloud at a time (owner decision 85). An automatic backup takes a while to make: when
 * the user switches to the other cloud or account meanwhile, nothing may land in the old one
 * (review, 25 Nov 2026: the worker kept the cloud it had picked before the export).
 */
class AutoBackupSwitchTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer()
    private val oneDrive = BackupTarget(BackupCloud.ONEDRIVE, "msal-1")
    private val google = BackupTarget(BackupCloud.GOOGLE_DRIVE, "sam@gmail.com")

    @Volatile
    private var current: BackupTarget? = oneDrive

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.shutdown()

    private val service = OneDriveBackupService(
        graph = { MicrosoftGraph(token = { "token" }, baseUrl = server.url("/v1.0").toString().trimEnd('/')) },
        downloadDir = { folder.newFolder() },
        onBackedUp = {},
        pause = {},
        pieceSize = 4
    )

    private fun made(file: java.io.File) = ExportService.ExportResult.Success(
        file, BackupMetadata(appVersion = "1.0.67", deviceModel = "Go 10.3", deviceManufacturer = "Onyx", androidVersion = 35)
    )

    private suspend fun backUp(export: suspend () -> ExportService.ExportResult) = DriveAutoBackupWorker.backUp(
        target = oneDrive,
        currentTarget = { current },
        export = export,
        serviceFor = { cloud -> if (cloud == BackupCloud.ONEDRIVE) service else error("only OneDrive here") }
    )

    @Test
    fun switchedToGoogleDriveDuringTheExportSendsNothingToOneDrive() = runBlocking {
        val file = folder.newFile("booxday_backup_2026-11-25_101500.zip").apply { writeBytes(ByteArray(10)) }

        val outcome = backUp {
            current = google
            made(file)
        }

        assertEquals(DriveAutoBackupWorker.Outcome.STOPPED, outcome)
        assertEquals(0, server.requestCount)
        assertFalse(file.exists())
    }

    @Test
    fun anotherAccountChosenBetweenPiecesMakesNoFile() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.method) {
                "POST" -> MockResponse().setBody("""{"uploadUrl":"${server.url("/up/s1")}"}""")
                else -> {
                    // The user connects another Microsoft account while the first piece goes up
                    current = BackupTarget(BackupCloud.ONEDRIVE, "msal-2")
                    MockResponse().setResponseCode(202).setBody("""{"nextExpectedRanges":["4-"]}""")
                }
            }
        }
        val file = folder.newFile("booxday_backup_2026-11-25_101500.zip").apply { writeBytes(ByteArray(10)) }

        val outcome = backUp { made(file) }

        assertEquals(DriveAutoBackupWorker.Outcome.STOPPED, outcome)
        // The session and the first piece only: the piece that would finish the file never went
        assertEquals(2, server.requestCount)
        assertEquals("POST", server.takeRequest().method)
        assertEquals("bytes 0-3/10", server.takeRequest().getHeader("Content-Range"))
    }
}

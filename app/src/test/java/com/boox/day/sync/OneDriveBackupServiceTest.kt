package com.boox.day.sync

import com.boox.day.sync.microsoft.GraphException
import com.boox.day.sync.microsoft.MicrosoftGraph
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.TimeUnit

/**
 * OneDrive backup (owner decisions 85-88, Nov 2026): the upload goes in pieces Microsoft accepts
 * (a wrong range or a piece size not a multiple of 320 KiB fails the whole file), a busy or lost
 * piece is tried again but never forever, and the list shows only backups, newest first.
 */
class OneDriveBackupServiceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer()
    private val pauses = mutableListOf<Long>()
    private var backedUpAt = 0L

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.shutdown()

    private fun service(token: suspend (Boolean) -> String = { "token" }) = OneDriveBackupService(
        graph = {
            MicrosoftGraph(
                token = token,
                baseUrl = server.url("/v1.0").toString().trimEnd('/'),
                // No silent retry by OkHttp itself, so a dropped reply reaches the service
                client = OkHttpClient.Builder().retryOnConnectionFailure(false).readTimeout(5, TimeUnit.SECONDS).build()
            )
        },
        downloadDir = { folder.newFolder() },
        onBackedUp = { backedUpAt = it },
        pause = { pauses += it },
        pieceSize = 4
    )

    private fun backupFile(bytes: ByteArray) = folder.newFile("booxday_backup_2026-11-25_101500.zip").apply { writeBytes(bytes) }

    private fun session() = MockResponse().setBody("""{"uploadUrl":"${server.url("/up/s1")}"}""")
    private fun more() = MockResponse().setResponseCode(202).setBody("""{"nextExpectedRanges":["4-"]}""")
    private fun finished() = MockResponse().setResponseCode(201).setBody("""{"id":"ITEM1","name":"booxday_backup_2026-11-25_101500.zip","size":10}""")

    @Test
    fun piecesCoverTheFileInOrder() = runBlocking {
        val bytes = ByteArray(10) { it.toByte() }
        server.enqueue(session())
        server.enqueue(more())
        server.enqueue(more())
        server.enqueue(finished())

        val result = service().uploadBackup(backupFile(bytes))

        assertEquals(DriveBackupService.UploadResult.Success("ITEM1", "booxday_backup_2026-11-25_101500.zip"), result)
        val create = server.takeRequest()
        assertEquals("POST", create.method)
        assertEquals("/v1.0/me/drive/special/approot:/booxday_backup_2026-11-25_101500.zip:/createUploadSession", create.path)
        assertTrue(create.body.readUtf8().contains("\"rename\""))
        val ranges = listOf("bytes 0-3/10", "bytes 4-7/10", "bytes 8-9/10")
        val sent = ByteArray(10)
        var at = 0
        for (range in ranges) {
            val piece = server.takeRequest()
            assertEquals("PUT", piece.method)
            assertEquals(range, piece.getHeader("Content-Range"))
            // The upload address carries its own permission; Microsoft refuses a token with it
            assertNull(piece.getHeader("Authorization"))
            val body = piece.body.readByteArray()
            body.copyInto(sent, at)
            at += body.size
        }
        assertArrayEquals(bytes, sent)
        assertTrue(backedUpAt > 0)
    }

    @Test
    fun realPieceSizeIsAMultipleOf320KiB() {
        assertEquals(0, OneDriveBackupService.PIECE_SIZE % (320 * 1024))
        assertTrue(OneDriveBackupService.PIECE_SIZE < 60 * 1024 * 1024)
    }

    @Test
    fun busyPieceIsSentAgainAfterMicrosoftsWait() = runBlocking {
        server.enqueue(session())
        server.enqueue(more())
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "3"))
        server.enqueue(more())
        server.enqueue(finished())

        val result = service().uploadBackup(backupFile(ByteArray(10)))

        assertTrue(result is DriveBackupService.UploadResult.Success)
        assertEquals(listOf(3_000L), pauses)
        server.takeRequest()
        val ranges = (1..4).map { server.takeRequest().getHeader("Content-Range") }
        assertEquals(listOf("bytes 0-3/10", "bytes 4-7/10", "bytes 4-7/10", "bytes 8-9/10"), ranges)
    }

    @Test
    fun aPieceMicrosoftAlreadyHasIsNotSentTwice() = runBlocking {
        server.enqueue(session())
        server.enqueue(more())
        // The second piece arrives but its answer is lost; sent again, Microsoft says it has it
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(MockResponse().setResponseCode(416))
        server.enqueue(finished())

        val result = service().uploadBackup(backupFile(ByteArray(10)))

        assertTrue(result is DriveBackupService.UploadResult.Success)
        server.takeRequest()
        val ranges = (1..4).map { server.takeRequest().getHeader("Content-Range") }
        assertEquals(listOf("bytes 0-3/10", "bytes 4-7/10", "bytes 4-7/10", "bytes 8-9/10"), ranges)
    }

    @Test
    fun aPieceThatKeepsFailingEndsTheTry() = runBlocking {
        server.enqueue(session())
        repeat(4) { server.enqueue(MockResponse().setResponseCode(503)) }

        val result = service().uploadBackup(backupFile(ByteArray(10)))

        assertEquals(DriveBackupService.UploadResult.Error("OneDrive is busy. Try again in a few minutes.", retryable = true), result)
        assertEquals(listOf(2_000L, 4_000L, 8_000L), pauses)
        assertEquals(5, server.requestCount)
        assertEquals(0L, backedUpAt)
    }

    @Test
    fun signInNeededIsNotRetried() = runBlocking {
        val result = service(token = { throw GraphException.SignInNeeded() }).uploadBackup(backupFile(ByteArray(10)))

        assertEquals(DriveBackupService.UploadResult.Error("OneDrive needs you to sign in again.", retryable = false), result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun listShowsOnlyBackupsNewestFirst() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"value":[
                  {"id":"a","name":"booxday_backup_2026-11-01_080000.zip","size":100,"createdDateTime":"2026-11-01T08:00:00Z","file":{}},
                  {"id":"b","name":"booxday_backup_2026-11-20_080000.zip","size":200,"createdDateTime":"2026-11-20T08:00:00Z","file":{}},
                  {"id":"c","name":"booxday_backup_2026-01-05_080000.json","size":50,"createdDateTime":"2026-01-05T08:00:00Z","file":{}},
                  {"id":"d","name":"booxday_backup_2026-11-20_080000_meta.json","size":5,"createdDateTime":"2026-11-21T08:00:00Z","file":{}},
                  {"id":"e","name":"notes.txt","size":5,"createdDateTime":"2026-11-22T08:00:00Z","file":{}},
                  {"id":"f","name":"old.zip","createdDateTime":"2026-11-23T08:00:00Z","folder":{"childCount":2}}
                ]}"""
            )
        )

        val result = service().listBackups() as DriveBackupService.ListResult.Success

        assertEquals(listOf("b", "a", "c"), result.backups.map { it.fileId })
        assertEquals(200L, result.backups.first().sizeBytes)
        assertEquals(java.time.Instant.parse("2026-11-20T08:00:00Z").toEpochMilli(), result.backups.first().createdTime)
        assertTrue(server.takeRequest().path!!.startsWith("/v1.0/me/drive/special/approot/children"))
    }

    @Test
    fun emptyFolderHasNoBackups() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"value":[]}"""))
        assertFalse(service().hasExistingBackups())
    }

    @Test
    fun downloadFollowsOneDrivesRedirectIntoATempFile() = runBlocking {
        val bytes = "PK backup bytes".toByteArray()
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/files/ITEM1")))
        server.enqueue(MockResponse().setBody(okio.Buffer().write(bytes)))

        val result = service().downloadBackupToFile("ITEM1") as DriveBackupService.FileDownloadResult.Success

        assertArrayEquals(bytes, result.file.readBytes())
        assertEquals("/v1.0/me/drive/items/ITEM1/content", server.takeRequest().path)
    }

    @Test
    fun failedDownloadLeavesNoFile() = runBlocking {
        val dir = folder.newFolder()
        server.enqueue(MockResponse().setResponseCode(404))
        val service = OneDriveBackupService(
            graph = { MicrosoftGraph(token = { "token" }, baseUrl = server.url("/v1.0").toString().trimEnd('/')) },
            downloadDir = { dir },
            onBackedUp = {}
        )

        val result = service.downloadBackupToFile("gone")

        assertTrue(result is DriveBackupService.FileDownloadResult.Error)
        assertEquals(0, dir.listFiles()!!.size)
    }
}

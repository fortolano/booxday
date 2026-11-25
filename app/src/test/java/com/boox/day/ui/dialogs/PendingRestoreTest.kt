package com.boox.day.ui.dialogs

import android.app.Application
import com.boox.day.sync.BackupMetadata
import com.boox.day.sync.DriveBackupService
import com.boox.day.sync.ImportService
import com.boox.day.sync.LocalBackupService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Owner decision 87 (Nov 2026): a cloud backup's Restore replaced all data in one tap. It must be
 * previewed and wait for "Restore from Backup?" like a device file; the downloaded copy goes away
 * either way, a device file never does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PendingRestoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val preview = ImportService.BackupPreview(
        metadata = BackupMetadata(appVersion = "1.0.66", deviceModel = "Go 10.3", deviceManufacturer = "Onyx", androidVersion = 35),
        eventCount = 3, taskCount = 4, pageCount = 5, strokeCount = 60, calendarCount = 1
    )
    private val restored = mutableListOf<java.io.File>()
    private val restore: suspend (java.io.File) -> LocalBackupService.RestoreResult = {
        restored += it
        LocalBackupService.RestoreResult.Error("not in this test")
    }

    @Test
    fun aCloudBackupIsRestoredOnlyAfterTheUserSaysRestore() = runBlocking {
        val download = folder.newFile("download.tmp")
        val pending = PendingRestore()

        val problem = pending.offerDownload(
            { DriveBackupService.FileDownloadResult.Success(download) },
            { LocalBackupService.PreviewResult.Success(preview) }
        )

        assertNull(problem)
        assertEquals(preview, pending.waiting!!.preview)
        assertTrue(restored.isEmpty())

        pending.confirm(restore)

        assertEquals(listOf(download), restored)
        assertFalse(download.exists())
        assertNull(pending.waiting)
    }

    @Test
    fun cancelDeletesTheDownloadButNeverADeviceFile() = runBlocking {
        val download = folder.newFile("download.tmp")
        val pending = PendingRestore()
        pending.offerDownload({ DriveBackupService.FileDownloadResult.Success(download) }, { LocalBackupService.PreviewResult.Success(preview) })
        pending.cancel()
        assertFalse(download.exists())

        val deviceFile = folder.newFile("booxday_backup_2026-11-25_101500.zip")
        pending.offerFile(preview, deviceFile)
        pending.cancel()
        assertTrue(deviceFile.exists())
        assertTrue(restored.isEmpty())
    }

    /** Review 25 Nov 2026: closing the screen during the preview left the private backup in the cache. */
    @Test
    fun closingDuringThePreviewDeletesTheDownload() = runBlocking {
        val download = folder.newFile("download.tmp")
        val pending = PendingRestore()
        val previewing = CompletableDeferred<Unit>()

        val screen = launch {
            pending.offerDownload(
                { DriveBackupService.FileDownloadResult.Success(download) },
                { previewing.complete(Unit); awaitCancellation() }
            )
        }
        previewing.await()
        screen.cancelAndJoin()

        assertFalse(download.exists())
        assertNull(pending.waiting)
        assertTrue(restored.isEmpty())
    }

    @Test
    fun anUnreadableDownloadWaitsForNothing() = runBlocking {
        val download = folder.newFile("download.tmp")
        val pending = PendingRestore()

        val problem = pending.offerDownload(
            { DriveBackupService.FileDownloadResult.Success(download) },
            { LocalBackupService.PreviewResult.Error("This file is not a BooxDay backup.") }
        )

        assertEquals("Cannot open backup: This file is not a BooxDay backup.", problem)
        assertNull(pending.waiting)
        assertFalse(download.exists())
    }
}

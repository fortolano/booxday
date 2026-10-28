package com.boox.day.sync

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.LowLevelHttpRequest
import com.google.api.client.http.LowLevelHttpResponse
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.testing.http.MockHttpTransport
import com.google.api.client.testing.http.MockLowLevelHttpRequest
import com.google.api.client.testing.http.MockLowLevelHttpResponse
import com.google.api.services.drive.Drive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The Drive backup folder is found or made by every upload and list (Oct 2026). Backup Now used to
 * read only the saved folder ID, which only some setup steps in the backup dialog wrote, so it failed
 * with "Backup folder not configured"; an ID saved under the old Google Cloud project points to a
 * folder drive.file can't see any more (404).
 */
class DriveBackupFolderTest {

    /** Drive's files endpoint with [folders] (ID to trashed) visible to the app. */
    private class FakeDrive(
        val folders: MutableMap<String, Boolean> = mutableMapOf(),
        val getStatus: Int? = null
    ) {
        val requests = mutableListOf<String>()
        val createdBodies = mutableListOf<String>()

        val drive: Drive = Drive.Builder(
            object : MockHttpTransport() {
                override fun buildRequest(method: String, url: String): LowLevelHttpRequest =
                    object : MockLowLevelHttpRequest(url) {
                        override fun execute(): LowLevelHttpResponse = answer(method, url, this)
                    }
            },
            GsonFactory.getDefaultInstance(),
            null
        ).setApplicationName("BooxDay test").build()

        private fun answer(method: String, url: String, request: MockLowLevelHttpRequest): LowLevelHttpResponse {
            val path = url.substringAfter("/drive/v3/files").substringBefore('?')
            requests += "$method files$path"
            return when {
                method == "GET" && path.isEmpty() -> {
                    // Search by name: the visible folders that aren't in the trash
                    val ids = folders.filterValues { trashed -> !trashed }.keys
                    json(200, """{"files":[${ids.joinToString(",") { """{"id":"$it"}""" }}]}""")
                }
                method == "GET" -> {
                    val id = path.removePrefix("/")
                    when {
                        getStatus != null -> json(getStatus, """{"error":{"code":$getStatus,"message":"Backend Error"}}""")
                        id !in folders -> json(404, """{"error":{"code":404,"message":"File not found: $id."}}""")
                        else -> json(200, """{"id":"$id","trashed":${folders.getValue(id)}}""")
                    }
                }
                method == "POST" -> {
                    createdBodies += request.contentAsString // undoes the client's gzip
                    folders[NEW_ID] = false
                    json(200, """{"id":"$NEW_ID"}""")
                }
                else -> json(400, """{"error":{"code":400,"message":"unexpected"}}""")
            }
        }

        private fun json(status: Int, body: String) = MockLowLevelHttpResponse()
            .setStatusCode(status)
            .setContentType("application/json; charset=UTF-8")
            .setContent(body)
    }

    private val saved = mutableListOf<String?>()
    private val save: (String?) -> Unit = { saved += it }

    @Test
    fun `Backup Now with no saved folder makes BooxDay Backups and saves it`() {
        val fake = FakeDrive()

        val result = DriveBackupService.findOrCreateBackupFolder(fake.drive, storedFolderId = null, save)

        assertEquals(DriveBackupService.FolderResult.Success(NEW_ID, wasCreated = true), result)
        assertEquals(listOf<String?>(NEW_ID), saved)
        assertEquals(1, fake.createdBodies.size)
        val body = fake.createdBodies.single()
        assertTrue(body, body.contains("\"name\":\"BooxDay Backups\""))
        assertTrue(body, body.contains("\"mimeType\":\"application/vnd.google-apps.folder\""))
    }

    @Test
    fun `a folder saved under the old Google project is dropped and a new one made`() {
        val fake = FakeDrive() // the old project's folder answers 404 under drive.file

        val result = DriveBackupService.findOrCreateBackupFolder(fake.drive, storedFolderId = "old-project-folder", save)

        assertEquals(DriveBackupService.FolderResult.Success(NEW_ID, wasCreated = true), result)
        assertEquals(listOf(null, NEW_ID), saved)
        assertEquals(listOf("GET files/old-project-folder", "GET files", "POST files"), fake.requests)
    }

    @Test
    fun `the saved folder is used while Drive has it`() {
        val fake = FakeDrive(mutableMapOf("saved-folder" to false))

        val result = DriveBackupService.findOrCreateBackupFolder(fake.drive, storedFolderId = "saved-folder", save)

        assertEquals(DriveBackupService.FolderResult.Success("saved-folder", wasCreated = false), result)
        assertEquals(emptyList<String?>(), saved)
        assertEquals(listOf("GET files/saved-folder"), fake.requests)
    }

    @Test
    fun `a folder found by name is reused, nothing made`() {
        val fake = FakeDrive(mutableMapOf("found-folder" to false))

        val result = DriveBackupService.findOrCreateBackupFolder(fake.drive, storedFolderId = null, save)

        assertEquals(DriveBackupService.FolderResult.Success("found-folder", wasCreated = false), result)
        assertEquals(listOf<String?>("found-folder"), saved)
        assertTrue(fake.createdBodies.isEmpty())
    }

    @Test
    fun `a saved folder in the trash is replaced`() {
        val fake = FakeDrive(mutableMapOf("trashed-folder" to true))

        val result = DriveBackupService.findOrCreateBackupFolder(fake.drive, storedFolderId = "trashed-folder", save)

        assertEquals(DriveBackupService.FolderResult.Success(NEW_ID, wasCreated = true), result)
        assertEquals(listOf(null, NEW_ID), saved)
    }

    @Test
    fun `a Drive error other than 404 keeps the saved folder and makes nothing`() {
        val fake = FakeDrive(mutableMapOf("saved-folder" to false), getStatus = 500)

        try {
            DriveBackupService.findOrCreateBackupFolder(fake.drive, storedFolderId = "saved-folder", save)
            fail("expected the Drive error to reach the caller")
        } catch (e: GoogleJsonResponseException) {
            assertEquals(500, e.statusCode)
        }
        assertEquals(emptyList<String?>(), saved)
        assertTrue(fake.createdBodies.isEmpty())
    }

    @Test
    fun `the restore list with no folder makes none`() {
        val fake = FakeDrive()

        assertNull(DriveBackupService.findBackupFolder(fake.drive, storedFolderId = "old-project-folder", save))
        assertEquals(listOf<String?>(null), saved)
        assertTrue(fake.createdBodies.isEmpty())
    }

    @Test
    fun `the restore list finds the folder when none is saved`() {
        val fake = FakeDrive(mutableMapOf("found-folder" to false))

        assertEquals("found-folder", DriveBackupService.findBackupFolder(fake.drive, storedFolderId = null, save))
        assertEquals(listOf<String?>("found-folder"), saved)
    }

    private companion object {
        const val NEW_ID = "new-folder"
    }
}

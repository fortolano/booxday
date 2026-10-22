package com.boox.day.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading GitHub's `releases/latest` answer and comparing versions (Oct 2026). The release below is
 * the real v1.0.33 answer (22 Oct 2026), cut to the fields Boox Day reads plus a few it ignores.
 */
class AppUpdateCheckerTest {

    private val notes = "Writing and compatibility improvements.\\n\\n" +
        "- Updated to BOOX Pen SDK 1.5.8 with improved pen input handling and pressure support.\\n" +
        "- Standard Eraser now removes the section you touch instead of the whole stroke. Undo and Redo preserve the remaining ink.\\n" +
        "- Fixed sparkling ink after adding a memo page and reduced interruptions while writing.\\n" +
        "- Improved startup drawing setup and memory use on larger screens.\\n" +
        "- Fixed the calendar quick-view buttons and improved opening linked BOOX Notes.\\n" +
        "- Updated native libraries for 16 KB memory-page alignment and added Android 15/16 regression coverage.\\n\\n" +
        "Verified on Go6_2: smooth writing after creating a page.\\n\\n" +
        "Install: download `booxday-1.0.33.apk` on the tablet, open it and allow installs from your browser or file manager if asked."

    private fun release(
        tag: String = "v1.0.33",
        draft: Boolean = false,
        prerelease: Boolean = false,
        assetName: String = "booxday-1.0.33.apk",
        digest: String? = "sha256:135753e2adc7ea175299b0820e958bab5d1272e07b24bd721cc6c66f701c8b20",
        size: Long = 84306565
    ): String = """
        {
          "url": "https://api.github.com/repos/fortolano/booxday/releases/123",
          "html_url": "https://github.com/fortolano/booxday/releases/tag/$tag",
          "id": 123,
          "tag_name": "$tag",
          "name": "BooxDay 1.0.33",
          "draft": $draft,
          "prerelease": $prerelease,
          "published_at": "2026-10-19T21:45:49Z",
          "author": { "login": "fortolano", "id": 1 },
          "assets": [
            {
              "id": 456,
              "name": "$assetName",
              "content_type": "application/vnd.android.package-archive",
              "state": "uploaded",
              "size": $size,
              ${if (digest != null) "\"digest\": \"$digest\"," else ""}
              "download_count": 12,
              "browser_download_url": "https://github.com/fortolano/booxday/releases/download/$tag/$assetName"
            }
          ],
          "body": "$notes"
        }
    """.trimIndent()

    @Test
    fun readsTheRealReleaseAnswer() {
        val info = AppUpdateChecker.parseRelease(release())
        assertNotNull(info)
        info!!
        assertEquals("1.0.33", info.version)
        assertEquals(84306565L, info.sizeBytes)
        assertEquals("135753e2adc7ea175299b0820e958bab5d1272e07b24bd721cc6c66f701c8b20", info.sha256)
        assertEquals("https://github.com/fortolano/booxday/releases/download/v1.0.33/booxday-1.0.33.apk", info.apkUrl)
        assertEquals(6, info.notes.size)
        assertEquals("Updated to BOOX Pen SDK 1.5.8 with improved pen input handling and pressure support.", info.notes.first())
        assertTrue(info.notes.none { it.contains("Install") || it.contains("Verified") })
    }

    @Test
    fun skipsDraftsPrereleasesAndUnverifiableAssets() {
        assertNull(AppUpdateChecker.parseRelease(release(draft = true)))
        assertNull(AppUpdateChecker.parseRelease(release(prerelease = true)))
        assertNull(AppUpdateChecker.parseRelease(release(digest = null)))
        assertNull(AppUpdateChecker.parseRelease(release(digest = "md5:abc")))
        assertNull(AppUpdateChecker.parseRelease(release(assetName = "notes.zip")))
        assertNull(AppUpdateChecker.parseRelease(release(size = 0)))
        assertNull(AppUpdateChecker.parseRelease(release(tag = "latest")))
        assertNull(AppUpdateChecker.parseRelease("not json"))
    }

    @Test
    fun digestIsReadInLowerCase() {
        val info = AppUpdateChecker.parseRelease(
            release(digest = "sha256:135753E2ADC7EA175299B0820E958BAB5D1272E07B24BD721CC6C66F701C8B20")
        )
        assertEquals("135753e2adc7ea175299b0820e958bab5d1272e07b24bd721cc6c66f701c8b20", info?.sha256)
    }

    @Test
    fun comparesVersionsByNumber() {
        assertTrue(AppUpdateChecker.isNewer("1.0.35", "1.0.34"))
        assertTrue(AppUpdateChecker.isNewer("1.0.10", "1.0.9"))
        assertTrue(AppUpdateChecker.isNewer("1.1", "1.0.99"))
        assertTrue(AppUpdateChecker.isNewer("1.0.0.1", "1.0.0"))
        assertFalse(AppUpdateChecker.isNewer("1.0.34", "1.0.34"))
        assertFalse(AppUpdateChecker.isNewer("1.0.33", "1.0.34"))
        assertFalse(AppUpdateChecker.isNewer("1.0", "1.0.0"))
        // Debug "next" builds carry a date suffix; only the numbers count
        assertFalse(AppUpdateChecker.isNewer("1.0.34", "1.0.34-next-20.10.2026-14:00"))
        assertTrue(AppUpdateChecker.isNewer("1.0.35", "1.0.34-next-20.10.2026-14:00"))
        // Unreadable candidate is never newer; unreadable current version accepts any readable one
        assertFalse(AppUpdateChecker.isNewer("latest", "1.0.34"))
        assertTrue(AppUpdateChecker.isNewer("1.0.35", "dev"))
    }

    @Test
    fun parsesTagsAndVersionNames() {
        assertEquals(listOf(1, 0, 33), AppUpdateChecker.parseVersion("v1.0.33"))
        assertEquals(listOf(1, 0, 33), AppUpdateChecker.parseVersion("1.0.33"))
        assertEquals(listOf(1, 0, 34), AppUpdateChecker.parseVersion("1.0.34-next-20.10.2026-14:00"))
        assertNull(AppUpdateChecker.parseVersion("BooxDay"))
    }

    @Test
    fun notesKeepOnlyBulletsWithoutMarkdown() {
        val bullets = AppUpdateChecker.releaseNotes(
            "Bug fix release.\n\n- **Tasks**: rows `fixed`.\n* Calendar: faster.\n- Install: see below.\n\nInstall: download it."
        )
        assertEquals(listOf("Tasks: rows fixed.", "Calendar: faster."), bullets)
        assertEquals(6, AppUpdateChecker.releaseNotes((1..9).joinToString("\n") { "- Item $it" }).size)
        assertTrue(AppUpdateChecker.releaseNotes(null).isEmpty())
    }
}

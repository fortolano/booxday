package com.boox.day.sync

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Removing the last Google account withdraws BooxDay's Google access (Nov 2026). It used to clear
 * the whole settings file, which also holds the calendar sync switch and Sync frequency that Outlook
 * and CalDAV accounts use: they went back to "on, every 4 hours". (The app's file is encrypted with
 * the Android Keystore, which Robolectric lacks, so a plain file stands in for it.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class GoogleAccessWithdrawnTest {

    @Test
    fun withdrawingGoogleAccessKeepsTheSyncSettings() {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("google_auth_test", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("sync_enabled", false)
            .putString("sync_frequency", GoogleAuthManager.FREQUENCY_DAILY)
            .putString("backup_frequency", "Monthly")
            .putBoolean("drive_backup_enabled", true)
            .putString("backup_folder_id", "folder")
            .putString("selected_account", "me@example.com")
            .commit()

        GoogleAuthManager.clearSignInData(prefs)

        assertFalse(prefs.getBoolean("sync_enabled", true))
        assertEquals(GoogleAuthManager.FREQUENCY_DAILY, prefs.getString("sync_frequency", null))
        assertEquals("Monthly", prefs.getString("backup_frequency", null))
        // the Google account's own backup and sign-in go with its access
        assertEquals(setOf("sync_enabled", "sync_frequency", "backup_frequency"), prefs.all.keys)
    }
}

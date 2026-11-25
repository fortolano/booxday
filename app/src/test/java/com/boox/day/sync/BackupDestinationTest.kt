package com.boox.day.sync

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * One backup cloud at a time (owner decision 85, Nov 2026). Someone backing up to Google Drive
 * before this must still be, with the same frequency and last backup; automatic backup goes only to
 * the chosen cloud. (The app's file is encrypted with the Android Keystore, which Robolectric lacks,
 * so a plain file stands in for it.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BackupDestinationTest {
    private lateinit var prefs: SharedPreferences
    private val day = 24 * 60 * 60 * 1000L
    private val now = 1_800_000_000_000L
    private val everyCloud: (BackupCloud) -> Boolean = { true }

    @Before
    fun setUp() {
        prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("backup_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun googleDriveBackupFromBeforeReadsBackUnchanged() {
        prefs.edit()
            .putBoolean("drive_backup_enabled", true)
            .putString("backup_frequency", "Daily")
            .putLong("last_backup_time", now - 2 * day)
            .commit()

        assertEquals(BackupCloud.GOOGLE_DRIVE, BackupPrefs.destination(prefs))
        assertEquals(now - 2 * day, prefs.getLong(BackupPrefs.lastBackupKey(BackupCloud.GOOGLE_DRIVE), 0L))
        assertEquals(BackupCloud.GOOGLE_DRIVE, BackupPrefs.dueCloud(prefs, everyCloud, now))
    }

    @Test
    fun backupOffFromBeforeIsNowhere() {
        prefs.edit().putBoolean("drive_backup_enabled", false).commit()
        assertNull(BackupPrefs.destination(prefs))
        assertNull(BackupPrefs.dueCloud(prefs, everyCloud, now))
    }

    @Test
    fun automaticBackupGoesOnlyToTheChosenCloud() {
        // Google Drive has never been backed up to, but OneDrive is the one chosen
        prefs.edit().putString("backup_frequency", "Daily").putLong(BackupPrefs.ONEDRIVE_LAST_BACKUP_TIME, now - 2 * day).commit()
        prefs.edit().also { BackupPrefs.putDestination(it, BackupCloud.ONEDRIVE) }.commit()

        assertFalse(prefs.getBoolean("drive_backup_enabled", true))
        assertEquals(BackupCloud.ONEDRIVE, BackupPrefs.dueCloud(prefs, everyCloud, now))
        // Its own last backup counts, not Google Drive's
        prefs.edit().putLong(BackupPrefs.ONEDRIVE_LAST_BACKUP_TIME, now - day / 2).commit()
        assertNull(BackupPrefs.dueCloud(prefs, everyCloud, now))
        // Not connected: nothing, and never Google Drive instead
        assertNull(BackupPrefs.dueCloud(prefs, { it == BackupCloud.GOOGLE_DRIVE }, now + 2 * day))
    }

    @Test
    fun monthlyWaitsThirtyDays() {
        prefs.edit().putString("backup_frequency", "Monthly").putLong("last_backup_time", now - 10 * day).commit()
        prefs.edit().also { BackupPrefs.putDestination(it, BackupCloud.GOOGLE_DRIVE) }.commit()

        assertNull(BackupPrefs.dueCloud(prefs, everyCloud, now))
        assertEquals(BackupCloud.GOOGLE_DRIVE, BackupPrefs.dueCloud(prefs, everyCloud, now + 21 * day))
    }
}

package com.boox.day.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * After a sign-in from the backup screen, Auto Backup turns on by itself only when no cloud is
 * chosen yet and the backups were listed and there are none. Review, 25 Nov 2026: a folder that
 * couldn't be read counted as empty, and signing in to restore turned it on too. Owner, same day:
 * the same rule for Google Drive as for OneDrive.
 */
class AutoBackupOnSignInTest {
    private val empty = DriveBackupService.ListResult.Success(emptyList())
    private val failed = DriveBackupService.ListResult.Error("Couldn't reach OneDrive. Check the internet connection, then try again.")

    @Test
    fun emptyFolderAndNoCloudChosenTurnsItOn() {
        assertTrue(turnsOnAutoBackupAfterSignIn(forRestore = false, destination = null, lookup = empty))
    }

    @Test
    fun aFolderThatCouldNotBeReadLeavesItOff() {
        assertFalse(turnsOnAutoBackupAfterSignIn(forRestore = false, destination = null, lookup = failed))
        // Google Drive's list when its folder search failed (it used to read as "no folder")
        assertFalse(turnsOnAutoBackupAfterSignIn(forRestore = false, destination = null, lookup = DriveBackupService.ListResult.Error("503 Backend Error")))
    }

    /** Owner, 25 Nov 2026: Google Drive's restore card too, like OneDrive's. */
    @Test
    fun signingInFromTheRestoreCardNeverTurnsItOn() {
        assertFalse(turnsOnAutoBackupAfterSignIn(forRestore = true, destination = null, lookup = empty))
        assertFalse(turnsOnAutoBackupAfterSignIn(forRestore = true, destination = null, lookup = null))
    }

    @Test
    fun backupsThereOrAnotherCloudChosenLeaveItOff() {
        val one = DriveBackupService.ListResult.Success(listOf(DriveBackupService.BackupInfo("1", "booxday_backup_2026-11-20_080000.zip", 1L, 10L)))
        assertFalse(turnsOnAutoBackupAfterSignIn(forRestore = false, destination = null, lookup = one))
        assertFalse(turnsOnAutoBackupAfterSignIn(forRestore = false, destination = BackupCloud.ONEDRIVE, lookup = empty))
        assertFalse(turnsOnAutoBackupAfterSignIn(forRestore = false, destination = BackupCloud.GOOGLE_DRIVE, lookup = empty))
    }
}

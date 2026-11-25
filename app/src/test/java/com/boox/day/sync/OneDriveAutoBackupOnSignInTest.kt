package com.boox.day.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * After a OneDrive sign-in, Auto Backup turns on by itself only like Google Drive's (owner's yes,
 * 25 Nov 2026): no cloud chosen yet and the folder read and empty. Review, same day: a folder that
 * couldn't be read counted as empty, and signing in to restore turned it on too.
 */
class OneDriveAutoBackupOnSignInTest {
    private val empty = DriveBackupService.ListResult.Success(emptyList())
    private val failed = DriveBackupService.ListResult.Error("Couldn't reach OneDrive. Check the internet connection, then try again.")

    @Test
    fun emptyFolderAndNoCloudChosenTurnsItOn() {
        assertTrue(OneDriveSignIn.turnsOnAutoBackup(forRestore = false, destination = null, lookup = empty))
    }

    @Test
    fun aFolderThatCouldNotBeReadLeavesItOff() {
        assertFalse(OneDriveSignIn.turnsOnAutoBackup(forRestore = false, destination = null, lookup = failed))
    }

    @Test
    fun signingInToRestoreNeverTurnsItOn() {
        assertFalse(OneDriveSignIn.turnsOnAutoBackup(forRestore = true, destination = null, lookup = empty))
        assertFalse(OneDriveSignIn.turnsOnAutoBackup(forRestore = true, destination = null, lookup = null))
    }

    @Test
    fun backupsThereOrAnotherCloudChosenLeaveItOff() {
        val one = DriveBackupService.ListResult.Success(listOf(DriveBackupService.BackupInfo("1", "booxday_backup_2026-11-20_080000.zip", 1L, 10L)))
        assertFalse(OneDriveSignIn.turnsOnAutoBackup(forRestore = false, destination = null, lookup = one))
        assertFalse(OneDriveSignIn.turnsOnAutoBackup(forRestore = false, destination = BackupCloud.GOOGLE_DRIVE, lookup = empty))
    }
}

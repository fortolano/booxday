package com.boox.day.sync

import android.content.SharedPreferences

/** The Microsoft account OneDrive backup uses (owner decision 86): its MSAL id and address. */
data class OneDriveAccount(val msalAccountId: String, val address: String)

/**
 * A cloud and the account a backup is for (Nov 2026): a backup made for one is uploaded only while
 * it is still the same, never to a cloud or account chosen while it was being made.
 */
data class BackupTarget(val cloud: BackupCloud, val account: String?)

/**
 * Backup settings kept in Google's settings file ([GoogleAuthManager]), which is device-only: not
 * in AppSettings (exported in every backup) and left out of Android's own backup. OneDrive's
 * settings sit next to Google Drive's there (Nov 2026).
 */
internal object BackupPrefs {
    /** "google", "onedrive" or "none" (owner decision 85: one backup cloud at a time). */
    const val DESTINATION = "backup_destination"
    /** Google Drive's on/off switch, all older versions had; still written for one installed over this. */
    const val DRIVE_BACKUP_ENABLED = "drive_backup_enabled"
    const val BACKUP_FREQUENCY = "backup_frequency"
    const val DRIVE_LAST_BACKUP_TIME = "last_backup_time"
    const val ONEDRIVE_ACCOUNT_ID = "onedrive_msal_account_id"
    const val ONEDRIVE_ADDRESS = "onedrive_address"
    const val ONEDRIVE_LAST_BACKUP_TIME = "onedrive_last_backup_time"
    private const val NONE = "none"

    /**
     * Where automatic backup goes, or null for nowhere. Before Nov 2026 only Google Drive's switch
     * was saved: on reads back as Google Drive, with its frequency and last backup as they were.
     */
    fun destination(prefs: SharedPreferences): BackupCloud? {
        val stored = prefs.getString(DESTINATION, null)
            ?: return if (prefs.getBoolean(DRIVE_BACKUP_ENABLED, false)) BackupCloud.GOOGLE_DRIVE else null
        return BackupCloud.ofKey(stored)
    }

    fun putDestination(edit: SharedPreferences.Editor, cloud: BackupCloud?) {
        edit.putString(DESTINATION, cloud?.key ?: NONE)
        edit.putBoolean(DRIVE_BACKUP_ENABLED, cloud == BackupCloud.GOOGLE_DRIVE)
    }

    /**
     * The cloud automatic backup should back up to now, or null: only the chosen one, while it is
     * [connected], once the frequency (shared by both clouds) has passed since its own last backup.
     */
    fun dueCloud(prefs: SharedPreferences, connected: (BackupCloud) -> Boolean, now: Long): BackupCloud? {
        val cloud = destination(prefs) ?: return null
        val frequency = GoogleAuthManager.backupFrequencyOf(prefs.getString(BACKUP_FREQUENCY, null))
        if (frequency == GoogleAuthManager.FREQUENCY_MANUAL || !connected(cloud)) return null
        val last = prefs.getLong(lastBackupKey(cloud), 0L)
        return cloud.takeIf { now - last >= GoogleAuthManager.getFrequencyMillis(frequency) }
    }

    fun oneDriveAccount(prefs: SharedPreferences): OneDriveAccount? {
        val id = prefs.getString(ONEDRIVE_ACCOUNT_ID, null) ?: return null
        return OneDriveAccount(id, prefs.getString(ONEDRIVE_ADDRESS, null).orEmpty())
    }

    /** Saves [account], or forgets the account and its last backup time (null). */
    fun putOneDriveAccount(edit: SharedPreferences.Editor, account: OneDriveAccount?) {
        if (account == null) {
            edit.remove(ONEDRIVE_ACCOUNT_ID).remove(ONEDRIVE_ADDRESS).remove(ONEDRIVE_LAST_BACKUP_TIME)
        } else {
            edit.putString(ONEDRIVE_ACCOUNT_ID, account.msalAccountId).putString(ONEDRIVE_ADDRESS, account.address)
        }
    }

    /** Each cloud's own last backup, so a screen never shows the other cloud's. */
    fun lastBackupKey(cloud: BackupCloud): String = when (cloud) {
        BackupCloud.GOOGLE_DRIVE -> DRIVE_LAST_BACKUP_TIME
        BackupCloud.ONEDRIVE -> ONEDRIVE_LAST_BACKUP_TIME
    }
}

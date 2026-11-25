package com.boox.day.sync

import android.content.SharedPreferences

/** The Microsoft account OneDrive backup uses (owner decision 86): its MSAL id and address. */
data class OneDriveAccount(val msalAccountId: String, val address: String)

/**
 * Backup settings kept in Google's settings file ([GoogleAuthManager]), which is device-only: not
 * in AppSettings (exported in every backup) and left out of Android's own backup. OneDrive's
 * settings sit next to Google Drive's there (Nov 2026).
 */
internal object BackupPrefs {
    const val DRIVE_LAST_BACKUP_TIME = "last_backup_time"
    const val ONEDRIVE_ACCOUNT_ID = "onedrive_msal_account_id"
    const val ONEDRIVE_ADDRESS = "onedrive_address"
    const val ONEDRIVE_LAST_BACKUP_TIME = "onedrive_last_backup_time"

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

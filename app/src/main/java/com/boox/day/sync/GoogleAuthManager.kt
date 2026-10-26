package com.boox.day.sync

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.drive.DriveScopes
import com.boox.day.security.LazyEncryptedPrefs
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Google Authentication Manager for BooxDay
 * Handles OAuth 2.0 authentication for:
 * - Calendar sync ([CALENDAR_SCOPES]: read-write events, read the calendar list)
 * - Cloud backup (DRIVE_FILE scope for app-specific files)
 *
 * Architecture: Single sign-in with independent feature toggles.
 * User can enable/disable Calendar Sync and Drive Backup independently.
 */
class GoogleAuthManager(private val context: Context) {

    companion object {
        private const val TAG = "GoogleAuthManager"
        private const val PREFS_NAME = "boox_day_google_auth"

        // Least access sync needs (Oct 2026, for Google's OAuth verification): read and write
        // events on the user's calendars (events list/get/insert/update/delete) and read the
        // calendar list (calendarList list/get). The full "calendar" scope also allows sharing and
        // creating or deleting calendars, which BooxDay never did. Accounts that granted the old
        // scope must allow these once (Calendar Accounts → Reconnect).
        const val CALENDAR_LIST_READONLY = "https://www.googleapis.com/auth/calendar.calendarlist.readonly"
        val CALENDAR_SCOPES = listOf(CalendarScopes.CALENDAR_EVENTS, CALENDAR_LIST_READONLY)

        // Sync frequency options (shared for sync and backup)
        const val FREQUENCY_1_HOUR = "1 hour"
        const val FREQUENCY_2_HOURS = "2 hours"
        const val FREQUENCY_4_HOURS = "4 hours"
        const val FREQUENCY_8_HOURS = "8 hours"
        const val FREQUENCY_DAILY = "Daily"
        const val FREQUENCY_WEEKLY = "Weekly"
        const val FREQUENCY_MANUAL = "Manual"

        val FREQUENCY_OPTIONS = listOf(
            FREQUENCY_4_HOURS,
            FREQUENCY_8_HOURS,
            FREQUENCY_DAILY
        )

        val BACKUP_FREQUENCY_OPTIONS = listOf(
            FREQUENCY_DAILY,
            FREQUENCY_WEEKLY,
            FREQUENCY_MANUAL
        )

        fun getFrequencyMillis(frequency: String): Long {
            return when (frequency) {
                FREQUENCY_1_HOUR -> 1 * 60 * 60 * 1000L
                FREQUENCY_2_HOURS -> 2 * 60 * 60 * 1000L
                FREQUENCY_4_HOURS -> 4 * 60 * 60 * 1000L
                FREQUENCY_8_HOURS -> 8 * 60 * 60 * 1000L
                FREQUENCY_DAILY -> 24 * 60 * 60 * 1000L
                FREQUENCY_WEEKLY -> 7 * 24 * 60 * 60 * 1000L
                FREQUENCY_MANUAL -> Long.MAX_VALUE  // Never auto-trigger
                else -> 24 * 60 * 60 * 1000L
            }
        }
    }

    private val encryptedPrefs = LazyEncryptedPrefs(
        context = context.applicationContext,
        prefsName = PREFS_NAME,
        tag = TAG
    )

    private val googleSignInClient: GoogleSignInClient

    init {
        // Request both Calendar and Drive scopes in single sign-in
        // Features can be enabled/disabled independently after sign-in
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope(CalendarScopes.CALENDAR_EVENTS),
                Scope(CALENDAR_LIST_READONLY),
                Scope(DriveScopes.DRIVE_FILE)    // App-specific Drive files for backup
            )
            .build()

        googleSignInClient = GoogleSignIn.getClient(context, gso)
        Log.i(TAG, "GoogleAuthManager initialized with calendar events + calendar list + DRIVE_FILE scopes")
    }

    private fun prefsOrNull(): SharedPreferences? = encryptedPrefs.getOrNull()

    private inline fun editPrefs(block: SharedPreferences.Editor.() -> Unit) {
        val prefs = prefsOrNull() ?: return
        prefs.edit().apply {
            block()
            apply()
        }
    }

    private inline fun <T> readPrefs(
        defaultValue: T,
        block: SharedPreferences.() -> T
    ): T {
        val prefs = prefsOrNull() ?: return defaultValue
        return try {
            prefs.block()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to read Google auth preferences: ${t.message}", t)
            defaultValue
        }
    }

    /**
     * Get sign-in intent for OAuth flow
     */
    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    /**
     * Set selected account after OAuth sign-in
     */
    fun setSelectedAccount(account: GoogleSignInAccount) {
        val accountName = account.email ?: return
        editPrefs { putString("selected_account", accountName) }
        Log.i(TAG, "Account selected: $accountName")
    }

    /**
     * Check if user is currently signed in with proper scopes
     */
    fun isSignedIn(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return account != null && hasCalendarScope(account)
    }

    /**
     * Get signed-in user's email
     */
    fun getUserEmail(): String? {
        return GoogleSignIn.getLastSignedInAccount(context)?.email
    }

    /**
     * Get Google Account Credential for API calls
     * Uses [CALENDAR_SCOPES] (events read-write, calendar list read)
     */
    fun getCredential(): GoogleAccountCredential? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null

        return GoogleAccountCredential.usingOAuth2(
            context,
            CALENDAR_SCOPES
        ).apply {
            selectedAccount = account.account
        }
    }

    /**
     * Get Google Account Credential for a SPECIFIC account by email.
     *
     * Uses Android's AccountManager tokens (per-account), NOT GoogleSignIn state.
     * This allows multiple Google accounts to have valid credentials simultaneously.
     */
    fun getCredentialForAccount(email: String): GoogleAccountCredential {
        return GoogleAccountCredential.usingOAuth2(
            context,
            CALENDAR_SCOPES
        ).apply {
            selectedAccount = Account(email, "com.google")
        }
    }

    /**
     * Get sign-in intent that forces the account picker to appear.
     *
     * When a Google account is already signed in, the normal signInIntent auto-selects it.
     * This method signs out of the GoogleSignInClient first (clearing "last signed in" state)
     * so the picker shows all available accounts.
     *
     * Note: This does NOT invalidate OAuth tokens in Android's AccountManager.
     * Existing accounts retain their API credentials via getCredentialForAccount().
     */
    suspend fun getSignInIntentForNewAccount(): Intent {
        suspendCancellableCoroutine { cont ->
            googleSignInClient.signOut().addOnCompleteListener { cont.resume(Unit) }
        }
        Log.i(TAG, "Cleared GoogleSignIn state to force account picker")
        return googleSignInClient.signInIntent
    }

    /**
     * Sign out from Google account
     */
    fun signOut(onComplete: () -> Unit) {
        googleSignInClient.signOut().addOnCompleteListener {
            clearStoredData()
            Log.i(TAG, "Signed out successfully")
            onComplete()
        }
    }

    /**
     * Revoke all OAuth permissions and disconnect app from Google account.
     * This will force the consent screen to appear on next sign-in.
     * Use this when you need to re-show the consent screen for recording/testing.
     */
    fun revokeAccess(onComplete: () -> Unit) {
        googleSignInClient.revokeAccess().addOnCompleteListener {
            clearStoredData()
            Log.i(TAG, "Access revoked - consent screen will appear on next sign-in")
            onComplete()
        }
    }

    /**
     * Check if account has the required calendar scopes ([CALENDAR_SCOPES])
     */
    private fun hasCalendarScope(account: GoogleSignInAccount): Boolean {
        return GoogleSignIn.hasPermissions(
            account,
            *CALENDAR_SCOPES.map { Scope(it) }.toTypedArray()
        )
    }

    /**
     * Check if account has the required Drive scope
     */
    fun hasDriveScope(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return false
        return GoogleSignIn.hasPermissions(
            account,
            Scope(DriveScopes.DRIVE_FILE)
        )
    }

    /**
     * Get Google Account Credential for Drive API calls
     * Uses DRIVE_FILE scope for app-specific file access
     */
    fun getDriveCredential(): GoogleAccountCredential? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null

        return GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_FILE)
        ).apply {
            selectedAccount = account.account
        }
    }

    // ========== Sync Preferences ==========

    fun setSyncEnabled(enabled: Boolean) {
        editPrefs { putBoolean("sync_enabled", enabled) }
        Log.d(TAG, "Sync enabled: $enabled")
    }

    fun isSyncEnabled(): Boolean {
        return readPrefs(defaultValue = true) { getBoolean("sync_enabled", true) }
    }

    fun setSyncFrequency(frequency: String) {
        editPrefs { putString("sync_frequency", frequency) }
        Log.d(TAG, "Sync frequency set to: $frequency")
    }

    fun getSyncFrequency(): String {
        val stored = readPrefs(defaultValue = FREQUENCY_4_HOURS) {
            getString("sync_frequency", FREQUENCY_4_HOURS) ?: FREQUENCY_4_HOURS
        }
        return if (stored in FREQUENCY_OPTIONS ||
            stored == FREQUENCY_1_HOUR ||
            stored == FREQUENCY_2_HOURS
        ) {
            stored
        } else {
            FREQUENCY_4_HOURS
        }
    }

    fun setLastSyncTime(timestamp: Long) {
        editPrefs { putLong("last_sync_time", timestamp) }
    }

    fun getLastSyncTime(): Long {
        return readPrefs(defaultValue = 0L) { getLong("last_sync_time", 0L) }
    }

    fun setSelectedCalendars(calendarIds: Set<String>) {
        editPrefs { putStringSet("selected_calendars", calendarIds) }
        Log.d(TAG, "Selected calendars: ${calendarIds.size}")
    }

    fun getSelectedCalendars(): Set<String> {
        return readPrefs(defaultValue = emptySet()) {
            getStringSet("selected_calendars", emptySet()) ?: emptySet()
        }
    }

    /**
     * Store the default calendar for creating new events
     */
    fun setDefaultCalendarId(calendarId: String) {
        editPrefs { putString("default_calendar_id", calendarId) }
    }

    fun getDefaultCalendarId(): String? {
        return readPrefs<String?>(defaultValue = null) { getString("default_calendar_id", null) }
    }

    fun isAutoSyncConfigured(): Boolean {
        return isSignedIn() && isSyncEnabled()
    }

    /**
     * Check if enough time has passed for auto-sync
     */
    fun shouldAutoSync(): Boolean {
        if (!isAutoSyncConfigured()) return false

        val lastSync = getLastSyncTime()
        val frequency = getSyncFrequency()
        val intervalMillis = getFrequencyMillis(frequency)
        val elapsed = System.currentTimeMillis() - lastSync

        return elapsed >= intervalMillis
    }

    // ========== Drive Backup Preferences ==========

    /**
     * Enable/disable cloud backup to Google Drive.
     * Independent from Calendar sync - works even in Standalone mode.
     */
    fun setDriveBackupEnabled(enabled: Boolean) {
        editPrefs { putBoolean("drive_backup_enabled", enabled) }
        Log.d(TAG, "Drive backup enabled: $enabled")
    }

    fun isDriveBackupEnabled(): Boolean {
        return readPrefs(defaultValue = false) { getBoolean("drive_backup_enabled", false) }
    }

    fun setBackupFrequency(frequency: String) {
        editPrefs { putString("backup_frequency", frequency) }
        Log.d(TAG, "Backup frequency set to: $frequency")
    }

    fun getBackupFrequency(): String {
        val stored = readPrefs(defaultValue = FREQUENCY_WEEKLY) {
            getString("backup_frequency", FREQUENCY_WEEKLY) ?: FREQUENCY_WEEKLY
        }
        return if (stored in BACKUP_FREQUENCY_OPTIONS) stored else FREQUENCY_WEEKLY
    }

    fun setLastBackupTime(timestamp: Long) {
        editPrefs { putLong("last_backup_time", timestamp) }
    }

    fun getLastBackupTime(): Long {
        return readPrefs(defaultValue = 0L) { getLong("last_backup_time", 0L) }
    }

    /**
     * Store the Drive folder ID where backups are stored
     */
    fun setBackupFolderId(folderId: String?) {
        if (folderId != null) {
            editPrefs { putString("backup_folder_id", folderId) }
        } else {
            editPrefs { remove("backup_folder_id") }
        }
    }

    fun getBackupFolderId(): String? {
        return readPrefs<String?>(defaultValue = null) { getString("backup_folder_id", null) }
    }

    fun isAutoBackupConfigured(): Boolean {
        return isSignedIn() && hasDriveScope() && isDriveBackupEnabled()
    }

    /**
     * Check if enough time has passed for auto-backup
     */
    fun shouldAutoBackup(): Boolean {
        if (!isAutoBackupConfigured()) return false

        val frequency = getBackupFrequency()
        if (frequency == FREQUENCY_MANUAL) return false

        val lastBackup = getLastBackupTime()
        val intervalMillis = getFrequencyMillis(frequency)
        val elapsed = System.currentTimeMillis() - lastBackup

        return elapsed >= intervalMillis
    }

    /**
     * Set preferred backup type (JSON or FULL_DATABASE).
     * FULL_DATABASE includes complete SQLite with strokes and OCR data.
     * JSON is lighter but doesn't preserve OCR index.
     */
    fun setPreferredBackupType(type: BackupType) {
        editPrefs { putString("preferred_backup_type", type.name) }
        Log.d(TAG, "Preferred backup type set to: ${type.name}")
    }

    /**
     * Get preferred backup type. Defaults to JSON for backward compatibility.
     */
    fun getPreferredBackupType(): BackupType {
        val stored = readPrefs(defaultValue = BackupType.JSON.name) {
            getString("preferred_backup_type", BackupType.JSON.name) ?: BackupType.JSON.name
        }
        return try {
            BackupType.valueOf(stored)
        } catch (e: IllegalArgumentException) {
            BackupType.JSON
        }
    }

    /**
     * Clear all stored authentication data
     */
    private fun clearStoredData() {
        editPrefs { clear() }
        Log.d(TAG, "Cleared all stored auth data")
    }
}

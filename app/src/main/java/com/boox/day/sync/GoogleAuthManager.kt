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

        /** Keys tied to the Google account itself, cleared when its access is withdrawn. */
        private val SIGN_IN_KEYS = listOf(
            "selected_account", "selected_calendars", "default_calendar_id",
            "drive_backup_enabled", "backup_folder_id", "last_backup_time"
        )

        /**
         * Clears only what belongs to the Google sign-in (Nov 2026): this file also holds the calendar
         * sync switch and Sync frequency, which Outlook and CalDAV accounts use too, and the backup
         * choices. Clearing the whole file reset them when the last Google account was removed.
         */
        internal fun clearSignInData(prefs: SharedPreferences) {
            // OneDrive backup isn't Google's: it stays the destination, with its account and time
            val googleWasDestination = BackupPrefs.destination(prefs) == BackupCloud.GOOGLE_DRIVE
            prefs.edit().apply {
                for (key in SIGN_IN_KEYS) remove(key)
                if (googleWasDestination) remove(BackupPrefs.DESTINATION)
                apply()
            }
        }

        // Least access sync needs (Oct 2026, for Google's OAuth verification): read and write
        // events on the user's calendars (events list/get/insert/update/delete) and read the
        // calendar list (calendarList list/get). The full "calendar" scope also allows sharing and
        // creating or deleting calendars, which BooxDay never did. Accounts that granted the old
        // full scope (1.0.37 and older) keep working: Google gives them tokens for these without
        // asking again (Go 10.3, 26 Oct 2026: 8 calendars and 618 events synced, no prompt).
        const val CALENDAR_LIST_READONLY = "https://www.googleapis.com/auth/calendar.calendarlist.readonly"
        val CALENDAR_SCOPES = listOf(CalendarScopes.CALENDAR_EVENTS, CALENDAR_LIST_READONLY)

        /**
         * True when [granted] scopes cover calendar sync: the two narrow scopes, or the old full
         * calendar scope, which includes them. A sign-in record from an older version lists only
         * the full scope; requiring the narrow ones there turned isSignedIn() false, which switches
         * off pushing tablet events to Google, editing synced events and Drive backup.
         */
        fun coversCalendarAccess(granted: Collection<String>): Boolean =
            granted.containsAll(CALENDAR_SCOPES) || CalendarScopes.CALENDAR in granted

        // Sync frequency options (shared for sync and backup)
        const val FREQUENCY_1_HOUR = "1 hour"
        const val FREQUENCY_2_HOURS = "2 hours"
        const val FREQUENCY_4_HOURS = "4 hours"
        const val FREQUENCY_8_HOURS = "8 hours"
        const val FREQUENCY_DAILY = "Daily"
        const val FREQUENCY_WEEKLY = "Weekly"
        const val FREQUENCY_MONTHLY = "Monthly"
        const val FREQUENCY_MANUAL = "Manual"

        val FREQUENCY_OPTIONS = listOf(
            FREQUENCY_4_HOURS,
            FREQUENCY_8_HOURS,
            FREQUENCY_DAILY
        )

        // Monthly added Nov 2026: the backup screen offered it, but it wasn't in this list, so it read
        // back as Weekly (and the screen then showed Weekly)
        val BACKUP_FREQUENCY_OPTIONS = listOf(
            FREQUENCY_DAILY,
            FREQUENCY_WEEKLY,
            FREQUENCY_MONTHLY,
            FREQUENCY_MANUAL
        )

        /** The saved backup frequency, or Weekly when none is saved or it isn't one BooxDay knows. */
        internal fun backupFrequencyOf(stored: String?): String =
            if (stored in BACKUP_FREQUENCY_OPTIONS) stored!! else FREQUENCY_WEEKLY

        fun getFrequencyMillis(frequency: String): Long {
            return when (frequency) {
                FREQUENCY_1_HOUR -> 1 * 60 * 60 * 1000L
                FREQUENCY_2_HOURS -> 2 * 60 * 60 * 1000L
                FREQUENCY_4_HOURS -> 4 * 60 * 60 * 1000L
                FREQUENCY_8_HOURS -> 8 * 60 * 60 * 1000L
                FREQUENCY_DAILY -> 24 * 60 * 60 * 1000L
                FREQUENCY_WEEKLY -> 7 * 24 * 60 * 60 * 1000L
                FREQUENCY_MONTHLY -> 30 * 24 * 60 * 60 * 1000L
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
     * Check if account has calendar access ([coversCalendarAccess]: the narrow scopes, or the old
     * full scope from an older version's sign-in)
     */
    private fun hasCalendarScope(account: GoogleSignInAccount): Boolean {
        return coversCalendarAccess(account.grantedScopes.map { it.scopeUri })
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
     * Where automatic backup goes (owner decision 85, Nov 2026): Google Drive, OneDrive or nowhere
     * (null). Independent from Calendar sync - works even in Standalone mode.
     */
    fun getBackupDestination(): BackupCloud? = readPrefs<BackupCloud?>(defaultValue = null) { BackupPrefs.destination(this) }

    /** Choosing one cloud stops the other's automatic backup; its backups stay where they are. */
    fun setBackupDestination(cloud: BackupCloud?) {
        editPrefs { BackupPrefs.putDestination(this, cloud) }
        runCatching { DriveAutoBackupWorker.syncSchedule(context, cloud != null) }
        Log.d(TAG, "Backup destination: ${cloud?.key ?: "none"}")
    }

    /** Google Drive as the destination on, or off when it is the destination (OneDrive's is left alone). */
    fun setDriveBackupEnabled(enabled: Boolean) {
        if (enabled) {
            setBackupDestination(BackupCloud.GOOGLE_DRIVE)
        } else if (getBackupDestination() == BackupCloud.GOOGLE_DRIVE) {
            setBackupDestination(null)
        }
    }

    fun isDriveBackupEnabled(): Boolean = getBackupDestination() == BackupCloud.GOOGLE_DRIVE

    fun setBackupFrequency(frequency: String) {
        editPrefs { putString("backup_frequency", frequency) }
        Log.d(TAG, "Backup frequency set to: $frequency")
    }

    fun getBackupFrequency(): String {
        return backupFrequencyOf(readPrefs<String?>(defaultValue = null) { getString("backup_frequency", null) })
    }

    fun setLastBackupTime(timestamp: Long) {
        editPrefs { putLong("last_backup_time", timestamp) }
    }

    fun getLastBackupTime(): Long {
        return readPrefs(defaultValue = 0L) { getLong("last_backup_time", 0L) }
    }

    /** [cloud]'s own last backup time (Google Drive's is the one above). */
    fun getLastBackupTime(cloud: BackupCloud): Long =
        readPrefs(defaultValue = 0L) { getLong(BackupPrefs.lastBackupKey(cloud), 0L) }

    fun setLastBackupTime(cloud: BackupCloud, timestamp: Long) {
        editPrefs { putLong(BackupPrefs.lastBackupKey(cloud), timestamp) }
    }

    /** The Microsoft account OneDrive backup uses (owner decision 86), or null when none is connected. */
    fun getOneDriveAccount(): OneDriveAccount? = readPrefs<OneDriveAccount?>(defaultValue = null) { BackupPrefs.oneDriveAccount(this) }

    /** Saves the OneDrive backup account; null forgets it (never signs it out of Microsoft). */
    fun setOneDriveAccount(account: OneDriveAccount?) {
        editPrefs { BackupPrefs.putOneDriveAccount(this, account) }
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

    /** True while [cloud] can take a backup: Google signed in with Drive access, or a OneDrive account kept. */
    fun isConnectedForBackup(cloud: BackupCloud): Boolean = when (cloud) {
        BackupCloud.GOOGLE_DRIVE -> isSignedIn() && hasDriveScope()
        BackupCloud.ONEDRIVE -> getOneDriveAccount() != null
    }

    /** The cloud an automatic backup is due for now, or null ([BackupPrefs.dueCloud]). */
    fun dueAutoBackupCloud(): BackupCloud? = readPrefs<BackupCloud?>(defaultValue = null) {
        BackupPrefs.dueCloud(this, ::isConnectedForBackup, System.currentTimeMillis())
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
        prefsOrNull()?.let { clearSignInData(it) }
        Log.d(TAG, "Cleared stored Google sign-in data")
    }
}

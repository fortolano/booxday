package com.boox.day.sync

import android.content.Context
import android.content.SharedPreferences
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.CalendarEventDao
import com.boox.day.data.db.ConnectedAccountDao
import com.boox.day.data.db.ConnectedAccountEntity
import com.boox.day.data.model.AccountType
import com.boox.day.data.model.ConnectedAccount
import com.boox.day.data.model.ProviderId
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helper for post-migration data fixes.
 *
 * After database migration v17→v18:
 * - New columns exist: providerId, accountId, accountEmail
 * - Existing events have: providerId="GOOGLE", accountId="", accountEmail=""
 * - We need to populate accountId and accountEmail for existing events
 *
 * ## Why isn't this done in the migration?
 *
 * The Room migration runs BEFORE app code and can only use SQL.
 * We need [GoogleAuthManager] to get the current user's email, which
 * requires the app to be running.
 *
 * ## Idempotency
 *
 * This migration fix is designed to be idempotent:
 * - Uses SharedPreferences flag to track completion
 * - The SQL UPDATE only affects events where accountId is empty
 * - Running multiple times produces the same result
 *
 * ## Usage
 *
 * Call [migrateExistingGoogleEvents] from Application.onCreate() or
 * when the app first needs sync functionality. It's async and non-blocking.
 */
object CalendarMigrationHelper {

    private const val TAG = "CalendarMigrationHelper"
    private const val PREFS_NAME = "calendar_migration"
    private const val KEY_V18_MIGRATED = "v18_events_migrated"

    /**
     * Run ONCE after v17→v18 migration to tag existing Google events
     * with account information.
     *
     * Safe to call multiple times - uses SharedPreferences flag to ensure
     * it only runs once.
     *
     * @param context Application context
     * @param googleAuthManager Google authentication manager to get current user
     * @param accountDao DAO for connected accounts
     * @param eventDao DAO for calendar events
     */
    suspend fun migrateExistingGoogleEvents(
        context: Context,
        googleAuthManager: GoogleAuthManager,
        accountDao: ConnectedAccountDao,
        eventDao: CalendarEventDao
    ) = withContext(Dispatchers.IO) {
        val prefs = getPrefs(context)

        // Check if already migrated
        if (prefs.getBoolean(KEY_V18_MIGRATED, false)) {
            Log.d(TAG, "v18 migration already completed, skipping")
            return@withContext
        }

        Log.i(TAG, "Starting v18 events migration...")

        try {
            // Check how many events need migration
            val unmigratedCount = eventDao.countUnmigratedEvents()
            Log.d(TAG, "Found $unmigratedCount events without account info")

            // If user was never signed in, there's nothing to migrate
            if (!googleAuthManager.isSignedIn()) {
                Log.i(TAG, "User not signed in to Google, marking migration complete")
                markMigrationComplete(prefs)
                return@withContext
            }

            // Get user's email from Google
            val email = googleAuthManager.getUserEmail()
            if (email == null) {
                Log.w(TAG, "Could not get Google user email, marking migration complete")
                markMigrationComplete(prefs)
                return@withContext
            }

            // Find or create account record for this user
            val accountId = findOrCreateGoogleAccount(
                email = email,
                googleAuthManager = googleAuthManager,
                accountDao = accountDao
            )

            if (accountId == null) {
                Log.e(TAG, "Failed to create account record for $email")
                // Don't mark as complete - will retry next launch
                return@withContext
            }

            // Update all events with empty accountId
            if (unmigratedCount > 0) {
                Log.i(TAG, "Updating $unmigratedCount events with provider info: accountId=$accountId, email=$email")
                eventDao.updateProviderInfo(
                    providerId = ProviderId.GOOGLE.key,
                    accountId = accountId,
                    accountEmail = email
                )

                // Verify update
                val remainingCount = eventDao.countUnmigratedEvents()
                Log.i(TAG, "Migration complete. Remaining unmigrated events: $remainingCount")
            } else {
                Log.i(TAG, "No events to migrate")
            }

            // Mark migration as complete
            markMigrationComplete(prefs)
            Log.i(TAG, "v18 migration completed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error during v18 migration", e)
            // Don't mark as complete on error - will retry next launch
        }
    }

    /**
     * Find existing account record or create a new one for the Google user.
     *
     * @return Account ID (nanoid) or null if failed
     */
    private suspend fun findOrCreateGoogleAccount(
        email: String,
        googleAuthManager: GoogleAuthManager,
        accountDao: ConnectedAccountDao
    ): String? = withContext(Dispatchers.IO) {
        try {
            // Check if account already exists
            val existingAccount = accountDao.getByEmail(email)
            if (existingAccount != null) {
                Log.d(TAG, "Found existing account record for $email: ${existingAccount.id}")
                return@withContext existingAccount.id
            }

            // Determine account type based on email domain
            val accountType = when {
                email.endsWith("@gmail.com") -> AccountType.GOOGLE_PERSONAL
                email.endsWith("@googlemail.com") -> AccountType.GOOGLE_PERSONAL
                else -> AccountType.GOOGLE_WORKSPACE
            }

            // Create new account record
            val newAccount = ConnectedAccountEntity(
                id = ConnectedAccount.create(
                    providerId = ProviderId.GOOGLE,
                    accountType = accountType,
                    email = email
                ).id,
                providerId = ProviderId.GOOGLE.key,
                accountType = accountType.key,
                email = email,
                displayName = null, // Will be populated on next sign-in
                isActive = true,
                createdAt = System.currentTimeMillis(),
                lastSyncAt = googleAuthManager.getLastSyncTime().takeIf { it > 0 }
            )

            accountDao.upsert(newAccount)
            Log.i(TAG, "Created account record for $email: ${newAccount.id}")

            newAccount.id

        } catch (e: Exception) {
            Log.e(TAG, "Error finding/creating account for $email", e)
            null
        }
    }

    /**
     * Mark migration as complete in SharedPreferences.
     */
    private fun markMigrationComplete(prefs: SharedPreferences) {
        prefs.edit().putBoolean(KEY_V18_MIGRATED, true).apply()
    }

    /**
     * Get migration preferences.
     */
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Reset migration flag (for testing only).
     * Allows the migration to run again.
     */
    fun resetMigrationFlag(context: Context) {
        getPrefs(context).edit().remove(KEY_V18_MIGRATED).apply()
        Log.w(TAG, "Migration flag reset - will run again on next launch")
    }

    /**
     * Check if migration has been completed.
     */
    fun isMigrationComplete(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_V18_MIGRATED, false)
    }

    /**
     * Get count of events still needing migration.
     * Useful for debugging.
     */
    suspend fun getUnmigratedEventCount(context: Context): Int = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            db.calendarEventDao().countUnmigratedEvents()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting unmigrated event count", e)
            -1
        }
    }
}

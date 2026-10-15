package com.boox.day.sync

import android.content.Context
import com.boox.day.data.db.AppDatabase
import android.util.Log

/**
 * Detects the current backup/restore state to determine appropriate UI.
 * Used by DataBackupDialog to show context-appropriate options.
 *
 * State detection flow:
 * 1. Check if backup is already enabled locally
 * 2. Check if local database is essentially empty (fresh install)
 * 3. Check if existing backups exist in Google Drive
 * 4. Return combined state for UI decision-making
 *
 * UI State Matrix:
 * - Fresh Install + Backups Exist → Restore-first UI (prominent restore, secondary overwrite)
 * - Has Data + Backups Exist → Warning UI (must confirm before overwrite)
 * - No Backups → Normal "Enable Backup" UI
 * - Backup Already Enabled → Normal backup/restore UI
 */
class BackupStateDetector(
    private val context: Context,
    private val driveBackupService: DriveBackupService
) {
    companion object {
        private const val TAG = "BackupStateDetector"
    }

    private val authManager = GoogleAuthManager(context)

    /**
     * Combined state for UI decision-making.
     *
     * @property hasExistingBackups True if backups exist in Google Drive
     * @property existingBackupInfo Summary of existing backups (null if none)
     * @property isLocalDatabaseFresh True if user has not created meaningful content yet
     * @property isBackupEnabled True if backup is already enabled locally
     */
    data class BackupState(
        val hasExistingBackups: Boolean,
        val existingBackupInfo: DriveBackupService.ExistingBackupSummary?,
        val isLocalDatabaseFresh: Boolean,
        val isBackupEnabled: Boolean
    ) {
        /**
         * Determine the UI state for the DataBackupDialog.
         */
        val uiState: UIState
            get() = when {
                isBackupEnabled -> UIState.BACKUP_ENABLED
                !hasExistingBackups -> UIState.NO_BACKUPS
                isLocalDatabaseFresh -> UIState.FRESH_INSTALL_WITH_BACKUPS
                else -> UIState.HAS_DATA_WITH_BACKUPS
            }
    }

    /**
     * UI states for DataBackupDialog rendering.
     */
    enum class UIState {
        /** User has no meaningful data, backups exist → show restore-first UI */
        FRESH_INSTALL_WITH_BACKUPS,
        /** User has data, backups exist → show warning before overwrite */
        HAS_DATA_WITH_BACKUPS,
        /** No backups exist → show normal "Enable Backup" UI */
        NO_BACKUPS,
        /** Backup already enabled → show normal backup/restore UI */
        BACKUP_ENABLED
    }

    /**
     * Determine current state after user signs in.
     * Called when DataBackupDialog loads with signed-in user.
     *
     * This method performs:
     * 1. Quick check if backup is already enabled (skip Drive API calls)
     * 2. Local database check (fast, no network)
     * 3. Drive backup existence check (may require folder discovery)
     * 4. Optional: Get backup summary for UI display
     */
    suspend fun detectState(): BackupState {
        Log.i(TAG, "Detecting backup state...")

        // Quick check: if backup is already enabled, skip expensive checks
        val isBackupEnabled = authManager.isDriveBackupEnabled()
        if (isBackupEnabled) {
            Log.i(TAG, "Backup already enabled, returning early")
            return BackupState(
                hasExistingBackups = true, // Assume true since backup is enabled
                existingBackupInfo = null, // No need to fetch details
                isLocalDatabaseFresh = false, // Irrelevant when backup enabled
                isBackupEnabled = true
            )
        }

        // Check local database state (fast, no network)
        val isLocalDatabaseFresh = isDatabaseFreshInstall()
        Log.d(TAG, "Local database fresh: $isLocalDatabaseFresh")

        // Check for existing backups in Drive
        val hasExistingBackups = driveBackupService.hasExistingBackups()
        Log.d(TAG, "Has existing backups: $hasExistingBackups")

        // If backups exist, get summary for UI display
        val existingBackupInfo = if (hasExistingBackups) {
            driveBackupService.getExistingBackupSummary()
        } else {
            null
        }

        val state = BackupState(
            hasExistingBackups = hasExistingBackups,
            existingBackupInfo = existingBackupInfo,
            isLocalDatabaseFresh = isLocalDatabaseFresh,
            isBackupEnabled = false
        )

        Log.i(TAG, "Detected state: ${state.uiState}")
        return state
    }

    /**
     * Check if local database is essentially empty (fresh install).
     * Returns true if user has not created meaningful content yet.
     *
     * Criteria for "fresh":
     * - 1 or fewer pages (default page may exist)
     * - No strokes (no handwriting)
     * - No tasks
     *
     * Note: This checks current state, not history. If user created
     * content and deleted it, counts would be 0 (intentional behavior).
     */
    suspend fun isDatabaseFreshInstall(): Boolean {
        return try {
            val db = AppDatabase.getDatabase(context)

            // Check page count (allow 1 default page)
            val pageCount = countTable(db, "page")
            if (pageCount > 1) {
                Log.d(TAG, "Not fresh: $pageCount pages")
                return false
            }

            // Check stroke count (handwriting indicates usage)
            val strokeCount = countTable(db, "stroke")
            if (strokeCount > 0) {
                Log.d(TAG, "Not fresh: $strokeCount strokes")
                return false
            }

            // Check task count
            val taskCount = countTable(db, "task")
            if (taskCount > 0) {
                Log.d(TAG, "Not fresh: $taskCount tasks")
                return false
            }

            // All checks passed - database is fresh
            Log.d(TAG, "Database is fresh: pages=$pageCount, strokes=$strokeCount, tasks=$taskCount")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking database state: ${e.message}", e)
            // On error, assume not fresh (safer default)
            false
        }
    }

    /**
     * Count rows in a table using raw SQL query.
     */
    private fun countTable(db: AppDatabase, tableName: String): Int {
        return try {
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $tableName").use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to count $tableName: ${e.message}")
            0
        }
    }
}

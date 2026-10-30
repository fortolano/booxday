package com.boox.day.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.boox.day.data.model.AccountType
import com.boox.day.data.model.ConnectedAccount
import com.boox.day.data.model.ProviderId
import kotlinx.coroutines.flow.Flow

/**
 * Room entity representing a connected calendar account.
 * Stores Google and Outlook account connections for multi-provider sync.
 *
 * @see ConnectedAccount for the domain model
 */
@Entity(
    tableName = "connected_accounts",
    indices = [
        // v30: one account per provider + server + email. Until v29 the email alone was unique,
        // so an iCloud (CalDAV) account whose Apple ID is a Gmail address could not sit next to
        // the Google account of the same address.
        Index(value = ["providerId", "serverUrl", "email"], unique = true),
        Index(value = ["providerId"])
    ]
)
data class ConnectedAccountEntity(
    @PrimaryKey
    val id: String,
    val providerId: String,         // "GOOGLE" or "OUTLOOK"
    val accountType: String,        // "GOOGLE_PERSONAL", etc.
    val email: String,
    val displayName: String?,
    val isActive: Boolean = true,
    val createdAt: Long,
    val lastSyncAt: Long? = null,
    /** CalDAV server address (v30); "" for Google and Outlook. Not NULL: SQLite treats NULLs as distinct in unique indexes. */
    @ColumnInfo(defaultValue = "")
    val serverUrl: String = ""
) {
    /**
     * Convert entity to domain model.
     */
    fun toDomainModel(): ConnectedAccount = ConnectedAccount(
        id = id,
        providerId = ProviderId.fromKey(providerId),
        accountType = AccountType.fromKey(accountType),
        email = email,
        displayName = displayName,
        isActive = isActive,
        createdAt = createdAt,
        lastSyncAt = lastSyncAt
    )

    /** Null for a row of a provider or account type this version doesn't know (never read as Google). */
    fun toDomainModelOrNull(): ConnectedAccount? {
        val provider = ProviderId.fromKeyOrNull(providerId) ?: return null
        val type = AccountType.fromKeyOrNull(accountType)?.takeIf { it.providerId == provider } ?: return null
        return ConnectedAccount(
            id = id,
            providerId = provider,
            accountType = type,
            email = email,
            displayName = displayName,
            isActive = isActive,
            createdAt = createdAt,
            lastSyncAt = lastSyncAt
        )
    }

    companion object {
        /**
         * Create entity from domain model.
         */
        fun fromDomainModel(account: ConnectedAccount): ConnectedAccountEntity =
            ConnectedAccountEntity(
                id = account.id,
                providerId = account.providerId.key,
                accountType = account.accountType.key,
                email = account.email,
                displayName = account.displayName,
                isActive = account.isActive,
                createdAt = account.createdAt,
                lastSyncAt = account.lastSyncAt
            )
    }
}

/**
 * DAO for connected accounts.
 */
@Dao
interface ConnectedAccountDao {
    /**
     * Get all active accounts as a reactive Flow.
     */
    @Query("SELECT * FROM connected_accounts WHERE isActive = 1 ORDER BY createdAt ASC")
    fun getActiveAccountsFlow(): Flow<List<ConnectedAccountEntity>>

    /**
     * Get all active accounts (non-reactive).
     */
    @Query("SELECT * FROM connected_accounts WHERE isActive = 1 ORDER BY createdAt ASC")
    suspend fun getActiveAccounts(): List<ConnectedAccountEntity>

    /**
     * Get all accounts for a specific provider.
     */
    @Query("SELECT * FROM connected_accounts WHERE providerId = :providerId AND isActive = 1 ORDER BY createdAt ASC")
    suspend fun getAccountsByProvider(providerId: String): List<ConnectedAccountEntity>

    /**
     * Get account by ID.
     */
    @Query("SELECT * FROM connected_accounts WHERE id = :accountId")
    suspend fun getById(accountId: String): ConnectedAccountEntity?

    /**
     * The active account of one provider (and server, "" except for CalDAV) with this email.
     * Scoped by provider since v30: by email alone, signing in to Outlook with the address of a
     * connected Google account found (and then updated) the Google row.
     */
    @Query("SELECT * FROM connected_accounts WHERE providerId = :providerId AND serverUrl = :serverUrl AND email = :email AND isActive = 1")
    suspend fun getByEmail(providerId: String, serverUrl: String, email: String): ConnectedAccountEntity?

    /**
     * Insert or update an account.
     *
     * An existing id is UPDATEd in place, never REPLACEd: REPLACE deletes the row first, and
     * calendar_selections cascade from it, so signing in again with a connected account wiped its
     * calendar on/off choices (Oct 2026). The same provider + server + email under another id is a
     * removed (inactive) account being connected again with a new id: that old row goes first, as
     * REPLACE did (its old choices with it); a plain upsert would hit the unique index
     * (providerId, serverUrl, email) and then update nothing. Accounts of other providers with the
     * same email are left alone (until v30 the delete matched the email only).
     */
    @Transaction
    suspend fun upsert(account: ConnectedAccountEntity) {
        deleteOtherAccount(account.providerId, account.serverUrl, account.email, account.id)
        upsertRow(account)
    }

    @Query("DELETE FROM connected_accounts WHERE providerId = :providerId AND serverUrl = :serverUrl AND email = :email AND id != :id")
    suspend fun deleteOtherAccount(providerId: String, serverUrl: String, email: String, id: String)

    /** Use [upsert]. */
    @Upsert
    suspend fun upsertRow(account: ConnectedAccountEntity)

    /**
     * Soft delete - deactivate account (keeps record for reference).
     */
    @Query("UPDATE connected_accounts SET isActive = 0 WHERE id = :accountId")
    suspend fun deactivate(accountId: String)

    /**
     * Hard delete by email (for cleanup).
     */
    @Query("DELETE FROM connected_accounts WHERE email = :email")
    suspend fun deleteByEmail(email: String)

    /**
     * "Remove account" (Oct 2026): deletes the row, so the email and name don't stay on the tablet;
     * its calendar_selections go with it (ON DELETE CASCADE). Removing used to only [deactivate].
     */
    @Query("DELETE FROM connected_accounts WHERE id = :accountId")
    suspend fun deleteById(accountId: String)

    /** Accounts left inactive by "Remove account" before Oct 2026. No query reads inactive rows. */
    @Query("DELETE FROM connected_accounts WHERE isActive = 0")
    suspend fun deleteInactive()

    /**
     * Update last sync timestamp.
     */
    @Query("UPDATE connected_accounts SET lastSyncAt = :timestamp WHERE id = :accountId")
    suspend fun updateLastSync(accountId: String, timestamp: Long)

    /**
     * Check if any accounts exist for a provider.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM connected_accounts WHERE providerId = :providerId AND isActive = 1 LIMIT 1)")
    suspend fun hasAccountsForProvider(providerId: String): Boolean

    /**
     * Count active accounts.
     */
    @Query("SELECT COUNT(*) FROM connected_accounts WHERE isActive = 1")
    suspend fun countActiveAccounts(): Int
}

package com.boox.day.data.db

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
        Index(value = ["email"], unique = true),
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
    val lastSyncAt: Long? = null
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
     * Get account by email.
     */
    @Query("SELECT * FROM connected_accounts WHERE email = :email AND isActive = 1")
    suspend fun getByEmail(email: String): ConnectedAccountEntity?

    /**
     * Insert or update an account.
     *
     * An existing id is UPDATEd in place, never REPLACEd: REPLACE deletes the row first, and
     * calendar_selections cascade from it, so signing in again with a connected account wiped its
     * calendar on/off choices (Oct 2026). The same email under another id is a removed (inactive)
     * account being connected again with a new id: that old row goes first, as REPLACE did (its
     * old choices with it); a plain upsert would hit the unique email index and then update nothing.
     */
    @Transaction
    suspend fun upsert(account: ConnectedAccountEntity) {
        deleteOtherAccountWithEmail(account.email, account.id)
        upsertRow(account)
    }

    @Query("DELETE FROM connected_accounts WHERE email = :email AND id != :id")
    suspend fun deleteOtherAccountWithEmail(email: String, id: String)

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

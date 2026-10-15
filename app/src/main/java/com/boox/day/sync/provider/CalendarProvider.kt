package com.boox.day.sync.provider

import android.app.Activity
import android.content.Intent
import com.boox.day.data.model.AccountType
import com.boox.day.data.model.ConnectedAccount
import com.boox.day.data.model.ProviderId
import com.boox.day.data.model.ProviderCalendar
import com.boox.day.data.model.ProviderEvent
import com.boox.day.sync.NewEventData
import com.boox.day.sync.UpdateEventData
import java.time.LocalDate

/**
 * Provider-agnostic interface for calendar providers.
 *
 * This interface defines the contract that all calendar providers (Google, Outlook, etc.)
 * must implement to participate in the multi-provider sync system.
 *
 * ## Implementation Notes
 *
 * ### Authentication Patterns
 * - **Google**: Uses Intent-based flow (getSignInIntent → handleSignInResult)
 * - **Outlook/MSAL**: Uses callback-based flow (getSignInIntent returns null, use signInInteractive)
 *
 * ### Error Handling
 * All operations return Kotlin's [Result] type. Implementations should:
 * - Return [Result.success] with data on success
 * - Return [Result.failure] with descriptive exception on error
 * - Handle token refresh internally before returning auth errors
 *
 * @see GoogleCalendarProvider for Google Calendar implementation
 * @see OutlookCalendarProvider for Outlook/Office365 implementation
 */
interface CalendarProvider {

    /**
     * The provider identifier for this implementation.
     */
    val providerId: ProviderId

    // ═══════════════════════════════════════════════════════════════════════════
    // Account Management
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get all currently signed-in accounts for this provider.
     *
     * @return List of connected accounts (may be empty if none signed in)
     */
    suspend fun getSignedInAccounts(): List<ConnectedAccount>

    /**
     * Get an Intent to start the sign-in flow.
     *
     * For Intent-based providers (Google), this returns an Intent to launch.
     * For callback-based providers (Outlook/MSAL), this returns null and
     * [signInInteractive] should be used instead.
     *
     * @param activity The activity to launch the sign-in from
     * @param accountType The type of account to sign in (e.g., GOOGLE_PERSONAL, OUTLOOK_WORK_SCHOOL)
     * @return Intent to launch, or null if provider uses callback-based auth
     */
    fun getSignInIntent(activity: Activity, accountType: AccountType): Intent?

    /**
     * Handle the result of an Intent-based sign-in flow.
     *
     * @param data The Intent data from onActivityResult
     * @return Result containing the new ConnectedAccount, or failure
     */
    suspend fun handleSignInResult(data: Intent?): Result<ConnectedAccount>

    /**
     * Perform interactive sign-in for callback-based providers (MSAL).
     *
     * For Intent-based providers (Google), this is a no-op that returns failure.
     *
     * @param activity The activity context for displaying the sign-in UI
     * @param accountType The type of account to sign in
     * @return Result containing the new ConnectedAccount, or failure
     */
    suspend fun signInInteractive(activity: Activity, accountType: AccountType): Result<ConnectedAccount>

    /**
     * Remove an account from this provider.
     *
     * This should:
     * 1. Sign out from the provider (revoke tokens if possible)
     * 2. NOT delete database records (caller handles that)
     *
     * @param accountId The account ID to remove
     * @return Result indicating success or failure
     */
    suspend fun removeAccount(accountId: String): Result<Unit>

    // ═══════════════════════════════════════════════════════════════════════════
    // Calendar Operations
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Fetch all calendars available for an account.
     *
     * @param accountId The account to fetch calendars for
     * @return Result containing list of calendars, or failure
     */
    suspend fun fetchCalendars(accountId: String): Result<List<ProviderCalendar>>

    /**
     * Fetch events from specified calendars within a date range.
     *
     * @param accountId The account to fetch events for
     * @param calendarIds Set of calendar IDs to fetch events from
     * @param startDate Start of date range (inclusive)
     * @param endDate End of date range (inclusive)
     * @return Result containing list of events, or failure
     */
    suspend fun fetchEvents(
        accountId: String,
        calendarIds: Set<String>,
        startDate: LocalDate,
        endDate: LocalDate
    ): Result<List<ProviderEvent>>

    // ═══════════════════════════════════════════════════════════════════════════
    // CRUD Operations
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Create a new event in a calendar.
     *
     * @param accountId The account containing the calendar
     * @param calendarId The calendar to create the event in
     * @param event The event data
     * @return Result containing the created event (with provider ID), or failure
     */
    suspend fun createEvent(
        accountId: String,
        calendarId: String,
        event: NewEventData
    ): Result<ProviderEvent>

    /**
     * Update an existing event.
     *
     * @param accountId The account containing the calendar
     * @param calendarId The calendar containing the event
     * @param eventId The provider's event ID
     * @param event The updated event data
     * @return Result containing the updated event, or failure
     */
    suspend fun updateEvent(
        accountId: String,
        calendarId: String,
        eventId: String,
        event: UpdateEventData
    ): Result<ProviderEvent>

    /**
     * Delete an event.
     *
     * @param accountId The account containing the calendar
     * @param calendarId The calendar containing the event
     * @param eventId The provider's event ID
     * @return Result indicating success or failure
     */
    suspend fun deleteEvent(
        accountId: String,
        calendarId: String,
        eventId: String
    ): Result<Unit>

    // ═══════════════════════════════════════════════════════════════════════════
    // Token Management
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Refresh the access token if needed.
     *
     * Implementations should check if the token is expired or close to expiry
     * and refresh it proactively.
     *
     * @param accountId The account to refresh the token for
     * @return Result indicating success (token valid or refreshed) or failure
     */
    suspend fun refreshTokenIfNeeded(accountId: String): Result<Unit>

    /**
     * Check if an account's credentials are still valid.
     *
     * This is a quick check that doesn't make network calls.
     * Use [refreshTokenIfNeeded] for a more thorough check with refresh.
     *
     * @param accountId The account to check
     * @return true if the account appears to have valid credentials
     */
    fun isAccountValid(accountId: String): Boolean
}

/**
 * Exception thrown when a calendar provider operation fails.
 */
sealed class CalendarProviderException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    /**
     * Authentication failed or token expired.
     */
    class AuthenticationException(
        message: String = "Authentication failed",
        cause: Throwable? = null
    ) : CalendarProviderException(message, cause)

    /**
     * Network error during operation.
     */
    class NetworkException(
        message: String = "Network error",
        cause: Throwable? = null
    ) : CalendarProviderException(message, cause)

    /**
     * Permission denied for the requested operation.
     */
    class PermissionException(
        message: String = "Permission denied",
        cause: Throwable? = null
    ) : CalendarProviderException(message, cause)

    /**
     * Resource not found (calendar or event deleted).
     */
    class NotFoundException(
        message: String = "Resource not found",
        cause: Throwable? = null
    ) : CalendarProviderException(message, cause)

    /**
     * Rate limited by the provider.
     */
    class RateLimitException(
        message: String = "Rate limited",
        val retryAfterMs: Long? = null,
        cause: Throwable? = null
    ) : CalendarProviderException(message, cause)

    /**
     * User cancelled the operation.
     */
    class CancelledException(
        message: String = "Operation cancelled",
        cause: Throwable? = null
    ) : CalendarProviderException(message, cause)

    /**
     * General provider error.
     */
    class ProviderException(
        message: String,
        val providerErrorCode: String? = null,
        cause: Throwable? = null
    ) : CalendarProviderException(message, cause)

    /**
     * Some provider work succeeded, but at least one calendar failed.
     * Carries any successfully fetched events so callers can still finish
     * downstream processing while surfacing a degraded sync result.
     */
    class PartialSyncException(
        val syncResult: com.boox.day.sync.SyncResult.PartialSuccess,
        val partialEvents: List<ProviderEvent> = emptyList()
    ) : CalendarProviderException(syncResult.getDisplayMessage())

    /**
     * Check if this exception indicates an auth error that could be resolved by re-authentication.
     */
    val needsReauthentication: Boolean
        get() = this is AuthenticationException

    /**
     * Check if this exception is recoverable (transient error).
     */
    val isRecoverable: Boolean
        get() = when (this) {
            is NetworkException -> true
            is RateLimitException -> true
            else -> false
        }
}

/**
 * Result of an operation that may need re-authentication.
 */
sealed class AuthAwareResult<out T> {
    data class Success<T>(val data: T) : AuthAwareResult<T>()
    data class NeedsReauth(val accountId: String, val message: String) : AuthAwareResult<Nothing>()
    data class Error(val exception: CalendarProviderException) : AuthAwareResult<Nothing>()
}

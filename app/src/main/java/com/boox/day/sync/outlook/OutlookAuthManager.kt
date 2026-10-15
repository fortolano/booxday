package com.boox.day.sync.outlook

import android.app.Activity
import android.content.Context
import com.boox.day.R
import com.boox.day.data.model.AccountType
import com.boox.day.data.model.ConnectedAccount
import com.boox.day.data.model.ProviderId
import com.microsoft.identity.client.AcquireTokenParameters
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IMultipleAccountPublicClientApplication
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.client.exception.MsalUiRequiredException
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Manages Microsoft/Outlook authentication using MSAL (Microsoft Authentication Library).
 *
 * This class handles:
 * - MSAL initialization with multiple account support
 * - Interactive sign-in for both personal (Outlook.com) and work/school accounts
 * - Silent token acquisition for subsequent API calls
 * - Account management (listing, removing accounts)
 *
 * ## Usage Pattern
 * 1. Call [initialize] once at app startup
 * 2. Use [signInInteractive] to add new accounts
 * 3. Use [getAccessToken] to get tokens for API calls
 * 4. Use [removeAccount] to sign out individual accounts
 *
 * ## Thread Safety
 * All methods are suspend functions that handle threading internally.
 * MSAL callbacks are bridged to coroutines using suspendCancellableCoroutine.
 *
 * @param context Application context (not activity context)
 */
class OutlookAuthManager(private val context: Context) {

    companion object {
        private const val TAG = "OutlookAuthManager"

        /** OAuth scopes requested for Outlook/Graph API access. */
        private val SCOPES = arrayOf("User.Read", "Calendars.Read", "Calendars.ReadWrite")

        /** Authority URL for personal Microsoft accounts (Outlook.com, Hotmail, Live). */
        const val AUTHORITY_PERSONAL = "https://login.microsoftonline.com/consumers"

        /** Authority URL for work/school Office 365 accounts. */
        const val AUTHORITY_WORK_SCHOOL = "https://login.microsoftonline.com/organizations"
    }

    /** The MSAL public client application instance. */
    private var msalApp: IMultipleAccountPublicClientApplication? = null

    /** Whether MSAL has been successfully initialized. */
    @Volatile
    private var isInitialized = false

    /** Initialization error message, if any. */
    var initializationError: String? = null
        private set

    // ═══════════════════════════════════════════════════════════════════════════
    // Initialization
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Initialize MSAL for multi-account operation.
     *
     * This must be called before any other methods. Safe to call multiple times;
     * subsequent calls are no-ops if already initialized.
     *
     * @return Result.success if initialized, Result.failure with exception otherwise
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.Main) {
        if (isInitialized && msalApp != null) {
            return@withContext Result.success(Unit)
        }

        try {
            val app = suspendCancellableCoroutine<IMultipleAccountPublicClientApplication> { continuation ->
                PublicClientApplication.createMultipleAccountPublicClientApplication(
                    context,
                    R.raw.auth_config,
                    object : IPublicClientApplication.IMultipleAccountApplicationCreatedListener {
                        override fun onCreated(application: IMultipleAccountPublicClientApplication) {
                            Log.i(TAG, "MSAL (Multi-Account) initialized successfully")
                            continuation.resume(application)
                        }

                        override fun onError(exception: MsalException) {
                            Log.e(TAG, "MSAL initialization failed: ${exception.message}", exception)
                            continuation.resumeWithException(exception)
                        }
                    }
                )
            }

            msalApp = app
            isInitialized = true
            initializationError = null
            Result.success(Unit)

        } catch (e: Exception) {
            val errorMsg = "MSAL initialization failed: ${e.message}"
            Log.e(TAG, errorMsg, e)
            initializationError = errorMsg
            isInitialized = false
            Result.failure(e)
        }
    }

    /**
     * Check if MSAL is initialized and ready.
     */
    fun isReady(): Boolean = isInitialized && msalApp != null

    // ═══════════════════════════════════════════════════════════════════════════
    // Authentication
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Perform interactive sign-in to add a new Outlook account.
     *
     * This launches the system browser (Chrome Custom Tabs) for the user to
     * authenticate with Microsoft. The result is returned via the callback
     * mechanism bridged to a coroutine.
     *
     * @param activity The activity to launch the sign-in from
     * @param accountType The type of account (OUTLOOK_PERSONAL or OUTLOOK_WORK_SCHOOL)
     * @return Result containing the authenticated account info, or failure
     */
    suspend fun signInInteractive(
        activity: Activity,
        accountType: AccountType
    ): Result<OutlookAuthResult> = withContext(Dispatchers.Main) {
        val app = msalApp ?: return@withContext Result.failure(
            IllegalStateException("MSAL not initialized. Call initialize() first.")
        )

        if (accountType.providerId != ProviderId.OUTLOOK) {
            return@withContext Result.failure(
                IllegalArgumentException("Invalid account type for Outlook: ${accountType.key}")
            )
        }

        val authority = accountType.authority ?: AUTHORITY_PERSONAL

        try {
            val authResult = suspendCancellableCoroutine<IAuthenticationResult> { continuation ->
                val params = AcquireTokenParameters.Builder()
                    .startAuthorizationFromActivity(activity)
                    .withScopes(SCOPES.toList())
                    .fromAuthority(authority)
                    .withCallback(object : AuthenticationCallback {
                        override fun onSuccess(authenticationResult: IAuthenticationResult) {
                            Log.i(TAG, "Token acquired successfully for ${authenticationResult.account.username}")
                            continuation.resume(authenticationResult)
                        }

                        override fun onError(exception: MsalException) {
                            Log.e(TAG, "Token acquisition failed: ${exception.message}", exception)
                            continuation.resumeWithException(exception)
                        }

                        override fun onCancel() {
                            Log.d(TAG, "Sign-in cancelled by user")
                            continuation.resumeWithException(
                                SignInCancelledException("Sign-in cancelled by user")
                            )
                        }
                    })
                    .build()

                app.acquireToken(params)
            }

            val result = OutlookAuthResult(
                msalAccountId = authResult.account.id,
                email = authResult.account.username ?: "Unknown",
                displayName = null, // Will be fetched via Graph API later
                accountType = accountType,
                accessToken = authResult.accessToken
            )

            Log.i(TAG, "Sign-in successful: ${result.email}")
            Result.success(result)

        } catch (e: SignInCancelledException) {
            Result.failure(e)
        } catch (e: MsalException) {
            Log.e(TAG, "MSAL error during sign-in: ${e.errorCode} - ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during sign-in", e)
            Result.failure(e)
        }
    }

    /**
     * Get an access token for an account (silently if possible).
     *
     * This attempts to get a cached token or refresh the token silently.
     * If the token cannot be acquired silently (e.g., requires re-authentication),
     * returns failure with MsalUiRequiredException.
     *
     * @param msalAccountId The MSAL account identifier
     * @return Result containing the access token string, or failure
     */
    suspend fun getAccessToken(msalAccountId: String): Result<String> = withContext(Dispatchers.IO) {
        val app = msalApp ?: return@withContext Result.failure(
            IllegalStateException("MSAL not initialized")
        )

        try {
            // Find the MSAL account
            val msalAccount = app.accounts.find { it.id == msalAccountId }
                ?: return@withContext Result.failure(
                    AccountNotFoundException("Account not found: $msalAccountId")
                )

            // Try to acquire token silently
            val authResult = acquireTokenSilent(msalAccount)
            Result.success(authResult.accessToken)

        } catch (e: MsalUiRequiredException) {
            Log.w(TAG, "Silent token acquisition failed - UI required: ${e.message}")
            Result.failure(e)
        } catch (e: MsalException) {
            Log.e(TAG, "Token acquisition error: ${e.errorCode} - ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error getting access token", e)
            Result.failure(e)
        }
    }

    /**
     * Acquire token silently for an MSAL account.
     */
    private suspend fun acquireTokenSilent(account: IAccount): IAuthenticationResult {
        val app = msalApp ?: throw IllegalStateException("MSAL not initialized")

        return suspendCancellableCoroutine { continuation ->
            val params = AcquireTokenSilentParameters.Builder()
                .forAccount(account)
                .fromAuthority(account.authority)
                .withScopes(SCOPES.toList())
                .withCallback(object : AuthenticationCallback {
                    override fun onSuccess(authenticationResult: IAuthenticationResult) {
                        continuation.resume(authenticationResult)
                    }

                    override fun onError(exception: MsalException) {
                        continuation.resumeWithException(exception)
                    }

                    override fun onCancel() {
                        continuation.resumeWithException(
                            SignInCancelledException("Silent auth cancelled")
                        )
                    }
                })
                .build()

            app.acquireTokenSilentAsync(params)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Account Management
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get all MSAL accounts currently signed in.
     *
     * @return List of MSAL accounts (may be empty)
     */
    suspend fun getMsalAccounts(): List<IAccount> = withContext(Dispatchers.IO) {
        msalApp?.accounts ?: emptyList()
    }

    /**
     * Check if an account exists in MSAL cache.
     *
     * @param msalAccountId The MSAL account identifier
     * @return true if account exists
     */
    suspend fun hasAccount(msalAccountId: String): Boolean = withContext(Dispatchers.IO) {
        msalApp?.accounts?.any { it.id == msalAccountId } ?: false
    }

    /**
     * Remove an account from MSAL.
     *
     * This signs out the user from this app only (doesn't affect other apps
     * or the browser session).
     *
     * @param msalAccountId The MSAL account identifier to remove
     * @return Result indicating success or failure
     */
    suspend fun removeAccount(msalAccountId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val app = msalApp ?: return@withContext Result.failure(
            IllegalStateException("MSAL not initialized")
        )

        try {
            val msalAccount = app.accounts.find { it.id == msalAccountId }
            if (msalAccount != null) {
                // MSAL removeAccount is synchronous
                app.removeAccount(msalAccount)
                Log.i(TAG, "Removed account: ${msalAccount.username}")
            } else {
                Log.w(TAG, "Account not found for removal: $msalAccountId")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing account: $msalAccountId", e)
            Result.failure(e)
        }
    }

    /**
     * Remove all accounts from MSAL (sign out completely).
     *
     * @return Result indicating success or failure
     */
    suspend fun signOutAll(): Result<Unit> = withContext(Dispatchers.IO) {
        val app = msalApp ?: return@withContext Result.failure(
            IllegalStateException("MSAL not initialized")
        )

        try {
            val accounts = app.accounts.toList()
            for (account in accounts) {
                try {
                    app.removeAccount(account)
                    Log.d(TAG, "Removed account: ${account.username}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing account ${account.username}", e)
                    // Continue removing other accounts
                }
            }
            Log.i(TAG, "Signed out of all Outlook accounts")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error during sign out all", e)
            Result.failure(e)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get the authority URL for an account type.
     */
    fun getAuthorityForAccountType(accountType: AccountType): String {
        return accountType.authority ?: when (accountType) {
            AccountType.OUTLOOK_PERSONAL -> AUTHORITY_PERSONAL
            AccountType.OUTLOOK_WORK_SCHOOL -> AUTHORITY_WORK_SCHOOL
            else -> AUTHORITY_PERSONAL
        }
    }
}

/**
 * Result of a successful Outlook authentication.
 */
data class OutlookAuthResult(
    val msalAccountId: String,
    val email: String,
    val displayName: String?,
    val accountType: AccountType,
    val accessToken: String
) {
    /**
     * Create a ConnectedAccount domain model from this auth result.
     */
    fun toConnectedAccount(): ConnectedAccount = ConnectedAccount.create(
        providerId = ProviderId.OUTLOOK,
        accountType = accountType,
        email = email,
        displayName = displayName
    )
}

/**
 * Exception thrown when user cancels sign-in.
 */
class SignInCancelledException(message: String) : Exception(message)

/**
 * Exception thrown when account is not found in MSAL cache.
 */
class AccountNotFoundException(message: String) : Exception(message)

package com.boox.day.sync.provider

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.room.withTransaction
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.CalDavAccount
import com.boox.day.data.db.CalDavCalendar
import com.boox.day.data.db.ConnectedAccountEntity
import com.boox.day.data.model.AccountType
import com.boox.day.data.model.ConnectedAccount
import com.boox.day.data.model.ProviderCalendar
import com.boox.day.data.model.ProviderEvent
import com.boox.day.data.model.ProviderId
import com.boox.day.security.AccountSecretStore
import com.boox.day.sync.NewEventData
import com.boox.day.sync.SyncResult
import com.boox.day.sync.UpdateEventData
import com.boox.day.sync.caldav.CalDavConnection
import com.boox.day.sync.caldav.CalDavEditor
import com.boox.day.sync.caldav.CalDavReader
import com.boox.day.sync.caldav.CalDavWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * CalDAV calendars (iCloud, Fastmail, Nextcloud, any https server) as a [CalendarProvider] (plan
 * stage 3, Oct 2026), so the calendar sync, Calendar Accounts and background sync treat them like
 * Google and Outlook.
 *
 * - Signing in is a form (name, app password, address), not a browser flow: [signIn], not
 *   [getSignInIntent] / [signInInteractive]. The password goes to [AccountSecretStore], never the
 *   database; what discovery found goes to caldav_account.
 * - The account's email is its sign-in name, and its serverUrl the address it signed in with
 *   (accounts are unique per provider + server + name).
 * - A calendar's id is its URL path (caldav_calendar.href).
 * - Reading: [CalDavReader]. The sync manager deletes window events a provider didn't return, so a
 *   calendar that failed makes the fetch a [CalendarProviderException.PartialSyncException].
 * - Writing: [CalDavWriter] (step 3.5): only BooxDay's fields change in the file, each write guarded
 *   by its change tag; one day of a series changes or goes alone.
 */
class CalDavCalendarProvider internal constructor(
    private val db: AppDatabase,
    private val secrets: AccountSecretStore,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val now: () -> Long = System::currentTimeMillis
) : CalendarProvider {

    companion object {
        private const val TAG = "CalDavCalendarProvider"

        fun create(context: Context): CalDavCalendarProvider {
            val appContext = context.applicationContext
            return CalDavCalendarProvider(AppDatabase.getDatabase(appContext), AccountSecretStore.get(appContext))
        }
    }

    override val providerId: ProviderId = ProviderId.CALDAV

    private val accountDao = db.connectedAccountDao()
    private val selectionDao = db.calendarSelectionDao()
    private val calDavDao = db.calDavDao()
    private val reader = CalDavReader(db, now)
    private val writer = CalDavWriter(db, now)

    // ═══════════════════════════════════════════════════════════════════════════
    // Account Management
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Signs in to a CalDAV server and saves the account, or fails with a [CalDavConnection.Failure]
     * the sign-in screen can explain. [address] is the ready-made one for iCloud and Fastmail.
     * [trustedCertSha256] is the fingerprint the user chose to trust after an
     * [CalDavConnection.Failure.UntrustedCertificate].
     *
     * Signing in again to the same server with the same name keeps the account (its id and
     * calendar choices) and replaces its password: that is "sign in again".
     */
    internal suspend fun signIn(
        accountType: AccountType,
        address: String,
        username: String,
        password: String,
        trustedCertSha256: String? = null
    ): Result<ConnectedAccount> = withContext(Dispatchers.IO) {
        try {
            require(accountType.providerId == ProviderId.CALDAV) { "Not a CalDAV account type: ${accountType.key}" }
            val name = username.trim()
            val found = CalDavConnection(name, password, trustedCertSha256).discover(address)
            // One spelling per server ("Example.com" = "https://example.com/"), so typing it
            // differently signs in to the same account instead of adding a second one
            val server = address.trim().let { if ("://" in it) it else "https://$it" }.toHttpUrlOrNull()?.toString() ?: address.trim()

            val existing = accountDao.getByEmail(ProviderId.CALDAV.key, server, name)
            val account = existing?.toDomainModel()?.copy(accountType = accountType, isActive = true)
                ?: ConnectedAccount.create(ProviderId.CALDAV, accountType, name, serverUrl = server)

            // The password first: without it the account couldn't sync, so it isn't saved either
            secrets.save(account.id, password).getOrThrow()
            try {
                db.withTransaction {
                    accountDao.upsert(ConnectedAccountEntity.fromDomainModel(account))
                    calDavDao.upsertAccount(
                        CalDavAccount(account.id, found.principalUrl, found.calendarHomeUrl, name, trustedCertSha256)
                    )
                    for (calendar in found.calendars) {
                        calDavDao.upsertCalendar(CalDavCalendar(account.id, calendar.href, calendar.ctag, calendar.syncToken))
                    }
                }
            } catch (e: Exception) {
                if (existing == null) secrets.delete(account.id)
                throw e
            }
            Log.i(TAG, "CalDAV account saved (${accountType.key}, ${found.calendars.size} calendars)")
            Result.success(account)
        } catch (e: Exception) {
            Log.w(TAG, "CalDAV sign-in failed: ${e.javaClass.simpleName}")
            Result.failure(e)
        }
    }

    override suspend fun getSignedInAccounts(): List<ConnectedAccount> = withContext(Dispatchers.IO) {
        accountDao.getAccountsByProvider(ProviderId.CALDAV.key)
            .filter { secrets.has(it.id) }
            .mapNotNull { it.toDomainModelOrNull() }
    }

    /** CalDAV signs in with a form: [signIn]. */
    override fun getSignInIntent(activity: Activity, accountType: AccountType): Intent? = null

    override suspend fun handleSignInResult(data: Intent?): Result<ConnectedAccount> =
        Result.failure(CalendarProviderException.ProviderException("CalDAV signs in with signIn()"))

    override suspend fun signInInteractive(activity: Activity, accountType: AccountType): Result<ConnectedAccount> =
        Result.failure(CalendarProviderException.ProviderException("CalDAV signs in with signIn()"))

    /** Forgets the password; the caller deletes the account row, and its CalDAV rows go with it (CASCADE). */
    override suspend fun removeAccount(accountId: String): Result<Unit> = withContext(Dispatchers.IO) {
        secrets.delete(accountId)
        Result.success(Unit)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Calendar Operations
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * The account's event calendars now. Calendars no longer on the server are forgotten with their
     * files (the sync manager takes them off the account's selection).
     */
    override suspend fun fetchCalendars(accountId: String): Result<List<ProviderCalendar>> = withContext(Dispatchers.IO) {
        try {
            val (connection, account) = connect(accountId)
            val calendars = listCalendars(connection, account)
            Result.success(calendars.map { it.toProviderCalendar() })
        } catch (e: Exception) {
            Result.failure(providerFailure(e))
        }
    }

    override suspend fun fetchEvents(
        accountId: String,
        calendarIds: Set<String>,
        startDate: LocalDate,
        endDate: LocalDate
    ): Result<List<ProviderEvent>> = withContext(Dispatchers.IO) {
        try {
            val (connection, account) = connect(accountId)
            val listed = listCalendars(connection, account).associateBy { it.href }

            val events = mutableListOf<ProviderEvent>()
            val gone = calendarIds.filter { it !in listed }.toMutableList()
            val errors = mutableListOf<Exception>()
            var read = 0
            for (href in calendarIds) {
                val calendar = listed[href] ?: continue
                try {
                    when (val outcome = reader.read(
                        connection, accountId, account.calendarHomeUrl, href, calendar.readOnly, startDate, endDate, zone()
                    )) {
                        is CalDavReader.Outcome.Read -> {
                            read++
                            events += outcome.events.map { it.copy(colorHex = calendar.colorHex) }
                        }
                        CalDavReader.Outcome.Gone -> gone += href
                    }
                } catch (e: Exception) {
                    // Sign-in problems end the whole fetch: every calendar would fail the same way
                    if (e is CalDavConnection.Failure.WrongPassword || e is CalDavConnection.Failure.UntrustedCertificate) throw e
                    Log.w(TAG, "CalDAV calendar not read: ${e.javaClass.simpleName}")
                    errors += e
                }
            }

            if (gone.isNotEmpty()) {
                // Removed on the server: off the selection, as a deleted Google calendar is
                selectionDao.deleteByCalendarIds(accountId, gone)
                Log.w(TAG, "Removed ${gone.size} CalDAV calendar(s) deleted on the server")
            }
            when {
                errors.isEmpty() -> {
                    accountDao.updateLastSync(accountId, now())
                    Result.success(events)
                }
                read == 0 -> Result.failure(providerFailure(errors.first()))
                else -> Result.failure(
                    CalendarProviderException.PartialSyncException(
                        SyncResult.PartialSuccess(
                            successCount = read,
                            failureCount = errors.size,
                            errors = errors.map { it.message ?: it.javaClass.simpleName },
                            deletedCalendarIds = gone
                        ),
                        events
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(providerFailure(e))
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CRUD Operations (step 3.5: CalDavWriter, guarded by each file's change tag)
    // ═══════════════════════════════════════════════════════════════════════════

    override suspend fun createEvent(accountId: String, calendarId: String, event: NewEventData): Result<ProviderEvent> =
        write(accountId, calendarId) { connection, account ->
            writer.create(connection, accountId, account.calendarHomeUrl, calendarId, fieldsOf(
                event.title, event.date, event.startTimeHour, event.startTimeMinute, event.endTimeHour, event.endTimeMinute,
                event.isAllDay, event.location, event.description
            ), zone())
        }

    override suspend fun updateEvent(
        accountId: String,
        calendarId: String,
        eventId: String,
        event: UpdateEventData
    ): Result<ProviderEvent> =
        write(accountId, calendarId) { connection, account ->
            writer.update(connection, accountId, account.calendarHomeUrl, calendarId, eventId, fieldsOf(
                event.title, event.date, event.startTimeHour, event.startTimeMinute, event.endTimeHour, event.endTimeMinute,
                event.isAllDay, event.location, event.description
            ), zone())
        }

    override suspend fun deleteEvent(accountId: String, calendarId: String, eventId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val (connection, account) = connect(accountId)
                writer.delete(connection, accountId, account.calendarHomeUrl, calendarId, eventId)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.w(TAG, "CalDAV delete failed: ${e.javaClass.simpleName}")
                Result.failure(providerFailure(e))
            }
        }

    /** A write that returns the event's row, in its calendar's colour. */
    private suspend fun write(
        accountId: String,
        calendarId: String,
        block: suspend (CalDavConnection, CalDavAccount) -> ProviderEvent
    ): Result<ProviderEvent> = withContext(Dispatchers.IO) {
        try {
            val (connection, account) = connect(accountId)
            val row = block(connection, account)
            val colour = selectionDao.getByAccountAndCalendarId(accountId, calendarId)?.calendarColor
            Result.success(row.copy(colorHex = colour))
        } catch (e: Exception) {
            Log.w(TAG, "CalDAV write failed: ${e.javaClass.simpleName}")
            Result.failure(providerFailure(e))
        }
    }

    private fun fieldsOf(
        title: String, date: String, startHour: Int, startMinute: Int, endHour: Int?, endMinute: Int?,
        allDay: Boolean, location: String?, description: String?
    ) = CalDavEditor.Fields(
        title = title,
        date = LocalDate.parse(date),
        startTime = LocalTime.of(startHour, startMinute),
        endTime = if (endHour != null && endMinute != null) LocalTime.of(endHour, endMinute) else null,
        isAllDay = allDay,
        location = location,
        description = description
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // Token Management
    // ═══════════════════════════════════════════════════════════════════════════

    /** No tokens: the password is either saved or the account needs signing in again. */
    override suspend fun refreshTokenIfNeeded(accountId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (secrets.has(accountId)) Result.success(Unit)
        else Result.failure(CalendarProviderException.AuthenticationException("Sign in again"))
    }

    override fun isAccountValid(accountId: String): Boolean = secrets.has(accountId)

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private fun connect(accountId: String): Pair<CalDavConnection, CalDavAccount> {
        val account = calDavDao.getAccount(accountId)
            ?: throw CalendarProviderException.AuthenticationException("Sign in again")
        // Unreadable (Keystore reset, or restored on another tablet): the user types it again
        val password = secrets.read(accountId)
            ?: throw CalendarProviderException.AuthenticationException("Sign in again")
        return CalDavConnection(account.username, password, account.pinnedCertSha256) to account
    }

    /** The server's calendar list; calendars that left it are forgotten with their files. */
    private suspend fun listCalendars(connection: CalDavConnection, account: CalDavAccount): List<CalDavConnection.RemoteCalendar> {
        val calendars = connection.listCalendars(account.calendarHomeUrl)
        val listed = calendars.map { it.href }.toSet()
        db.withTransaction {
            val stored = calDavDao.getCalendars(account.accountId).map { it.href }
            for (calendar in calendars) {
                val kept = calDavDao.getCalendar(account.accountId, calendar.href)
                calDavDao.upsertCalendar(
                    CalDavCalendar(account.accountId, calendar.href, calendar.ctag ?: kept?.ctag, calendar.syncToken ?: kept?.syncToken)
                )
            }
            for (href in stored.filter { it !in listed }) {
                calDavDao.deleteResourcesOf(account.accountId, href)
                calDavDao.deleteCalendar(account.accountId, href)
            }
        }
        return calendars
    }

    private fun CalDavConnection.RemoteCalendar.toProviderCalendar() = ProviderCalendar(
        id = href,
        name = name,
        color = colorHex,
        isPrimary = false,  // CalDAV has no main calendar: the sync manager makes the first the default
        isWritable = !readOnly,
        accessRole = if (readOnly) "reader" else "writer"
    )

    /** The sync manager's error kinds: a refused password needs Reconnect, no connection waits. */
    private fun providerFailure(e: Exception): CalendarProviderException = when (e) {
        is CalendarProviderException -> e
        is CalDavConnection.Failure.WrongPassword -> CalendarProviderException.AuthenticationException("The server refused the password", e)
        // A certificate that changed (a home server's new one) is trusted again by signing in again
        is CalDavConnection.Failure.UntrustedCertificate -> CalendarProviderException.AuthenticationException("The server's certificate changed", e)
        is CalDavConnection.Failure.Offline -> CalendarProviderException.NetworkException("No connection", e)
        is CalDavWriter.EventGone -> CalendarProviderException.NotFoundException("The event is no longer on the server", e)
        is CalDavConnection.Failure.Server -> when (e.code) {
            403 -> CalendarProviderException.PermissionException("The server refused access", e)
            // Changed on the server twice while saving (CalDavWriter reads it again once)
            412 -> CalendarProviderException.ProviderException("It was changed elsewhere at the same time. Try again.", "412", e)
            404, 410 -> CalendarProviderException.NotFoundException("Not found on the server", e)
            429, 503 -> CalendarProviderException.RateLimitException("The server is busy", cause = e)
            else -> CalendarProviderException.ProviderException("The server answered ${e.code}", e.code.toString(), e)
        }
        else -> CalendarProviderException.ProviderException(e.message ?: e.javaClass.simpleName, cause = e)
    }
}

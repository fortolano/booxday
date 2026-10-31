package com.boox.day.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Query
import androidx.room.Upsert

/*
 * CalDAV calendar accounts (plan stage 3, step 3.2, Oct 2026; v32). Device-only like every account
 * table: never backed up (sign-in is per device, sync/BackupSchema). The password is not here: it is
 * in security/AccountSecretStore (encrypted, out of backups). Removing the account removes all of it
 * (CASCADE from connected_accounts).
 */

/** Where an account's calendars live, found by discovery, and the server certificate the user trusted. */
@Entity(
    tableName = "caldav_account",
    foreignKeys = [ForeignKey(
        entity = ConnectedAccountEntity::class,
        parentColumns = ["id"],
        childColumns = ["accountId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class CalDavAccount(
    @androidx.room.PrimaryKey val accountId: String,
    /** The user's principal URL (RFC 5397 current-user-principal). */
    val principalUrl: String,
    /** The collection holding the account's calendars (RFC 4791 calendar-home-set). */
    val calendarHomeUrl: String,
    /** The sign-in name (iCloud: the Apple ID; may not be an email elsewhere). */
    val username: String,
    /** SHA-256 of the server certificate the user chose to trust ("Trust This Server"), or null for a normally trusted one. */
    val pinnedCertSha256: String? = null
)

/** One calendar collection's change markers, so an unchanged calendar costs one small request. */
@Entity(
    tableName = "caldav_calendar",
    primaryKeys = ["accountId", "href"],
    foreignKeys = [ForeignKey(
        entity = ConnectedAccountEntity::class,
        parentColumns = ["id"],
        childColumns = ["accountId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class CalDavCalendar(
    val accountId: String,
    /** The collection's URL path: also its calendarId in calendar_selections and calendar_events. */
    val href: String,
    /** CalendarServer's getctag: changes with any change in the calendar. */
    val ctag: String? = null,
    /** RFC 6578 sync-token, where the server has one. */
    val syncToken: String? = null
)

/**
 * One event file as the server holds it. Kept whole, so an edit on the tablet changes only BooxDay's
 * fields and leaves everything else (alarms, attendees, other apps' data) as it was; the etag guards
 * every write ("If-Match"). Its occurrences in the window are rows of calendar_events.
 */
@Entity(
    tableName = "caldav_resource",
    primaryKeys = ["accountId", "href"],
    foreignKeys = [ForeignKey(
        entity = ConnectedAccountEntity::class,
        parentColumns = ["id"],
        childColumns = ["accountId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["accountId", "calendarHref"])]
)
data class CalDavResource(
    val accountId: String,
    /** The file's URL path. */
    val href: String,
    val calendarHref: String,
    val etag: String?,
    /** The iCalendar text as last read from (or written to) the server. */
    val ics: String,
    /** The event's UID (one file holds one event and its exceptions). */
    val uid: String?,
    val syncedAt: Long
)

@Dao
interface CalDavDao {
    @Upsert
    fun upsertAccount(account: CalDavAccount)

    @Query("SELECT * FROM caldav_account WHERE accountId = :accountId")
    fun getAccount(accountId: String): CalDavAccount?

    @Upsert
    fun upsertCalendar(calendar: CalDavCalendar)

    @Query("SELECT * FROM caldav_calendar WHERE accountId = :accountId AND href = :href")
    fun getCalendar(accountId: String, href: String): CalDavCalendar?

    @Query("DELETE FROM caldav_calendar WHERE accountId = :accountId AND href = :href")
    fun deleteCalendar(accountId: String, href: String)

    @Upsert
    fun upsertResource(resource: CalDavResource)

    @Query("SELECT * FROM caldav_resource WHERE accountId = :accountId AND href = :href")
    fun getResource(accountId: String, href: String): CalDavResource?

    @Query("SELECT * FROM caldav_resource WHERE accountId = :accountId AND calendarHref = :calendarHref")
    fun getResources(accountId: String, calendarHref: String): List<CalDavResource>

    @Query("DELETE FROM caldav_resource WHERE accountId = :accountId AND href = :href")
    fun deleteResource(accountId: String, href: String)

    @Query("DELETE FROM caldav_resource WHERE accountId = :accountId AND calendarHref = :calendarHref")
    fun deleteResourcesOf(accountId: String, calendarHref: String)
}

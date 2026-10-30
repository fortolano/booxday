package com.boox.day.data.db

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A calendar account's on/off choices (calendar_selections) hang off its row in connected_accounts
 * (foreign key ON DELETE CASCADE). Signing in again with a connected account saves its row again,
 * which must keep those choices; only deleting the account may remove them (Oct 2026: saving used
 * REPLACE, which deletes the row first and so wiped them).
 *
 * Runs on the JVM (Robolectric) against the real Room database and DAOs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35, 36], application = Application::class)
class ConnectedAccountSelectionSurvivalTest {

    private lateinit var db: AppDatabase
    private lateinit var accounts: ConnectedAccountDao
    private lateinit var selections: CalendarSelectionDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accounts = db.connectedAccountDao()
        selections = db.calendarSelectionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun signingInAgainKeepsTheCalendarChoices() = runBlocking {
        accounts.upsert(account("acc-1", "me@example.com", displayName = "Me"))
        selections.upsertAll(listOf(selection("acc-1", "work", enabled = false), selection("acc-1", "family", enabled = true)))

        // What GoogleCalendarProvider.handleSignInResult does for an account that is already connected
        val existing = accounts.getByEmail("GOOGLE", "", "me@example.com")!!
        accounts.upsert(existing.copy(displayName = "Me (renamed)", isActive = true))

        assertEquals(
            mapOf("work" to false, "family" to true),
            selections.getByAccountId("acc-1").associate { it.calendarId to it.isFetchEnabled }
        )
        assertEquals("Me (renamed)", accounts.getById("acc-1")!!.displayName)
    }

    @Test
    fun reconnectingARemovedAccountSavesTheNewRecord() = runBlocking {
        accounts.upsert(account("old-id", "me@example.com"))
        selections.upsertAll(listOf(selection("old-id", "work", enabled = true)))
        accounts.deactivate("old-id") // How "Remove account" left it before Oct 2026

        // Sign-in finds no active account for the email and creates a new one (new random id)
        accounts.upsert(account("new-id", "me@example.com"))

        val saved = accounts.getByEmail("GOOGLE", "", "me@example.com")
        assertEquals("new-id", saved?.id)
        assertTrue(saved!!.isActive)
        assertEquals(null, accounts.getById("old-id"))
        assertTrue("the removed account's old choices go with it, as before", selections.getByAccountId("old-id").isEmpty())
    }

    @Test
    fun deletingTheAccountStillRemovesItsChoices() = runBlocking {
        accounts.upsert(account("acc-1", "me@example.com"))
        selections.upsertAll(listOf(selection("acc-1", "work", enabled = true)))

        accounts.deleteByEmail("me@example.com")

        assertTrue(selections.getByAccountId("acc-1").isEmpty())
    }

    @Test
    fun removingAnAccountDeletesItsRecordAndChoices() = runBlocking {
        accounts.upsert(account("acc-1", "me@example.com", displayName = "Me"))
        accounts.upsert(account("acc-2", "other@example.com"))
        selections.upsertAll(listOf(selection("acc-1", "work", enabled = true), selection("acc-2", "home", enabled = true)))

        // What MultiAccountSyncManager.removeAccount does since Oct 2026
        accounts.deleteById("acc-1")

        assertEquals("no email or name left on the tablet", null, accounts.getById("acc-1"))
        assertTrue(selections.getByAccountId("acc-1").isEmpty())
        assertEquals("other accounts are untouched", 1, selections.getByAccountId("acc-2").size)
        assertEquals("other@example.com", accounts.getById("acc-2")?.email)
    }

    @Test
    fun startupCleanupDeletesAccountsLeftInactive() = runBlocking {
        accounts.upsert(account("removed", "old@example.com"))
        accounts.upsert(account("kept", "me@example.com"))
        selections.upsertAll(listOf(selection("removed", "work", enabled = true), selection("kept", "home", enabled = true)))
        accounts.deactivate("removed") // Left by "Remove account" before Oct 2026

        accounts.deleteInactive()

        assertEquals(null, accounts.getById("removed"))
        assertTrue(selections.getByAccountId("removed").isEmpty())
        assertTrue(accounts.getById("kept")!!.isActive)
        assertEquals(1, selections.getByAccountId("kept").size)
    }

    @Test
    fun theSameEmailOnGoogleAndOutlookKeepsBothAccountsAndTheirChoices() = runBlocking {
        accounts.upsert(account("acc-g", "me@gmail.com"))
        selections.upsertAll(listOf(selection("acc-g", "work", enabled = true)))

        // Signing in to Outlook with the same address (v29: deleted the Google row and its choices)
        accounts.upsert(account("acc-o", "me@gmail.com", providerId = "OUTLOOK", accountType = "OUTLOOK_PERSONAL"))

        assertEquals("acc-g", accounts.getByEmail("GOOGLE", "", "me@gmail.com")?.id)
        assertEquals("acc-o", accounts.getByEmail("OUTLOOK", "", "me@gmail.com")?.id)
        assertEquals("the Google account keeps its calendar choices", 1, selections.getByAccountId("acc-g").size)
    }

    @Test
    fun anAccountOfAnUnknownProviderIsNeverReadAsGoogle() = runBlocking {
        val newer = account("acc-c", "me@icloud.com", providerId = "CALDAV", accountType = "CALDAV_ICLOUD")

        assertEquals(null, newer.toDomainModelOrNull())
        assertEquals(null, com.boox.day.data.model.ProviderId.fromKeyOrNull("CALDAV"))
        assertEquals(
            "events keep the display fallback",
            com.boox.day.data.model.ProviderId.GOOGLE,
            com.boox.day.data.model.ProviderId.fromKey("LOCAL")
        )
        assertEquals(
            com.boox.day.data.model.ProviderId.GOOGLE,
            account("acc-g", "me@gmail.com").toDomainModelOrNull()?.providerId
        )
    }

    private fun account(
        id: String,
        email: String,
        displayName: String? = null,
        providerId: String = "GOOGLE",
        accountType: String = "GOOGLE_PERSONAL"
    ) = ConnectedAccountEntity(
        id = id,
        providerId = providerId,
        accountType = accountType,
        email = email,
        displayName = displayName,
        isActive = true,
        createdAt = 1_760_000_000_000
    )

    private fun selection(accountId: String, calendarId: String, enabled: Boolean) = CalendarSelectionEntity(
        id = "$accountId-$calendarId",
        accountId = accountId,
        calendarId = calendarId,
        calendarName = calendarId,
        calendarColor = null,
        isFetchEnabled = enabled
    )
}

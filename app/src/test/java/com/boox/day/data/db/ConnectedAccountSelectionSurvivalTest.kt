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
        val existing = accounts.getByEmail("me@example.com")!!
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
        accounts.deactivate("old-id") // "Remove account" only marks it inactive

        // Sign-in finds no active account for the email and creates a new one (new random id)
        accounts.upsert(account("new-id", "me@example.com"))

        val saved = accounts.getByEmail("me@example.com")
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

    private fun account(id: String, email: String, displayName: String? = null) = ConnectedAccountEntity(
        id = id,
        providerId = "GOOGLE",
        accountType = "GOOGLE_PERSONAL",
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

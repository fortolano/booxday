package com.boox.day.sync

import com.boox.day.data.model.AccountType
import com.boox.day.data.model.ConnectedAccount
import com.boox.day.data.model.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Opening Calendar Accounts must not hide "sign in again" (keepSyncStatus, 31 Oct 2026). */
class AccountReconnectStatusTest {

    private val account = ConnectedAccount(
        id = "acc-1",
        providerId = ProviderId.GOOGLE,
        accountType = AccountType.GOOGLE_WORKSPACE,
        email = "user@example.com"
    )

    @Test
    fun `a reload keeps sign in again, and a new account starts clean`() {
        val shown = listOf(
            MultiAccountSyncViewModel.AccountWithCalendars(
                account = account,
                needsReauth = true,
                lastSyncMessage = "Google needs you to sign in again"
            )
        )
        val newAccount = account.copy(id = "acc-2", email = "other@example.com")
        val loaded = listOf(
            MultiAccountSyncViewModel.AccountWithCalendars(account = account),
            MultiAccountSyncViewModel.AccountWithCalendars(account = newAccount)
        )

        val kept = keepSyncStatus(loaded, shown)

        assertTrue(kept[0].needsReauth)
        assertEquals("Google needs you to sign in again", kept[0].lastSyncMessage)
        assertEquals(false, kept[1].needsReauth)
    }
}

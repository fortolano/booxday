package com.boox.day.ui

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.model.AccountType
import com.boox.day.sync.GoogleAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Reconnect Google Calendar" (Oct 2026): BooxDay now asks Google only for calendar events and the
 * calendar list (GoogleAuthManager.CALENDAR_SCOPES). An account signed in with an older version
 * allowed the full calendar scope instead, so Google refuses its tokens until it signs in once
 * more, and sync stops without a word. At start, this opens a popup for that account
 * (ui/dialogs/GoogleReconnectPopup.kt); Later hides it until the next start. MainScreen hosts it
 * and keeps raw drawing off while it is open.
 */
object GoogleReconnectGate {
    private const val TAG = "GoogleReconnectGate"

    /** The account type to sign in again with while the popup is open; null when closed. */
    var accountType by mutableStateOf<AccountType?>(null)
        private set

    val isOpen: Boolean get() = accountType != null

    private var checked = false

    /** Once per app run: opens the popup when a connected Google account needs to sign in again. */
    suspend fun checkOnce(context: Context) {
        if (checked) return
        checked = true
        val type = try {
            withContext(Dispatchers.IO) { accountNeedingReconnect(context) }
        } catch (e: Exception) {
            Log.w(TAG, "Reconnect check failed: ${e.message}")
            null
        } ?: return
        withContext(Dispatchers.Main) { accountType = type }
    }

    private suspend fun accountNeedingReconnect(context: Context): AccountType? {
        if (GlobalAppSettings.current.syncMode == AppSettings.SyncMode.STANDALONE) return null
        val email = GoogleAuthManager(context).calendarReconnectEmail() ?: return null
        // Only an account still connected in BooxDay (getByEmail reads active accounts)
        val account = AppDatabase.getDatabase(context).connectedAccountDao().getByEmail(email) ?: return null
        return AccountType.fromKey(account.accountType)
    }

    fun close() {
        accountType = null
    }
}

package com.boox.day.sync.caldav

import com.boox.day.data.model.AccountType
import com.boox.day.data.model.ConnectedAccount
import com.boox.day.data.model.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the CalDAV sign-in form does after Connect (plan step 3.6): a certificate Android can't check
 * is never trusted without asking, and one that differs from the certificate trusted before is
 * flagged as changed; a refused password gets the service's own hint.
 */
class CalDavSignInOutcomeTest {

    @Test
    fun anUncheckableCertificateIsAskedAboutAndAChangedOneSaysSo() {
        val untrusted = Result.failure<CalDavSignedIn>(
            CalDavConnection.Failure.UntrustedCertificate(NEW, "CN=radicale.home.lan,O=Home")
        )

        // First time: asked, not trusted, not "changed"
        assertEquals(
            CalDavSignInOutcome.AskTrust(NEW, "radicale.home.lan", changed = false),
            calDavSignInOutcome(untrusted, AccountType.CALDAV_OTHER, trustedBefore = null)
        )
        // Trusted before and now another certificate: asked, and flagged
        assertEquals(
            CalDavSignInOutcome.AskTrust(NEW, "radicale.home.lan", changed = true),
            calDavSignInOutcome(untrusted, AccountType.CALDAV_OTHER, trustedBefore = OLD)
        )

        // A refused password: iCloud's hint is the app-specific password
        val refused = calDavSignInOutcome(Result.failure(CalDavConnection.Failure.WrongPassword()), AccountType.CALDAV_ICLOUD, null)
        assertTrue("app-specific password" in (refused as CalDavSignInOutcome.Failed).message)

        val account = ConnectedAccount("a1", ProviderId.CALDAV, AccountType.CALDAV_OTHER, "fort")
        assertEquals(CalDavSignInOutcome.Connected(account), calDavSignInOutcome(Result.success(CalDavSignedIn(account, startTasks = false)), AccountType.CALDAV_OTHER, OLD))
    }

    private companion object {
        val OLD = (0 until 32).joinToString(":") { "%02X".format(it) }
        val NEW = (0 until 32).joinToString(":") { "%02X".format(255 - it) }
    }
}

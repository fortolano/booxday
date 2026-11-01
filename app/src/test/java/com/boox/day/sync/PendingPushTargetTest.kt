package com.boox.day.sync

import com.boox.day.data.db.LocalEvent
import com.boox.day.data.model.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where a pending tablet change of a synced event goes: never to Google for a provider it doesn't belong to. */
class PendingPushTargetTest {

    private fun event(providerId: String, accountId: String? = null) =
        LocalEvent(title = "Dentist", dayId = "2026-11-02", startTime = 0L, providerId = providerId, accountId = accountId)

    @Test
    fun anUnknownProvidersChangeIsNeverSentToGoogle() {
        // An event of a provider this version doesn't know used to go to Google (CalDAV until it was known)
        assertTrue(pendingPushTarget(event("EXCHANGE", "acc-1")) is PendingPushTarget.Nowhere)
    }

    @Test
    fun eventsOfTheOldGoogleOnlyPathAndKnownProvidersGoWhereTheyDid() {
        assertEquals(PendingPushTarget.LegacyGoogle, pendingPushTarget(event("LOCAL")))
        assertEquals(PendingPushTarget.LegacyGoogle, pendingPushTarget(event("GOOGLE")))
        assertEquals(PendingPushTarget.Provider(ProviderId.OUTLOOK, "acc-2"), pendingPushTarget(event("OUTLOOK", "acc-2")))
        assertEquals(PendingPushTarget.Provider(ProviderId.CALDAV, "acc-3"), pendingPushTarget(event("CALDAV", "acc-3")))
        assertTrue("Outlook without its account waits", pendingPushTarget(event("OUTLOOK")) is PendingPushTarget.Nowhere)
    }
}

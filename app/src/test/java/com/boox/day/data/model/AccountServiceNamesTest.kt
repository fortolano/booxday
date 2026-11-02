package com.boox.day.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * An event's service is named as users know it (owner's choice, 2 Nov 2026): a CalDAV account by
 * its service, or its server for Other, never "CalDAV Calendar"; Google and Outlook as before.
 */
class AccountServiceNamesTest {

    @Test
    fun aCalDavEventIsNamedByItsServiceGoogleAsBefore() {
        AccountServiceNames.update(
            listOf(
                ConnectedAccount("i1", ProviderId.CALDAV, AccountType.CALDAV_ICLOUD, "me@icloud.com", serverUrl = "https://caldav.icloud.com/"),
                ConnectedAccount("o1", ProviderId.CALDAV, AccountType.CALDAV_OTHER, "fort", serverUrl = "https://dav.example.com/"),
                ConnectedAccount("g1", ProviderId.GOOGLE, AccountType.GOOGLE_PERSONAL, "me@gmail.com")
            )
        )
        assertEquals("iCloud", AccountServiceNames.of("CALDAV", "i1"))
        assertEquals("dav.example.com", AccountServiceNames.of("CALDAV", "o1"))
        assertEquals("Google Calendar", AccountServiceNames.of("GOOGLE", "g1"))
    }
}

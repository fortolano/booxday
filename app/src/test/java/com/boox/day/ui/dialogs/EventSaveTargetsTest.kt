package com.boox.day.ui.dialogs

import com.boox.day.data.model.AccountType
import com.boox.day.data.model.ProviderId
import com.boox.day.data.model.WritableCalendar
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Add Event with CalDAV accounts (plan step 3.6): each CalDAV account is its own line, and an event
 * goes to the chosen account's calendar even when another account has a calendar at the same
 * address (two Nextcloud accounts both have "/remote.php/dav/calendars/fort/personal/").
 */
class EventSaveTargetsTest {

    @Test
    fun anEventGoesToTheChosenAccountEvenWhenTwoCalendarsShareAnAddress() {
        val personal = "/remote.php/dav/calendars/fort/personal/"
        val calendars = listOf(
            WritableCalendar("primary", "Me", "g1", "me@gmail.com", ProviderId.GOOGLE, isPrimary = true),
            WritableCalendar(personal, "Personal", "n1", "fort", ProviderId.CALDAV, false, null, AccountType.CALDAV_NEXTCLOUD, "https://home.example.com/"),
            WritableCalendar(personal, "Personal", "n2", "fort", ProviderId.CALDAV, false, null, AccountType.CALDAV_NEXTCLOUD, "https://work.example.com/")
        )

        val targets = eventSaveTargets(calendars, hasGoogle = true, hasOutlook = false)
        assertEquals(
            listOf("Google" to "me@gmail.com", "Nextcloud" to "fort · home.example.com", "Nextcloud" to "fort · work.example.com"),
            targets.map { it.name to it.detail }
        )

        val work = targets.last()
        assertEquals("n2", chosenCalendar(calendars, work.providerId, work.accountId, personal)?.accountId)
        // Google keeps choosing by service, as before
        assertEquals("g1", chosenCalendar(calendars, ProviderId.GOOGLE, null, "primary")?.accountId)

        // A new event starts in the account's "Default calendar for new events", not its main one
        val googleWork = WritableCalendar("work", "Work", "g1", "me@gmail.com", ProviderId.GOOGLE, isPrimary = false, isDefault = true)
        assertEquals("work", calendarForNewEvent(listOf(calendars.first(), googleWork))?.id)
    }
}

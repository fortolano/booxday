package com.boox.day.actionitems

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ActionItemRulesTest {
    private val wednesday = LocalDate.parse("2026-10-07")

    private fun find(line: String) = ActionItemRules.find(line, wednesday)

    @Test
    fun `doing words, marks and reminders count`() {
        assertEquals("Call Mike about the budget", find("Call Mike about the budget")?.text)
        assertEquals("Send deck to Anna by Friday", find("- send deck to Anna by Friday")?.text)
        assertEquals("Email Sarah", find("1. Email Sarah")?.text)
        assertEquals("Book flights", find("[ ] book flights")?.text)
        assertEquals("Renew passport", find("- [ ] renew passport")?.text)
        assertEquals("Passport photos", find("☐ passport photos")?.text)
        assertEquals("Dentist", find("TODO: dentist")?.text)
        assertEquals("Mike to send numbers", find("Action: Mike to send numbers")?.text)
        assertEquals("Mike: send the deck", find("Mike: send the deck")?.text)
        assertEquals("We need to hire a designer", find("We need to hire a designer")?.text)
        assertEquals("Don't forget the receipts", find("don't forget the receipts.")?.text)
        assertEquals("Follow up with the bishop", find("• follow up with the bishop")?.text)
    }

    @Test
    fun `notes, ticked boxes and scraps don't`() {
        assertNull(find("- Revenue up 5% this quarter"))
        assertNull(find("Budget approved by the board"))
        assertNull(find("[x] call mom"))
        assertNull(find("- [x] call mom"))
        assertNull(find("☑ pay rent"))
        assertNull(find("TODO:"))
        assertNull(find("Go"))
        assertNull(find("- 12/10"))
        assertNull(find(""))
    }

    @Test
    fun `the day a line names becomes its due day`() {
        assertEquals("2026-10-09", find("send deck by Friday")?.dueDay)
        assertEquals("2026-10-16", find("send deck next Friday")?.dueDay)
        assertEquals("2026-10-13", find("email Sarah next Tue")?.dueDay)
        assertEquals("2026-10-07", find("pay rent today")?.dueDay)
        assertEquals("2026-10-08", find("Mike: send the deck tomorrow")?.dueDay)
        assertEquals("2026-10-12", find("pay invoice Oct 12")?.dueDay)
        assertEquals("2026-11-03", find("renew license 3 Nov")?.dueDay)
        assertEquals("2026-10-15", find("submit form 10/15")?.dueDay)
        assertEquals("2026-10-25", find("submit form 25/10")?.dueDay)
        // A few days back stays this year; long past means next year
        assertEquals("2026-10-02", find("call bank 10/2")?.dueDay)
        assertEquals("2027-09-01", find("call bank 9/1")?.dueDay)
        assertNull(find("call the bank")?.dueDay)
    }

    @Test
    fun `a line keeps its key when read again`() {
        assertEquals(ActionItemRules.key("p1", "Call Mike!"), ActionItemRules.key("p1", "call  mike"))
        assertNotEquals(ActionItemRules.key("p1", "Call Mike"), ActionItemRules.key("p2", "Call Mike"))
    }
}

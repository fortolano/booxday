package com.boox.day.ocr.semantic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrgNamesTest {

    @Test
    fun findsCompaniesAndTeams() {
        assertEquals(setOf("acme corp"), OrgNames.find("Call with Acme Corp about the renewal"))
        assertEquals(setOf("global tech inc"), OrgNames.find("Global Tech Inc quarterly review"))
        assertEquals(setOf("ibm co"), OrgNames.find("Lunch at IBM Co today"))
        assertEquals(setOf("design team"), OrgNames.find("Design team weekly"))
    }

    @Test
    fun plainWordsAreNotACompany() {
        assertTrue(OrgNames.find("please review the draft agenda before our session").isEmpty())
        assertTrue(OrgNames.find("the whole team").isEmpty())
    }

    /** The old pattern never finished on this kind of text: six plain words were enough (Nov 2026). */
    @Test(timeout = 2_000)
    fun longTextFinishesAtOnce() {
        val sentence = "please review the draft agenda before our quarterly planning session and bring your notes "
        val text = sentence.repeat(60).take(5_000)
        assertTrue(OrgNames.find(text).isEmpty())
        assertTrue(OrgNames.find(text.split(" ").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }).isEmpty())
        assertTrue(OrgNames.find("a".repeat(5_000)).isEmpty())
        assertTrue(OrgNames.find("A".repeat(5_000)).isEmpty())
    }
}

package com.boox.day.ocr

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Nine fixed languages and one Other language card (owner, 5 Nov 2026). */
class HandwritingLanguagesTest {

    private val steps = mutableListOf<String>()

    private fun swap(old: String?, new: String, wasActive: Boolean = false, downloads: Boolean = true) = runBlocking {
        OtherLanguageSwap.swap(
            old, new, wasActive,
            show = { steps += "show $it" },
            download = {
                steps += "download $it"
                downloads
            },
            activate = { steps += "use $it" },
            delete = { steps += "delete $it" }
        )
    }

    @Test fun `the list holds the languages without a card, and no gestures`() {
        val tags = HandwritingLanguages.others.map { it.tag }
        assertEquals(9, HandwritingLanguages.FIXED.size)
        assertTrue("uk" in tags)
        assertTrue("ar" in tags)
        assertTrue("pt-BR" in tags)
        assertFalse("ru" in tags)
        assertFalse("en-US" in tags)
        assertTrue(tags.none { "-x-" in it })
        assertTrue(HandwritingLanguages.others.all { it.name.first().isUpperCase() })
    }

    @Test fun `the new language downloads first, and the old one goes last`() {
        assertTrue(swap("uk", "pl"))
        assertEquals(listOf("show pl", "download pl", "delete uk"), steps)
    }

    @Test fun `a failed download leaves the card as it was`() {
        assertFalse(swap("uk", "pl", wasActive = true, downloads = false))
        assertEquals(listOf("show pl", "download pl", "show uk"), steps)
    }

    @Test fun `a language in use hands over before it is deleted`() {
        swap("uk", "pl", wasActive = true)
        assertEquals(listOf("show pl", "download pl", "use pl", "delete uk"), steps)
    }

    @Test fun `the first choice deletes nothing, and choosing the same one does nothing`() {
        swap(null, "pl")
        assertEquals(listOf("show pl", "download pl"), steps)
        steps.clear()
        assertTrue(swap("pl", "pl"))
        assertTrue(steps.isEmpty())
    }
}

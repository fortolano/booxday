package com.boox.day.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextTest {

    @Test
    fun foldDropsCaseAccentsAndPunctuation() {
        assertEquals("l eneide 50", SearchText.fold("L'Énéide, 50%!"))
        assertEquals("cafe creme", SearchText.fold("  CAFÉ   crème  "))
        assertEquals("lodz", SearchText.fold("Łódź"))
        assertEquals("strasse", SearchText.fold("Straße"))
        assertEquals("", SearchText.fold("%_*\"-"))
    }

    @Test
    fun ftsQueryKeepsLettersSqliteDoesNotFold() {
        // unicode61 only removes marks Unicode decomposes: ł and ß stay in the index
        assertEquals("łodz*", SearchText.ftsPrefixQuery("Łódź"))
        assertEquals("straße*", SearchText.ftsPrefixQuery("Straße"))
    }

    @Test
    fun queryWordsAreFoldedAndDeduplicated() {
        assertEquals(listOf("dar", "mat"), SearchText.queryWords("Dar  MAT dar"))
        assertTrue(SearchText.queryWords("  %_ ").isEmpty())
    }

    @Test
    fun everyWordMustStartAWordInAnyOrder() {
        val text = SearchText.fold("Talk about dark matter with Mary")
        assertTrue(SearchText.matchesWordStarts(text, SearchText.queryWords("dar mat")))
        assertTrue(SearchText.matchesWordStarts(text, SearchText.queryWords("mat dar")))
        assertTrue(SearchText.matchesWordStarts(text, SearchText.queryWords("MARY")))
        assertFalse(SearchText.matchesWordStarts(text, SearchText.queryWords("atter")))
        assertFalse(SearchText.matchesWordStarts(text, SearchText.queryWords("dar xyz")))
        assertFalse(SearchText.matchesWordStarts(SearchText.fold("primary school"), listOf("mary")))
    }

    @Test
    fun aWordAfterAnEarlierInnerHitStillCounts() {
        // "mary" first appears inside "primary", then as a word of its own
        assertTrue(SearchText.matchesWordStarts(SearchText.fold("primary call with Mary"), listOf("mary")))
    }

    @Test
    fun substringsFindWordsInsideWords() {
        assertTrue(SearchText.matchesSubstrings(SearchText.fold("Primary school"), listOf("mary")))
        assertFalse(SearchText.matchesSubstrings(SearchText.fold("Primary school"), listOf("mary", "zoo")))
    }

    @Test
    fun accentsAreIgnoredBothWays() {
        assertTrue(SearchText.matchesWordStarts(SearchText.fold("Café au lait"), SearchText.queryWords("cafe")))
        assertTrue(SearchText.matchesWordStarts(SearchText.fold("cafe au lait"), SearchText.queryWords("CAFÉ")))
    }

    @Test
    fun percentAndUnderscoreAreNotWildcards() {
        // Under LIKE, "a_c" matched "abc" and "%" matched everything. Now they are punctuation:
        // "a_c" is the words "a" and "c", and "%" alone has nothing to search for.
        assertFalse(SearchText.matchesWordStarts(SearchText.fold("abc"), SearchText.queryWords("a_c")))
        assertTrue(SearchText.matchesWordStarts(SearchText.fold("a_c"), SearchText.queryWords("a_c")))
        assertTrue(SearchText.queryWords("%").isEmpty())
    }

    @Test
    fun emptyQueriesMatchNothing() {
        assertFalse(SearchText.matchesWordStarts("anything", emptyList()))
        assertFalse(SearchText.matchesSubstrings("anything", emptyList()))
        assertEquals("", SearchText.ftsPrefixQuery(" - "))
    }
}

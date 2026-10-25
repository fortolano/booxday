package com.boox.day.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Page images are saved once edits pause (Oct 2026), not every 500 ms while writing. Pins the rule
 * PageDataManager uses: one save after the last edit, pages independent, readers can flush.
 * Time is driven by hand; Unconfined runs each step at once.
 */
class PerKeyDebouncerTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private var now = 0L
    private val sleepers = mutableListOf<Pair<Long, CompletableDeferred<Unit>>>()
    private val saves = mutableListOf<Pair<String, Long>>()

    private val sleep: suspend (Long) -> Unit = { ms ->
        val wake = CompletableDeferred<Unit>()
        sleepers += (now + ms) to wake
        wake.await()
    }

    private val debouncer = PerKeyDebouncer(scope, 2_000L, sleep) { page -> saves += page to now }

    private fun advanceTo(time: Long) {
        now = time
        val due = sleepers.filter { it.first <= time }
        sleepers.removeAll(due)
        due.forEach { it.second.complete(Unit) }
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun editsCloseTogetherSaveOnceAfterTheLast() {
        debouncer.schedule("page")
        advanceTo(500)
        debouncer.schedule("page")
        advanceTo(1_500)
        debouncer.schedule("page")

        advanceTo(3_000)
        assertTrue(saves.isEmpty())
        advanceTo(3_500)
        assertEquals(listOf("page" to 3_500L), saves)
        assertFalse(debouncer.isPending("page"))
    }

    @Test
    fun pagesAreSavedIndependently() {
        debouncer.schedule("a")
        advanceTo(1_000)
        debouncer.schedule("b")

        advanceTo(2_000)
        assertEquals(listOf("a" to 2_000L), saves)
        advanceTo(3_000)
        assertEquals(listOf("a" to 2_000L, "b" to 3_000L), saves)
    }

    @Test
    fun flushSavesAtOnceAndOnlyOnce() {
        debouncer.schedule("page")
        advanceTo(300)

        assertTrue(debouncer.flush("page"))
        assertEquals(listOf("page" to 300L), saves)
        advanceTo(5_000)
        assertEquals(1, saves.size)
        assertFalse(debouncer.flush("page"))
    }

    @Test
    fun cancelDropsTheWaitingSave() {
        debouncer.schedule("page")
        debouncer.cancel("page")

        advanceTo(5_000)
        assertTrue(saves.isEmpty())
        assertFalse(debouncer.isPending("page"))
    }

    @Test
    fun flushAllSavesEveryWaitingPage() {
        debouncer.schedule("a")
        debouncer.schedule("b")

        debouncer.flushAll()
        assertEquals(setOf("a", "b"), saves.map { it.first }.toSet())
        advanceTo(5_000)
        assertEquals(2, saves.size)
    }

    @Test
    fun aSaveCanAskToRunAgain() {
        // PageDataManager re-schedules a page whose save met a page transition
        var retried = false
        lateinit var retrying: PerKeyDebouncer
        retrying = PerKeyDebouncer(scope, 2_000L, sleep) { page ->
            saves += page to now
            if (!retried) {
                retried = true
                retrying.schedule(page)
            }
        }

        retrying.schedule("page")
        advanceTo(2_000)
        assertTrue(retrying.isPending("page"))
        advanceTo(4_000)
        assertEquals(listOf("page" to 2_000L, "page" to 4_000L), saves)
        assertFalse(retrying.isPending("page"))
    }
}

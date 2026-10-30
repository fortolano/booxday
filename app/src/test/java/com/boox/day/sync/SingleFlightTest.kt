package com.boox.day.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * SingleFlight keeps MultiAccountSyncManager.syncAllAccounts to one run at a time (Oct 2026).
 * UNDISPATCHED starts each call right away up to its first suspension, so the order is exact.
 */
class SingleFlightTest {

    @Test
    fun `a second caller for the running job shares its result`() = runBlocking {
        val flight = SingleFlight<String, Int>()
        val release = CompletableDeferred<Unit>()
        var runs = 0

        val first = async(start = CoroutineStart.UNDISPATCHED) { flight.run("sync") { runs++; release.await(); 42 } }
        val second = async(start = CoroutineStart.UNDISPATCHED) { flight.run("sync") { runs++; 7 } }
        release.complete(Unit)

        assertEquals(42, first.await())
        assertEquals("the second caller got the running job's result", 42, second.await())
        assertEquals("the job ran once", 1, runs)
    }

    @Test
    fun `a different job waits for the running one`() = runBlocking {
        val flight = SingleFlight<String, String>()
        val release = CompletableDeferred<Unit>()
        var secondStarted = false

        val first = async(start = CoroutineStart.UNDISPATCHED) { flight.run("this month") { release.await(); "A" } }
        val second = async(start = CoroutineStart.UNDISPATCHED) { flight.run("next month") { secondStarted = true; "B" } }
        assertFalse("never two at once", secondStarted)
        release.complete(Unit)

        assertEquals("A", first.await())
        assertEquals("B", second.await())
    }

    @Test
    fun `a caller that joined a cancelled job runs its own`() = runBlocking {
        val flight = SingleFlight<String, Int>()
        val never = CompletableDeferred<Unit>()

        val first = async(start = CoroutineStart.UNDISPATCHED) { flight.run("sync") { never.await(); 1 } }
        val second = async(start = CoroutineStart.UNDISPATCHED) { flight.run("sync") { 2 } }
        first.cancel()  // its screen went away

        assertEquals(2, second.await())
    }
}

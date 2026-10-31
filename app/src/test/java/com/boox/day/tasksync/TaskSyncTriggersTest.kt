package com.boox.day.tasksync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When task sync runs by itself (plan step 2.7; owner decisions 11 and 34). The wait after an edit
 * is 100 ms here instead of 25 s.
 */
class TaskSyncTriggersTest {

    private var on = true
    private var now = 1_000_000_000L
    private var lastRun = 0L
    private val runs = mutableListOf<Long>()
    private val finished = mutableListOf<Long>()
    private var hold: CompletableDeferred<Unit>? = null
    /** What each run returns, in turn; DONE once used up. */
    private val results = ArrayDeque<TaskSyncRun.Result>()
    private var waitingAt: Long? = null
    private var afterRun: () -> Unit = {}

    private fun CoroutineScope.triggers() = TaskSyncTriggers.Triggers(
        this,
        isOn = { on },
        lastRunAt = { lastRun },
        nextWaitingAt = { waitingAt },
        run = { changedAt ->
            runs += changedAt
            hold?.await()
            finished += changedAt
            lastRun = now
            afterRun()
            results.removeFirstOrNull() ?: TaskSyncRun.Result.DONE
        },
        clock = { now },
        afterEditMs = 100L,
        retryWaitsMs = listOf(50L, 50L, 50L)
    )

    // ── Retries (owner, 30 Oct 2026: 1, 2, 4, 8, 15 minutes in the app; 50 ms here) ──

    @Test
    fun offlineIsRetriedAFewTimesThenStops() = runBlocking {
        repeat(10) { results += TaskSyncRun.Result.OFFLINE }
        val triggers = triggers()
        triggers.syncNow()
        delay(500)
        assertEquals("the run and its 3 retries", 4, runs.size)
        delay(200)
        assertEquals("then it stops", 4, runs.size)
    }

    @Test
    fun retriesStopOnceASyncWorks() = runBlocking {
        results += listOf(TaskSyncRun.Result.BUSY, TaskSyncRun.Result.FAILED)
        val triggers = triggers()
        triggers.syncNow()
        delay(500)
        assertEquals(3, runs.size)
    }

    @Test
    fun whatNeedsTheUserIsNotRetried() = runBlocking {
        results += listOf(TaskSyncRun.Result.NEEDS_RECONNECT)
        triggers().syncNow()
        delay(300)
        assertEquals(1, runs.size)
        results += listOf(TaskSyncRun.Result.PAUSED_FOR_DELETES)
        triggers().syncNow()
        delay(300)
        assertEquals(2, runs.size)
    }

    @Test
    fun aChangeThatFailedToSendIsRetriedAtItsOwnTime() = runBlocking {
        waitingAt = now + 300  // the sender's own wait, or Google's Retry-After
        afterRun = { if (runs.size == 2) waitingAt = null }
        val triggers = triggers()
        triggers.syncNow()
        delay(150)
        assertEquals("not at 50 ms: the change isn't due yet", 1, runs.size)
        delay(300)
        assertEquals(2, runs.size)
        delay(200)
        assertEquals("sent: no more retries", 2, runs.size)
    }

    @Test
    fun anEditStartsTheRetriesAgain() = runBlocking {
        repeat(20) { results += TaskSyncRun.Result.OFFLINE }
        val triggers = triggers()
        triggers.syncNow()
        delay(500)
        assertEquals(4, runs.size)
        triggers.afterEdit()
        delay(700)
        assertEquals("the edit's run and 3 more retries", 8, runs.size)
    }

    @Test
    fun turningOffStopsRetries() = runBlocking {
        repeat(10) { results += TaskSyncRun.Result.OFFLINE }
        val triggers = triggers()
        triggers.syncNow()
        delay(20)
        triggers.cancel()
        delay(300)
        assertEquals(1, runs.size)
    }

    @Test
    fun aRetryWaitingIsDroppedWhenAnotherSyncWorks() = runBlocking {
        results += TaskSyncRun.Result.OFFLINE
        val triggers = triggers()
        triggers.syncNow()
        delay(10)
        triggers.syncNow()  // DONE before the retry's 50 ms
        delay(300)
        assertEquals(2, runs.size)
    }

    // ── Edits, returns, Sync ──

    @Test
    fun aBurstOfEditsIsOneRunAfterTheLast() = runBlocking {
        val triggers = triggers()
        triggers.afterEdit()
        now += 10
        triggers.afterEdit()
        now += 10
        triggers.afterEdit()
        delay(50)
        assertTrue("not yet", runs.isEmpty())
        delay(300)
        assertEquals("one run, for the last edit", listOf(now), runs)
    }

    @Test
    fun anEditDuringARunGetsARunOfItsOwn() = runBlocking {
        val triggers = triggers()
        hold = CompletableDeferred()
        triggers.afterEdit()
        delay(250)
        assertEquals("the first run is going", 1, runs.size)
        now += 1_000
        triggers.afterEdit()  // doesn't cancel the running one
        hold!!.complete(Unit)
        delay(300)
        assertEquals(listOf(now - 1_000, now), runs)
        assertEquals("the first run wasn't cut off", listOf(now - 1_000, now), finished)
    }

    @Test
    fun comingBackRunsAtMostEveryTenMinutes() = runBlocking {
        val triggers = triggers()
        assertTrue("never ran: runs", triggers.onReturn())
        delay(20)
        assertEquals(1, runs.size)

        now += 9 * 60_000L
        assertFalse("9 minutes later: no", triggers.onReturn())
        now += 60_000L
        assertTrue("10 minutes later: yes", triggers.onReturn())
        delay(20)
        assertEquals(2, runs.size)
    }

    @Test
    fun anyRunCountsForTheTenMinutes() = runBlocking {
        val triggers = triggers()
        triggers.syncNow()
        delay(20)
        now += 5 * 60_000L
        assertFalse("Sync Now 5 minutes ago", triggers.onReturn())
    }

    @Test
    fun aClockSetBackDoesntStopRuns() = runBlocking {
        val triggers = triggers()
        lastRun = now + 3_600_000L
        assertTrue(triggers.onReturn())
    }

    @Test
    fun syncNowAlwaysRuns() = runBlocking {
        val triggers = triggers()
        lastRun = now
        triggers.syncNow()
        triggers.syncNow()
        delay(20)
        assertEquals(listOf(now, now), runs)
    }

    @Test
    fun nothingRunsWhileOff() = runBlocking {
        on = false
        val triggers = triggers()
        triggers.afterEdit()
        triggers.syncNow()
        assertFalse(triggers.onReturn())
        delay(250)
        assertTrue(runs.isEmpty())
    }

    @Test
    fun turningOffDropsTheWaitingRun() = runBlocking {
        val triggers = triggers()
        triggers.afterEdit()
        triggers.cancel()
        delay(250)
        assertTrue(runs.isEmpty())
    }
}

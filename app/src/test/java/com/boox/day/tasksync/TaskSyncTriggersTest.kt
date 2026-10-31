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

    private fun CoroutineScope.triggers() = TaskSyncTriggers.Triggers(
        this,
        isOn = { on },
        lastRunAt = { lastRun },
        run = { changedAt ->
            runs += changedAt
            hold?.await()
            finished += changedAt
            lastRun = now
            TaskSyncRun.Result.DONE
        },
        clock = { now },
        afterEditMs = 100L
    )

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

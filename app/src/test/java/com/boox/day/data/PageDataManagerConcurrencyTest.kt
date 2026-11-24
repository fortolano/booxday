package com.boox.day.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies PageDataManager's loading-job cancellation and removal do not self-deadlock on jobLock
 * or lose active jobs during cache eviction (Phase 1 C1).
 */
class PageDataManagerConcurrencyTest {

    private val testPages = listOf("pdm-active-1", "pdm-active-2", "pdm-ignored", "pdm-done")

    @After
    fun tearDown() = runBlocking {
        testPages.forEach { PageDataManager.cancelLoadingPageNow(it) }
        PageDataManager.clearAllPagesNow()
    }

    @Test
    fun cancelLoadingPagesDoesNotSelfDeadlockAndRespectsIgnoredIds() = runBlocking {
        val active1 = Job()
        val active2 = Job()
        val ignored = Job()
        PageDataManager.registerLoadingJobForTest("pdm-active-1", active1)
        PageDataManager.registerLoadingJobForTest("pdm-active-2", active2)
        PageDataManager.registerLoadingJobForTest("pdm-ignored", ignored)

        withTimeout(2_000L) {
            PageDataManager.cancelLoadingPagesNow(ignoredPageIds = listOf("pdm-ignored"))
        }

        assertTrue(active1.isCancelled)
        assertTrue(active2.isCancelled)
        assertFalse(ignored.isCancelled)
        assertFalse(PageDataManager.hasLoadingJobForTest("pdm-active-1"))
        assertFalse(PageDataManager.hasLoadingJobForTest("pdm-active-2"))
        assertTrue(PageDataManager.hasLoadingJobForTest("pdm-ignored"))

        ignored.cancel()
    }

    @Test
    fun removePageSkipsActiveJobAndClearAllPagesHandlesCompletedJobsWithoutCme() = runBlocking {
        val active = Job()
        val completed = CompletableDeferred(Unit)
        PageDataManager.registerLoadingJobForTest("pdm-active-1", active)
        PageDataManager.registerLoadingJobForTest("pdm-done", completed)

        // Active loading page must not be evicted mid-load
        assertFalse(PageDataManager.removePage("pdm-active-1"))
        assertTrue(PageDataManager.hasLoadingJobForTest("pdm-active-1"))

        withTimeout(2_000L) {
            PageDataManager.clearAllPagesNow()
        }

        assertTrue(active.isCancelled)
        assertFalse(PageDataManager.hasLoadingJobForTest("pdm-active-1"))
        assertFalse(PageDataManager.hasLoadingJobForTest("pdm-done"))
    }
}

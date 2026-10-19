package com.boox.day.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BitmapMemoryBudgetTest {
    private val mib = 1024L * 1024L

    @Test fun cacheBudgetsLeaveMostOfTheHeapForTheEditor() {
        for (heap in listOf(128 * mib, 256 * mib, 512 * mib)) {
            val caches = BitmapMemoryBudget.previewBytes(heap).toLong() + BitmapMemoryBudget.pageCacheBytes(heap)
            assertTrue(caches <= heap * 3 / 8)
        }
    }

    @Test fun aHighResolutionPageDoesNotReceiveTheOldEightImageAllowance() {
        val pageBytes = 2400L * 3200L * 4L
        val previewBudget = BitmapMemoryBudget.previewBytes(256 * mib)
        assertEquals(1L, previewBudget / pageBytes)
        assertEquals(2L, BitmapMemoryBudget.pageCacheBytes(256 * mib) / pageBytes)
        assertTrue(pageBytes * 8 > previewBudget)
    }

    @Test fun smallerTabletsCanStillCacheSeveralPreviews() {
        val pageBytes = 1072L * 982L * 4L
        assertTrue(BitmapMemoryBudget.previewBytes(256 * mib) / pageBytes >= 6)
    }

    @Test fun veryLargeHeapsDoNotGrowOptionalCachesWithoutLimit() {
        assertEquals(32 * mib, BitmapMemoryBudget.previewBytes(4L * 1024 * mib).toLong())
        assertEquals(64 * mib, BitmapMemoryBudget.pageCacheBytes(4L * 1024 * mib))
    }
}

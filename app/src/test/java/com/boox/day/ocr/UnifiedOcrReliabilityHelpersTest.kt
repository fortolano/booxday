package com.boox.day.ocr

import com.boox.day.data.db.UnifiedOcrPage
import com.boox.day.data.db.buildFailedPage
import com.boox.day.data.db.buildInProgressPage
import com.boox.day.ocr.model.OcrQueueItem
import com.boox.day.ocr.model.OcrSource
import com.boox.day.ocr.model.UnifiedOcrStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedOcrReliabilityHelpersTest {

    @Test
    fun buildInProgressPage_preservesRetryHistoryAndIndexedState() {
        val existing = UnifiedOcrPage(
            id = UnifiedOcrPage.generateId(OcrSource.BOOX_NOTE, "note-1", "page-1"),
            sourceType = OcrSource.BOOX_NOTE.name,
            sourceId = "note-1",
            pageId = "page-1",
            sourceCreatedAt = 10L,
            sourceModifiedAt = 20L,
            ocrAt = 30L,
            status = UnifiedOcrStatus.FAILED.name,
            failCount = 2,
            lastError = "old error",
            textHash = "text-hash",
            semanticHash = "semantic-hash",
            firstIndexedAt = 40L
        )

        val updated = buildInProgressPage(
            existingPage = existing,
            sourceType = OcrSource.BOOX_NOTE,
            sourceId = "note-1",
            pageId = "page-1",
            sourceModifiedAt = 99L,
            now = 123L
        )

        assertEquals(UnifiedOcrStatus.IN_PROGRESS.name, updated.status)
        assertEquals(2, updated.failCount)
        assertEquals("old error", updated.lastError)
        assertEquals("text-hash", updated.textHash)
        assertEquals("semantic-hash", updated.semanticHash)
        assertEquals(40L, updated.firstIndexedAt)
        assertEquals(99L, updated.sourceModifiedAt)
        assertEquals(123L, updated.ocrAt)
        assertEquals(10L, updated.sourceCreatedAt)
    }

    @Test
    fun buildFailedPage_incrementsFailCountWithoutResettingHashes() {
        val existing = UnifiedOcrPage(
            id = UnifiedOcrPage.generateId(OcrSource.MEMO, "2026-03-07", "page-1"),
            sourceType = OcrSource.MEMO.name,
            sourceId = "2026-03-07",
            pageId = "page-1",
            sourceCreatedAt = 10L,
            sourceModifiedAt = 20L,
            ocrAt = 30L,
            status = UnifiedOcrStatus.IN_PROGRESS.name,
            failCount = 1,
            lastError = null,
            textHash = "text-hash",
            semanticHash = "semantic-hash",
            firstIndexedAt = 40L
        )

        val failed = buildFailedPage(
            existingPage = existing,
            sourceType = OcrSource.MEMO,
            sourceId = "2026-03-07",
            pageId = "page-1",
            error = "network fail",
            now = 222L
        )

        assertEquals(UnifiedOcrStatus.FAILED.name, failed.status)
        assertEquals(2, failed.failCount)
        assertEquals("network fail", failed.lastError)
        assertEquals("text-hash", failed.textHash)
        assertEquals("semantic-hash", failed.semanticHash)
        assertEquals(40L, failed.firstIndexedAt)
        assertEquals(222L, failed.ocrAt)
    }

    @Test
    fun filterCompletedQueueItems_usesCompositeQueueIdentity() {
        val memoPage = OcrQueueItem(
            sourceType = OcrSource.MEMO,
            sourceId = "2026-03-07",
            pageId = "page-1",
            sourceCreatedAt = 1L,
            sourceModifiedAt = 2L
        )
        val booxPage = OcrQueueItem(
            sourceType = OcrSource.BOOX_NOTE,
            sourceId = "note-1",
            pageId = "page-1",
            sourceCreatedAt = 3L,
            sourceModifiedAt = 4L
        )

        val remaining = filterCompletedQueueItems(
            queue = listOf(memoPage, booxPage),
            completedQueueIds = setOf(memoPage.stableQueueId())
        )

        assertEquals(1, remaining.size)
        assertEquals(booxPage.stableQueueId(), remaining.single().stableQueueId())
        assertTrue(remaining.any { it.pageId == "page-1" })
    }

    @Test
    fun buildFailedPage_forNewRowStartsFailureTracking() {
        val failed = buildFailedPage(
            existingPage = null,
            sourceType = OcrSource.BOOX_NOTE,
            sourceId = "note-2",
            pageId = "page-9",
            error = null,
            now = 555L
        )

        assertEquals(UnifiedOcrStatus.FAILED.name, failed.status)
        assertEquals(1, failed.failCount)
        assertNull(failed.lastError)
        assertEquals(555L, failed.sourceCreatedAt)
        assertEquals(555L, failed.sourceModifiedAt)
    }
}

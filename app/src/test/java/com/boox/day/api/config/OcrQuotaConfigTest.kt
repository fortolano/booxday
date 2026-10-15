package com.boox.day.api.config

import com.boox.day.ocr.model.OcrSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class OcrQuotaConfigTest {

    @Test
    fun `stableHash returns same value for identical config`() {
        val first = OcrQuotaConfig(
            backfillLimit = 10,
            monthlyBudget = 25,
            booxNotesPagesPerNote = 2,
            smartLinkEnabled = true
        )
        val second = OcrQuotaConfig(
            backfillLimit = 10,
            monthlyBudget = 25,
            booxNotesPagesPerNote = 2,
            smartLinkEnabled = true
        )

        assertEquals(first.stableHash(), second.stableHash())
    }

    @Test
    fun `stableHash changes when OCR sampling config changes`() {
        val baseline = OcrQuotaConfig(booxNotesPagesPerNote = 1)
        val changed = OcrQuotaConfig(booxNotesPagesPerNote = 2)

        assertNotEquals(baseline.stableHash(), changed.stableHash())
    }

    @Test
    fun `monthlySourceBudget maps source-specific limits correctly`() {
        val config = OcrQuotaConfig(
            monthlyBudgetBySource = SourceBudgets(
                booxNotes = 11,
                memos = 7,
                tasks = 3
            )
        )

        assertEquals(11, config.monthlySourceBudget(OcrSource.BOOX_NOTE))
        assertEquals(7, config.monthlySourceBudget(OcrSource.MEMO))
        assertEquals(3, config.monthlySourceBudget(OcrSource.TASK))
        assertEquals(null, config.monthlySourceBudget(OcrSource.CALENDAR_EVENT))
    }
}

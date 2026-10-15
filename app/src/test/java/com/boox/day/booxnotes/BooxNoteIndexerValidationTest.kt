package com.boox.day.booxnotes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BooxNoteIndexerValidationTest {

    @Test
    fun shouldTreatNoteAsPresentDuringQuickValidation_keepsProviderConfirmedNotes() {
        assertTrue(
            shouldTreatNoteAsPresentDuringQuickValidation(
                nativeExists = false,
                providerHasNote = true
            )
        )
    }

    @Test
    fun shouldTreatNoteAsPresentDuringQuickValidation_keepsUnknownNativeFailures() {
        assertTrue(
            shouldTreatNoteAsPresentDuringQuickValidation(
                nativeExists = null,
                providerHasNote = false
            )
        )
    }

    @Test
    fun shouldTreatNoteAsPresentDuringQuickValidation_rejectsConfirmedMissingNotes() {
        assertFalse(
            shouldTreatNoteAsPresentDuringQuickValidation(
                nativeExists = false,
                providerHasNote = false
            )
        )
    }
}

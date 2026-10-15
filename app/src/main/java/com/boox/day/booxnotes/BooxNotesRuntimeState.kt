package com.boox.day.booxnotes

/**
 * Runtime capability state for Boox Notes baseline and Semantic indexing layer.
 *
 * Boox Notes is the baseline capability. Semantic indexing is an optional layer on top of it.
 */
enum class BooxNotesDegradedReason {
    NO_PERMISSION,
    SCANNING_IN_PROGRESS,
    INITIAL_SCAN_PENDING,
    OCR_DISABLED_ADVANCED
}

data class BooxNotesRuntimeState(
    val booxAvailable: Boolean,
    val semanticAvailable: Boolean,
    val booxReason: BooxNotesDegradedReason?,
    val semanticReason: BooxNotesDegradedReason?
)

object BooxNotesRuntimeEvaluator {
    fun evaluate(
        hasStoragePermission: Boolean,
        isScanning: Boolean,
        isReady: Boolean,
        ocrEnabled: Boolean
    ): BooxNotesRuntimeState {
        val booxReason = when {
            !hasStoragePermission -> BooxNotesDegradedReason.NO_PERMISSION
            isScanning -> BooxNotesDegradedReason.SCANNING_IN_PROGRESS
            !isReady -> BooxNotesDegradedReason.INITIAL_SCAN_PENDING
            else -> null
        }

        val semanticReason = when {
            !ocrEnabled -> BooxNotesDegradedReason.OCR_DISABLED_ADVANCED
            else -> null
        }

        return BooxNotesRuntimeState(
            booxAvailable = booxReason == null,
            semanticAvailable = semanticReason == null,
            booxReason = booxReason,
            semanticReason = semanticReason
        )
    }
}

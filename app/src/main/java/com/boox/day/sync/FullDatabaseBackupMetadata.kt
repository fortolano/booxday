package com.boox.day.sync

import kotlinx.serialization.Serializable

/**
 * Backup type selection for user preference.
 * Both types coexist - user chooses which to use.
 */
enum class BackupType {
    /** JSON backup: 8 entities (LocalCalendars, LocalEvents, Tasks, DayPages, Pages, Strokes, TextAnnotations, Images) */
    JSON,
    /** Full database backup: All 23 entities + FTS4 + indices + triggers */
    FULL_DATABASE
}

/**
 * Metadata for a full database backup.
 * Stored as a companion JSON file alongside the SQLite database file.
 *
 * Contains:
 * - Version information for compatibility checking
 * - Device/app info for debugging
 * - SHA-256 hash for integrity verification
 * - Table counts for preview display
 * - OCR information for cost estimation
 */
@Serializable
data class FullDatabaseBackupMetadata(
    /** Always "FULL_DATABASE" to identify backup type */
    val backupType: String = "FULL_DATABASE",
    /** Metadata schema version (for future migration) */
    val version: Int = 1,
    /** Unix timestamp when backup was created */
    val createdAt: Long,
    /** App version name (e.g., "1.2.3") */
    val appVersion: String,
    /** App version code (e.g., 45) */
    val appVersionCode: Int,
    /** Room database version (e.g., 23) */
    val databaseVersion: Int,
    /** Device model (e.g., "Tab Ultra C Pro") */
    val deviceModel: String,
    /** Device manufacturer (e.g., "ONYX") */
    val deviceManufacturer: String,
    /** Android API level (e.g., 33) */
    val androidVersion: Int,
    /** Size of the database file in bytes */
    val databaseSizeBytes: Long,
    /** SHA-256 hash of the database file for integrity verification */
    val sha256Hash: String,
    /** Row counts for each table (key = table name, value = count) */
    val tableCounts: Map<String, Int>,
    /** Whether FTS4 virtual table exists */
    val hasFts4Table: Boolean,
    /** Number of pages with OCR text indexed */
    val ocrPagesIndexed: Int,
    /** Estimated cost to regenerate OCR data via Cloud Vision API (e.g., "$1.50") */
    val estimatedOcrCost: String
) {
    companion object {
        /** Cost per page for Google Cloud Vision OCR (~$0.0015 per page) */
        private const val OCR_COST_PER_PAGE = 0.0015

        /**
         * Calculate estimated OCR regeneration cost.
         *
         * @param ocrPagesIndexed Number of pages with OCR data
         * @return Formatted cost string (e.g., "$1.50")
         */
        fun calculateOcrCost(ocrPagesIndexed: Int): String {
            val cost = ocrPagesIndexed * OCR_COST_PER_PAGE
            return "$${String.format("%.2f", cost)}"
        }
    }
}

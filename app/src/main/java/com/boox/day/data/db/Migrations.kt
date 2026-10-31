package com.boox.day.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// ════════════════════════════════════════════════════════════════════════════════
// MIGRATION 16 → 17: Task Due Dates Feature (Dec 2025)
// ════════════════════════════════════════════════════════════════════════════════
//
// This migration adds the dueDate field to Task for deadline tracking.
// - dueDate: Epoch milliseconds when task is due (nullable)
// - Index for efficient overdue task queries
//
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add dueDate column to Task table
        db.execSQL("ALTER TABLE task ADD COLUMN dueDate INTEGER DEFAULT NULL")

        // Create index for efficient due date queries (overdue tasks, due today, etc.)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_task_dueDate ON task(dueDate)")
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// MIGRATION 17 → 18: Multi-Provider Calendar Sync (Dec 2025)
// ════════════════════════════════════════════════════════════════════════════════
//
// This migration adds support for multiple calendar providers (Google, Outlook)
// and multiple accounts per provider.
//
// New tables:
// - connected_accounts: Stores connected Google/Outlook accounts
// - calendar_selections: Stores per-calendar sync settings
//
// Modified tables:
// - calendar_events: Added providerId, accountId, accountEmail for multi-provider support
//
// Migration preserves ALL existing events with providerId="GOOGLE".
// Post-migration code in CalendarMigrationHelper assigns accountId/email.
//
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ═══════════════════════════════════════════════════════════════════════
        // Step 1: Create connected_accounts table
        // ═══════════════════════════════════════════════════════════════════════
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS connected_accounts (
                id TEXT NOT NULL PRIMARY KEY,
                providerId TEXT NOT NULL,
                accountType TEXT NOT NULL,
                email TEXT NOT NULL,
                displayName TEXT,
                isActive INTEGER NOT NULL DEFAULT 1,
                createdAt INTEGER NOT NULL,
                lastSyncAt INTEGER
            )
        """.trimIndent())

        // Indices for connected_accounts
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_connected_accounts_email ON connected_accounts(email)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_connected_accounts_providerId ON connected_accounts(providerId)")

        // ═══════════════════════════════════════════════════════════════════════
        // Step 2: Create calendar_selections table with FK
        // ═══════════════════════════════════════════════════════════════════════
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS calendar_selections (
                id TEXT NOT NULL PRIMARY KEY,
                accountId TEXT NOT NULL,
                calendarId TEXT NOT NULL,
                calendarName TEXT NOT NULL,
                calendarColor TEXT,
                isFetchEnabled INTEGER NOT NULL DEFAULT 0,
                isDefaultForAccount INTEGER NOT NULL DEFAULT 0,
                displayOrder INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY (accountId) REFERENCES connected_accounts(id) ON DELETE CASCADE
            )
        """.trimIndent())

        // Indices for calendar_selections
        db.execSQL("CREATE INDEX IF NOT EXISTS index_calendar_selections_accountId ON calendar_selections(accountId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_calendar_selections_accountId_calendarId ON calendar_selections(accountId, calendarId)")

        // ═══════════════════════════════════════════════════════════════════════
        // Step 3: Add new columns to calendar_events
        // ═══════════════════════════════════════════════════════════════════════
        // DEFAULT 'GOOGLE' ensures existing events are tagged correctly
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN providerId TEXT NOT NULL DEFAULT 'GOOGLE'")
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN accountId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN accountEmail TEXT NOT NULL DEFAULT ''")

        // ═══════════════════════════════════════════════════════════════════════
        // Step 4: Create new indices on calendar_events
        // ═══════════════════════════════════════════════════════════════════════
        db.execSQL("CREATE INDEX IF NOT EXISTS index_calendar_events_dayId ON calendar_events(dayId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_calendar_events_accountId ON calendar_events(accountId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_calendar_events_provider_account_calendar ON calendar_events(providerId, accountId, calendarId)")
    }
}

val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "DELETE FROM Page " +
                    "WHERE notebookId IS NOT NULL " +
                    "AND notebookId NOT IN (SELECT id FROM Notebook);"
        )
    }
}

val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE boox_note_index RENAME TO boox_note_index_old")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `boox_note_index` (
                `uniqueId` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `originalDate` TEXT NOT NULL,
                `originalTimestamp` INTEGER NOT NULL,
                `pageCount` INTEGER NOT NULL,
                `pageIdsJson` TEXT NOT NULL,
                `shapeFileModified` INTEGER NOT NULL,
                `indexedAt` INTEGER NOT NULL,
                `titleSource` TEXT,
                `dateSource` TEXT,
                `pageSource` TEXT,
                `previewStatus` TEXT,
                `hasTextContent` INTEGER NOT NULL,
                `warningsJson` TEXT,
                PRIMARY KEY(`uniqueId`)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO `boox_note_index` (
                `uniqueId`,
                `title`,
                `originalDate`,
                `originalTimestamp`,
                `pageCount`,
                `pageIdsJson`,
                `shapeFileModified`,
                `indexedAt`,
                `titleSource`,
                `dateSource`,
                `pageSource`,
                `previewStatus`,
                `hasTextContent`,
                `warningsJson`
            )
            SELECT
                `uniqueId`,
                `title`,
                `originalDate`,
                `originalTimestamp`,
                `pageCount`,
                `pageIdsJson`,
                `shapeFileModified`,
                `indexedAt`,
                NULL,
                NULL,
                NULL,
                NULL,
                0,
                NULL
            FROM `boox_note_index_old`
            """.trimIndent()
        )

        db.execSQL("DROP TABLE `boox_note_index_old`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_boox_note_index_originalDate` ON `boox_note_index` (`originalDate`)")
    }
}

val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE boox_note_links RENAME TO boox_note_links_old")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `boox_note_links` (
                `id` TEXT NOT NULL,
                `noteUniqueId` TEXT NOT NULL,
                `linkedEventId` TEXT,
                `linkedDayDate` TEXT NOT NULL,
                `isManualLink` INTEGER NOT NULL,
                `confidence` TEXT NOT NULL,
                `confidenceScore` INTEGER NOT NULL,
                `matchReasons` TEXT,
                `suggestedEventIds` TEXT,
                `dismissedEventIds` TEXT,
                `needsAttention` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `lastReviewedAt` INTEGER,
                `decisionMode` TEXT NOT NULL,
                `engineVersion` TEXT,
                `dataQualityFlagsJson` TEXT,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`linkedEventId`) REFERENCES `calendar_events`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO `boox_note_links` (
                `id`,
                `noteUniqueId`,
                `linkedEventId`,
                `linkedDayDate`,
                `isManualLink`,
                `confidence`,
                `confidenceScore`,
                `matchReasons`,
                `suggestedEventIds`,
                `dismissedEventIds`,
                `needsAttention`,
                `createdAt`,
                `updatedAt`,
                `lastReviewedAt`,
                `decisionMode`,
                `engineVersion`,
                `dataQualityFlagsJson`
            )
            SELECT
                `id`,
                `noteUniqueId`,
                `linkedEventId`,
                `linkedDayDate`,
                `isManualLink`,
                `confidence`,
                `confidenceScore`,
                `matchReasons`,
                `suggestedEventIds`,
                `dismissedEventIds`,
                `needsAttention`,
                `createdAt`,
                `updatedAt`,
                `lastReviewedAt`,
                'LEGACY',
                NULL,
                NULL
            FROM `boox_note_links_old`
            """.trimIndent()
        )

        db.execSQL("DROP TABLE `boox_note_links_old`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_boox_note_links_noteUniqueId` ON `boox_note_links` (`noteUniqueId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_boox_note_links_linkedEventId` ON `boox_note_links` (`linkedEventId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_boox_note_links_linkedDayDate` ON `boox_note_links` (`linkedDayDate`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_boox_note_links_needsAttention` ON `boox_note_links` (`needsAttention`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `boox_note_semantic_aggregate` (
                `noteUniqueId` TEXT NOT NULL,
                `samplePageIdsJson` TEXT NOT NULL,
                `samplePageCount` INTEGER NOT NULL,
                `sourceModifiedAt` INTEGER NOT NULL,
                `configHashUsed` TEXT NOT NULL,
                `aggregateSynopsis` TEXT,
                `docStyle` TEXT,
                `keywordsJson` TEXT,
                `keyPhrasesJson` TEXT,
                `keySentencesJson` TEXT,
                `entitiesJson` TEXT,
                `actionItemsJson` TEXT,
                `temporalSignalsJson` TEXT,
                `topicsJson` TEXT,
                `domain` TEXT,
                `domainConfidence` REAL NOT NULL,
                `intentsJson` TEXT,
                `subdomainHint` TEXT,
                `wordCount` INTEGER NOT NULL,
                `confidence` REAL NOT NULL,
                `coverageFlagsJson` TEXT,
                `aggregateVersion` INTEGER NOT NULL,
                `rebuiltAt` INTEGER NOT NULL,
                PRIMARY KEY(`noteUniqueId`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_boox_note_semantic_aggregate_domain` ON `boox_note_semantic_aggregate` (`domain`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_boox_note_semantic_aggregate_rebuiltAt` ON `boox_note_semantic_aggregate` (`rebuiltAt`)")
    }
}

val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE task ADD COLUMN templateId TEXT DEFAULT NULL")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_templateId` ON `task` (`templateId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recurring_task_template` (
                `id` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `defaultPriority` TEXT NOT NULL,
                `defaultEstimatedMinutes` INTEGER,
                `recurrenceType` TEXT NOT NULL,
                `daysOfWeek` TEXT,
                `dayOfMonth` INTEGER,
                `intervalDays` INTEGER NOT NULL,
                `anchorDate` TEXT NOT NULL,
                `endDate` TEXT,
                `isActive` INTEGER NOT NULL,
                `isHabit` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_task_template_isActive` ON `recurring_task_template` (`isActive`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_task_template_anchorDate` ON `recurring_task_template` (`anchorDate`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_task_template_endDate` ON `recurring_task_template` (`endDate`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recurring_task_exception` (
                `id` TEXT NOT NULL,
                `templateId` TEXT NOT NULL,
                `occurrenceDate` TEXT NOT NULL,
                `exceptionType` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`templateId`) REFERENCES `recurring_task_template`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_task_exception_templateId` ON `recurring_task_exception` (`templateId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_task_exception_occurrenceDate` ON `recurring_task_exception` (`occurrenceDate`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_recurring_task_exception_templateId_occurrenceDate` ON `recurring_task_exception` (`templateId`, `occurrenceDate`)")
    }
}

// Migration 9 -> 10: Add typedText column to Task table
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE task ADD COLUMN typedText TEXT")
    }
}

// Migration 10 -> 11: Add LocalEvent table for standalone/hybrid calendar mode
// This migration creates the local_events table and migrates existing calendar_events data
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create new local_events table with hybrid schema
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS local_events (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                dayId TEXT NOT NULL,
                startTime INTEGER NOT NULL,
                endTime INTEGER,
                isAllDay INTEGER NOT NULL DEFAULT 0,
                location TEXT,
                description TEXT,
                colorHex TEXT,
                attendees TEXT,
                syncEnabled INTEGER NOT NULL DEFAULT 0,
                syncStatus TEXT NOT NULL DEFAULT 'LOCAL_ONLY',
                googleEventId TEXT,
                googleCalendarId TEXT,
                googleCalendarName TEXT,
                lastSyncedAt INTEGER,
                serverEtag TEXT,
                isReadOnly INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                linkedMemoPageId TEXT
            )
        """.trimIndent())

        // Create indices for performance
        db.execSQL("CREATE INDEX IF NOT EXISTS index_local_events_dayId ON local_events(dayId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_local_events_googleEventId ON local_events(googleEventId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_local_events_syncStatus ON local_events(syncStatus)")

        // Migrate existing calendar_events to local_events
        // - Generate new UUID for local id (using the Google ID as a seed via randomblob)
        // - Mark all as syncEnabled = true (they came from Google)
        // - Mark all as syncStatus = 'SYNCED' (they're in sync)
        // - Copy Google ID to googleEventId field
        // Note: We use hex(randomblob(16)) to generate UUID-like strings
        db.execSQL("""
            INSERT INTO local_events (
                id, title, dayId, startTime, endTime, isAllDay, location, description,
                colorHex, attendees, syncEnabled, syncStatus, googleEventId, googleCalendarId,
                googleCalendarName, lastSyncedAt, isReadOnly, createdAt, updatedAt, linkedMemoPageId
            )
            SELECT
                lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-' || hex(randomblob(2)) || '-' || hex(randomblob(2)) || '-' || hex(randomblob(6))),
                title,
                dayId,
                startTime,
                endTime,
                isAllDay,
                location,
                description,
                colorHex,
                attendees,
                1,
                'SYNCED',
                id,
                calendarId,
                calendarName,
                syncedAt,
                isReadOnly,
                syncedAt,
                syncedAt,
                NULL
            FROM calendar_events
        """.trimIndent())

        // Keep calendar_events table for backward compatibility during transition
        // Can be dropped in a future migration (e.g., MIGRATION_12_13) after stability confirmed
    }
}

// Migration 11 -> 12: Add LocalCalendar table and localCalendarId field to LocalEvent
// This migration:
// 1. Creates the local_calendars table
// 2. Creates the Default calendar
// 3. Adds localCalendarId column to local_events
// 4. Assigns all existing local events (syncEnabled=0 OR LOCAL_ONLY status) to Default
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Step 1: Create local_calendars table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS local_calendars (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                colorHex TEXT,
                sortOrder INTEGER NOT NULL,
                isDefault INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL
            )
        """.trimIndent())

        // Step 2: Create index on sortOrder
        db.execSQL("""
            CREATE INDEX IF NOT EXISTS index_local_calendars_sortOrder
            ON local_calendars (sortOrder)
        """.trimIndent())

        // Step 3: Seed the Default calendar with a generated UUID
        // Using SQLite's randomblob to generate a UUID-like string
        val defaultId = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        db.execSQL("""
            INSERT INTO local_calendars (id, name, colorHex, sortOrder, isDefault, createdAt)
            VALUES ('$defaultId', 'Default', NULL, 0, 1, $now)
        """.trimIndent())

        // Step 4: Add localCalendarId column to local_events
        db.execSQL("""
            ALTER TABLE local_events ADD COLUMN localCalendarId TEXT
        """.trimIndent())

        // Step 5: Create index on localCalendarId
        db.execSQL("""
            CREATE INDEX IF NOT EXISTS index_local_events_localCalendarId
            ON local_events (localCalendarId)
        """.trimIndent())

        // Step 6: Assign all existing local-only events to the Default calendar
        // Local events are those with syncEnabled=0 OR syncStatus='LOCAL_ONLY'
        db.execSQL("""
            UPDATE local_events
            SET localCalendarId = '$defaultId'
            WHERE syncEnabled = 0 OR syncStatus = 'LOCAL_ONLY'
        """.trimIndent())
    }
}

// Migration 3 -> 4: Add TextAnnotation table for handwriting recognition and voice input
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `text_annotations` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `content` TEXT NOT NULL,
                `x` REAL NOT NULL,
                `y` REAL NOT NULL,
                `width` REAL NOT NULL,
                `height` REAL NOT NULL,
                `fontSize` REAL NOT NULL DEFAULT 16,
                `fontFamily` TEXT NOT NULL DEFAULT 'sans-serif',
                `fontWeight` INTEGER NOT NULL DEFAULT 400,
                `fontStyle` TEXT NOT NULL DEFAULT 'normal',
                `textColor` INTEGER NOT NULL DEFAULT -16777216,
                `backgroundColor` INTEGER NOT NULL DEFAULT 0,
                `textAlign` TEXT NOT NULL DEFAULT 'left',
                `padding` REAL NOT NULL DEFAULT 8,
                `pageId` TEXT NOT NULL,
                `sourceType` TEXT NOT NULL DEFAULT 'handwriting',
                `sourceStrokeIds` TEXT,
                `recognitionLanguage` TEXT,
                `recognitionConfidence` REAL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                FOREIGN KEY(`pageId`) REFERENCES `Page`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_text_annotations_pageId` ON `text_annotations`(`pageId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_text_annotations_content` ON `text_annotations`(`content`)")
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// Migration 12 -> 13: Enhanced Boox Notes linking with confidence levels (Dec 2025)
// ════════════════════════════════════════════════════════════════════════════════
//
// This migration adds new columns to boox_note_links for the enhanced auto-linking
// algorithm with confidence levels, match reasons, and suggestions.
//
// New columns:
// - linkedDayDate: Every note belongs to a day (no orphans)
// - confidence: "HIGH", "MEDIUM", "LOW", "NONE"
// - confidenceScore: Raw score (0-200+)
// - matchReasons: JSON array of MatchReason enum names
// - suggestedEventIds: JSON array of alternative event IDs
// - dismissedEventIds: JSON array of user-dismissed suggestions
// - needsAttention: Boolean flag for quick filtering
// - lastReviewedAt: Timestamp of last user interaction
//
// New indices:
// - linkedDayDate: For day-based queries
// - needsAttention: For attention list queries
//
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add new columns to boox_note_links
        db.execSQL("ALTER TABLE boox_note_links ADD COLUMN linkedDayDate TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE boox_note_links ADD COLUMN confidence TEXT NOT NULL DEFAULT 'NONE'")
        db.execSQL("ALTER TABLE boox_note_links ADD COLUMN confidenceScore INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE boox_note_links ADD COLUMN matchReasons TEXT")
        db.execSQL("ALTER TABLE boox_note_links ADD COLUMN suggestedEventIds TEXT")
        db.execSQL("ALTER TABLE boox_note_links ADD COLUMN dismissedEventIds TEXT")
        db.execSQL("ALTER TABLE boox_note_links ADD COLUMN needsAttention INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE boox_note_links ADD COLUMN lastReviewedAt INTEGER")

        // Create new indices
        db.execSQL("CREATE INDEX IF NOT EXISTS index_boox_note_links_linkedDayDate ON boox_note_links(linkedDayDate)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_boox_note_links_needsAttention ON boox_note_links(needsAttention)")

        // Backfill linkedDayDate from boox_note_index
        // This ensures no orphans - every link gets its note's originalDate
        db.execSQL("""
            UPDATE boox_note_links
            SET linkedDayDate = (
                SELECT originalDate FROM boox_note_index
                WHERE boox_note_index.uniqueId = boox_note_links.noteUniqueId
            )
            WHERE linkedDayDate = ''
        """.trimIndent())
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// Migration 13 -> 14: Add SummaryCache table for Daily/Weekly summary caching
// ════════════════════════════════════════════════════════════════════════════════
//
// This migration adds a cache table for AI-generated Daily and Weekly summaries.
// Caching avoids regenerating expensive AI summaries on every request.
// - Daily summaries: cached for 4 hours
// - Weekly summaries: cached for 20 hours
//
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS summary_cache (
                id TEXT PRIMARY KEY NOT NULL,
                type TEXT NOT NULL,
                dateKey TEXT NOT NULL,
                resultJson TEXT NOT NULL,
                generatedAt INTEGER NOT NULL,
                validHours INTEGER NOT NULL
            )
        """.trimIndent())

        // Index for quick lookups by type
        db.execSQL("CREATE INDEX IF NOT EXISTS index_summary_cache_type ON summary_cache(type)")
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// MIGRATION 14 → 15: Parent-Child Task Relationships
// ════════════════════════════════════════════════════════════════════════════════
//
// This migration adds parentTaskId column to the Task table to support
// parent-child relationships for the Break Down Task feature.
// - Parent tasks have parentTaskId = NULL
// - Child tasks (subtasks) have parentTaskId pointing to their parent's id
// - ON DELETE SET NULL: children become orphans if parent is deleted
//
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add parentTaskId column (nullable, defaults to NULL for existing tasks)
        db.execSQL("ALTER TABLE task ADD COLUMN parentTaskId TEXT DEFAULT NULL")

        // Create index for efficient child lookups
        db.execSQL("CREATE INDEX IF NOT EXISTS index_task_parentTaskId ON task(parentTaskId)")
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// MIGRATION 15 → 16: Rollover Tracking for Agentic Planning System
// ════════════════════════════════════════════════════════════════════════════════
//
// This migration adds rollover tracking fields to the Task table to support
// the Agentic Planning System's "zombie task" detection feature.
//
// New columns:
// - migrationCount: Number of times task was moved to a different day (0 = never moved)
// - originalDate: The day this task was first created (set when first migrated)
// - lastMigratedAt: Epoch ms timestamp of the most recent migration
// - estimatedMinutes: Duration estimate for capacity planning (AI-assigned)
//
// A task is considered a "zombie" when migrationCount >= 3, indicating it has
// been repeatedly deferred and may need user attention (break down, delegate, or drop).
//
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add rollover tracking columns
        db.execSQL("ALTER TABLE task ADD COLUMN migrationCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE task ADD COLUMN originalDate TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE task ADD COLUMN lastMigratedAt INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE task ADD COLUMN estimatedMinutes INTEGER DEFAULT NULL")

        // Create index for efficient zombie queries (incomplete + high migration count)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_task_migrationCount ON task(migrationCount)")
    }
}

// Note: MIGRATION_18_19 and MIGRATION_19_20 removed - app not launched yet.
// Room auto-creates UnifiedOcrPage and UnifiedOcrText tables from entity annotations.
// FTS4 virtual table and triggers are created via UnifiedOcrDao callbacks.

// ════════════════════════════════════════════════════════════════════════════════
// MIGRATION 20 → 21: SemanticGenius Domain/Intent Classification (Jan 2026)
// ════════════════════════════════════════════════════════════════════════════════
//
// This migration adds domain/intent classification columns to unified_ocr_text.
// The semantic engine computes these but they weren't being persisted.
//
// New columns:
// - domain: Primary classification (WORK, SCHOOL, LDS_LEADERSHIP, etc.)
// - domainConfidence: Confidence score (0.0-1.0)
// - intentsJson: JSON array of [{intent, confidence}]
// - subdomainHint: For SPIRITUAL domain (lds, christian, etc.)
// - classificationExplainJson: Full debug trace for transparency
//
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add domain/intent classification columns to unified_ocr_text
        db.execSQL("ALTER TABLE unified_ocr_text ADD COLUMN domain TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE unified_ocr_text ADD COLUMN domainConfidence REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE unified_ocr_text ADD COLUMN intentsJson TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE unified_ocr_text ADD COLUMN subdomainHint TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE unified_ocr_text ADD COLUMN classificationExplainJson TEXT DEFAULT NULL")

        // Create index for domain-based queries (filter by WORK, SCHOOL, etc.)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_unified_ocr_text_domain ON unified_ocr_text(domain)")
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// MIGRATION 21 → 22: OCR Quota Tracking (Jan 2026)
// ════════════════════════════════════════════════════════════════════════════════
//
// This migration adds firstIndexedAt column to unified_ocr_page for quota tracking.
// Used to distinguish new pages (cost budget) vs re-indexed pages (free).
//
// - firstIndexedAt: Epoch ms when page was FIRST indexed (0 = never)
// - Re-indexing preserves this timestamp, so we can track:
//   - New pages: firstIndexedAt == 0 -> costs quota budget
//   - Re-indexed pages: firstIndexedAt > 0 -> free (no budget cost)
//
val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add firstIndexedAt column to unified_ocr_page
        // Default 0 means "never indexed before" for existing rows
        db.execSQL("ALTER TABLE unified_ocr_page ADD COLUMN firstIndexedAt INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v27 (plan T2, Oct 2026):
 * 1. Due dates become local calendar days: new `dueDay` TEXT ("yyyy-MM-dd") filled from the old
 *    epoch-ms `dueDate` in the device's zone. `dueDate` stays as a legacy column (never written).
 * 2. One occurrence per recurring template per day: rows that duplicate an earlier row's
 *    (dayDate, templateId) keep their content but lose the template link, then a unique index
 *    makes further duplicates impossible.
 */
val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE task ADD COLUMN dueDay TEXT DEFAULT NULL")
        db.execSQL(
            "UPDATE task SET dueDay = date(dueDate / 1000, 'unixepoch', 'localtime') WHERE dueDate IS NOT NULL"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_dueDay` ON `task` (`dueDay`)")

        db.execSQL(
            """
            UPDATE task SET templateId = NULL
            WHERE templateId IS NOT NULL
            AND rowid NOT IN (
                SELECT MIN(rowid) FROM task WHERE templateId IS NOT NULL GROUP BY dayDate, templateId
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_task_dayDate_templateId` ON `task` (`dayDate`, `templateId`)"
        )
    }
}

/** v28 (plan T3 / 13.6A, Oct 2026): optional per-task reminder time, minutes after midnight. */
val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE task ADD COLUMN reminderMinuteOfDay INTEGER DEFAULT NULL")
    }
}

/**
 * v29 (open-source cleanup, Oct 2026): the What's New center is gone. Its messages came only from the
 * old commercial server's announcements, so the table is dropped (its indices go with it).
 */
val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `whats_new_message`")
    }
}

/**
 * v30 (task sync groundwork, Oct 2026):
 * - connected_accounts: `serverUrl` ("" for Google and Outlook) and one account per
 *   provider + server + email instead of per email, so an iCloud account whose Apple ID is a
 *   Gmail address can sit next to that Google account (CalDAV, later).
 * - task_sync_link / task_sync_outbox / task_sync_list (TaskSync.kt) and the trigger that turns a
 *   linked task row's deletion into an outbox DELETE.
 */
val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `connected_accounts` ADD COLUMN `serverUrl` TEXT NOT NULL DEFAULT ''")
        db.execSQL("DROP INDEX IF EXISTS `index_connected_accounts_email`")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_connected_accounts_providerId_serverUrl_email` " +
                "ON `connected_accounts` (`providerId`, `serverUrl`, `email`)"
        )
        // As in schemas/.../30.json
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `task_sync_link` (`taskId` TEXT NOT NULL, `provider` TEXT NOT NULL, " +
                "`accountEmail` TEXT NOT NULL, `serverUrl` TEXT NOT NULL DEFAULT '', `listId` TEXT NOT NULL, " +
                "`remoteId` TEXT NOT NULL, `remoteVersion` TEXT, `syncedTitle` TEXT, `syncedCompleted` INTEGER NOT NULL, " +
                "`syncedDueDay` TEXT, `syncedParentRemoteId` TEXT, `contentHash` TEXT, `remoteUndated` INTEGER NOT NULL, " +
                "`orphanedAt` INTEGER, `syncedAt` INTEGER NOT NULL, PRIMARY KEY(`taskId`))"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_task_sync_link_provider_accountEmail_serverUrl_listId_remoteId` " +
                "ON `task_sync_link` (`provider`, `accountEmail`, `serverUrl`, `listId`, `remoteId`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `task_sync_outbox` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`op` TEXT NOT NULL, `taskId` TEXT NOT NULL, `provider` TEXT, `accountEmail` TEXT, `serverUrl` TEXT, " +
                "`listId` TEXT, `remoteId` TEXT, `createdAt` INTEGER NOT NULL, `attempts` INTEGER NOT NULL, " +
                "`nextAttemptAt` INTEGER NOT NULL, `lastError` TEXT)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_sync_outbox_taskId` ON `task_sync_outbox` (`taskId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_sync_outbox_nextAttemptAt` ON `task_sync_outbox` (`nextAttemptAt`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `task_sync_list` (`provider` TEXT NOT NULL, `accountEmail` TEXT NOT NULL, " +
                "`serverUrl` TEXT NOT NULL DEFAULT '', `listId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`isDefaultForNew` INTEGER NOT NULL, `pullCursor` TEXT, `lastFullCheckAt` INTEGER, " +
                "PRIMARY KEY(`provider`, `accountEmail`, `serverUrl`, `listId`))"
        )
        TaskSyncSchema.createTriggersV30(db)
    }
}

/**
 * v30 → v31 (task sync merge, Oct 2026). Plan: C:\Users\fort\.claude\plans\reactive-wiggling-micali.md, 2.3.
 * - task_sync_link.remoteParentId: the parent the service's copy has now. Each side keeps its own
 *   subtasks (owner decision 24), so it can differ from syncedParentRemoteId (the tablet's grouping
 *   as last sent); until now they were the same, so it starts as a copy.
 * - task_sync_link.keptOnTablet: the task stays on the tablet only and is never sent (deleted in the
 *   service while changed or ticked here; ticked when task sync was turned on).
 * - The row-deleted trigger leaves out such a task: it has no copy to delete.
 */
val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `task_sync_link` ADD COLUMN `remoteParentId` TEXT")
        db.execSQL("UPDATE `task_sync_link` SET `remoteParentId` = `syncedParentRemoteId`")
        db.execSQL("ALTER TABLE `task_sync_link` ADD COLUMN `keptOnTablet` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("DROP TRIGGER IF EXISTS `${TaskSyncSchema.ROW_DELETED_TRIGGER}`")
        TaskSyncSchema.createTriggers(db)
    }
}

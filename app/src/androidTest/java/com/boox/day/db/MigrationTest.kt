package com.boox.day.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.MIGRATION_23_24
import com.boox.day.data.db.MIGRATION_24_25
import com.boox.day.data.db.MIGRATION_25_26
import com.boox.day.data.db.MIGRATION_26_27
import com.boox.day.data.db.MIGRATION_27_28
import com.boox.day.data.db.MIGRATION_28_29
import com.boox.day.data.db.MIGRATION_29_30
import com.boox.day.data.db.MIGRATION_30_31
import com.boox.day.data.db.TaskSyncSchema
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private data class ColumnInfo(
        val name: String,
        val notNull: Boolean,
        val defaultValue: String?
    )

    @Test
    fun simpleTest() {
        assertTrue(true)
    }

    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        listOf(), // Add AutoMigrationSpecs here if any
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate23To24_addsBooxNoteIndexColumns() {
        val dbName = "migration-test-23-24"
        helper.createDatabase(dbName, 23).apply {
            insertLegacyBooxNoteIndexRow(this)
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(
            dbName,
            24,
            true,
            MIGRATION_23_24
        )

        val columns = getColumnInfo(migratedDb, "boox_note_index")
        assertTrue(columns.containsKey("titleSource"))
        assertTrue(columns.containsKey("dateSource"))
        assertTrue(columns.containsKey("pageSource"))
        assertTrue(columns.containsKey("previewStatus"))
        assertTrue(columns.containsKey("warningsJson"))
        assertTrue(columns["hasTextContent"]?.notNull == true)
        assertEquals(null, columns["hasTextContent"]?.defaultValue)

        migratedDb.query(
            "SELECT titleSource, dateSource, pageSource, previewStatus, hasTextContent, warningsJson " +
                "FROM boox_note_index WHERE uniqueId = 'note-1'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
            assertTrue(cursor.isNull(1))
            assertTrue(cursor.isNull(2))
            assertTrue(cursor.isNull(3))
            assertEquals(0, cursor.getInt(4))
            assertTrue(cursor.isNull(5))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate24To25_addsSmartLinkColumnsAndSemanticAggregateTable() {
        val dbName = "migration-test-24-25"
        helper.createDatabase(dbName, 24).apply {
            insertLegacyBooxNoteLinkRow(this)
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(
            dbName,
            25,
            true,
            MIGRATION_24_25
        )

        val linkColumns = getColumnInfo(migratedDb, "boox_note_links")
        assertTrue(linkColumns["decisionMode"]?.notNull == true)
        assertEquals(null, linkColumns["decisionMode"]?.defaultValue)
        assertTrue(linkColumns.containsKey("engineVersion"))
        assertTrue(linkColumns.containsKey("dataQualityFlagsJson"))

        migratedDb.query(
            "SELECT decisionMode, engineVersion, dataQualityFlagsJson " +
                "FROM boox_note_links WHERE id = 'link-1'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("LEGACY", cursor.getString(0))
            assertTrue(cursor.isNull(1))
            assertTrue(cursor.isNull(2))
        }

        assertTrue(tableExists(migratedDb, "boox_note_semantic_aggregate"))
        val aggregateColumns = getColumnInfo(migratedDb, "boox_note_semantic_aggregate")
        assertTrue(aggregateColumns["domainConfidence"]?.notNull == true)
        assertTrue(aggregateColumns["aggregateVersion"]?.notNull == true)
        assertTrue(aggregateColumns["rebuiltAt"]?.notNull == true)

        val aggregateIndexes = getIndexNames(migratedDb, "boox_note_semantic_aggregate")
        assertTrue("index_boox_note_semantic_aggregate_domain" in aggregateIndexes)
        assertTrue("index_boox_note_semantic_aggregate_rebuiltAt" in aggregateIndexes)
    }

    @Test
    @Throws(IOException::class)
    fun migrate23To25_fullPath_preservesNoteData() {
        val dbName = "migration-test-23-25"
        helper.createDatabase(dbName, 23).apply {
            insertLegacyBooxNoteIndexRow(this)
            insertLegacyBooxNoteLinkRow(this)
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(
            dbName,
            25,
            true,
            MIGRATION_23_24,
            MIGRATION_24_25
        )

        migratedDb.query(
            "SELECT uniqueId, title, hasTextContent FROM boox_note_index WHERE uniqueId = 'note-1'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("note-1", cursor.getString(0))
            assertEquals("Meeting Notes", cursor.getString(1))
            assertEquals(0, cursor.getInt(2))
        }

        migratedDb.query(
            "SELECT noteUniqueId, linkedDayDate, decisionMode FROM boox_note_links WHERE id = 'link-1'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("note-1", cursor.getString(0))
            assertEquals("2026-03-11", cursor.getString(1))
            assertEquals("LEGACY", cursor.getString(2))
        }

        assertTrue(tableExists(migratedDb, "boox_note_semantic_aggregate"))
    }

    @Test
    @Throws(IOException::class)
    fun migrate25To26_addsRecurringTaskTablesAndTemplateLink() {
        val dbName = "migration-test-25-26"
        helper.createDatabase(dbName, 25).apply {
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(
            dbName,
            26,
            true,
            MIGRATION_25_26
        )

        val taskColumns = getColumnInfo(migratedDb, "task")
        assertTrue(taskColumns.containsKey("templateId"))
        assertEquals(null, taskColumns["templateId"]?.defaultValue)

        val taskIndexes = getIndexNames(migratedDb, "task")
        assertTrue("index_task_templateId" in taskIndexes)

        assertTrue(tableExists(migratedDb, "recurring_task_template"))
        val templateColumns = getColumnInfo(migratedDb, "recurring_task_template")
        assertTrue(templateColumns["title"]?.notNull == true)
        assertTrue(templateColumns["recurrenceType"]?.notNull == true)
        assertTrue(templateColumns["anchorDate"]?.notNull == true)

        val templateIndexes = getIndexNames(migratedDb, "recurring_task_template")
        assertTrue("index_recurring_task_template_isActive" in templateIndexes)
        assertTrue("index_recurring_task_template_anchorDate" in templateIndexes)
        assertTrue("index_recurring_task_template_endDate" in templateIndexes)

        assertTrue(tableExists(migratedDb, "recurring_task_exception"))
        val exceptionColumns = getColumnInfo(migratedDb, "recurring_task_exception")
        assertTrue(exceptionColumns["templateId"]?.notNull == true)
        assertTrue(exceptionColumns["occurrenceDate"]?.notNull == true)
        assertTrue(exceptionColumns["exceptionType"]?.notNull == true)

        val exceptionIndexes = getIndexNames(migratedDb, "recurring_task_exception")
        assertTrue("index_recurring_task_exception_templateId" in exceptionIndexes)
        assertTrue("index_recurring_task_exception_occurrenceDate" in exceptionIndexes)
        assertTrue("index_recurring_task_exception_templateId_occurrenceDate" in exceptionIndexes)
    }

    @Test
    @Throws(IOException::class)
    fun migrate26To27_addsDueDayAndOneOccurrencePerTemplatePerDay() {
        val dbName = "migration-test-26-27"
        // 2026-10-02 12:00 local time, as the <= v26 epoch-ms due date
        val dueMillis = java.time.LocalDate.of(2026, 10, 2).atTime(12, 0)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        helper.createDatabase(dbName, 26).apply {
            execSQL(
                "INSERT INTO Page (id, scroll, notebookId, background, backgroundType, parentFolderId, linkedEventId, createdAt, updatedAt) " +
                    "VALUES ('page-1', 0, NULL, 'blank', 'native', NULL, NULL, 1, 1)"
            )
            fun task(id: String, row: Int, typed: String?, due: Long?, template: String?) = execSQL(
                "INSERT INTO task (id, dayDate, taskPageIndex, rowIndex, pageId, isCompleted, priority, typedText, " +
                    "migrationCount, dueDate, templateId, createdAt, updatedAt) VALUES (?, '2026-10-01', 0, ?, 'page-1', 0, '', ?, 0, ?, ?, 1, 1)",
                arrayOf<Any?>(id, row, typed, due, template)
            )
            task("t-due", 0, "Report", dueMillis, null)
            task("t-repeat-1", 1, "Weekly review", null, "tmpl-1")
            task("t-repeat-2", 2, "Weekly review (back-filled twice)", null, "tmpl-1")
            task("t-plain", 3, null, null, null)
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(dbName, 27, true, MIGRATION_26_27)

        assertTrue(getColumnInfo(migratedDb, "task").containsKey("dueDay"))
        val indexes = getIndexNames(migratedDb, "task")
        assertTrue("index_task_dueDay" in indexes)
        assertTrue("index_task_dayDate_templateId" in indexes)

        migratedDb.query("SELECT id, dueDay, templateId, typedText FROM task ORDER BY rowIndex").use { c ->
            val rows = mutableMapOf<String, Triple<String?, String?, String?>>()
            while (c.moveToNext()) {
                rows[c.getString(0)] = Triple(c.getString(1), c.getString(2), c.getString(3))
            }
            // Due day filled from the epoch-ms due date, in the device's zone
            assertEquals("2026-10-02", rows["t-due"]?.first)
            assertEquals(null, rows["t-plain"]?.first)
            // First occurrence keeps the template link; the duplicate keeps its text, loses the link
            assertEquals("tmpl-1", rows["t-repeat-1"]?.second)
            assertEquals(null, rows["t-repeat-2"]?.second)
            assertEquals("Weekly review (back-filled twice)", rows["t-repeat-2"]?.third)
        }

        // A second occurrence of the same template on the same day is now refused
        var refused = false
        try {
            migratedDb.execSQL("UPDATE task SET templateId = 'tmpl-1' WHERE id = 't-plain'")
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            refused = true
        }
        assertTrue(refused)
    }

    @Test
    @Throws(IOException::class)
    fun migrate27To28_addsPerTaskReminderTime() {
        val dbName = "migration-test-27-28"
        helper.createDatabase(dbName, 27).apply {
            execSQL(
                "INSERT INTO Page (id, scroll, notebookId, background, backgroundType, parentFolderId, linkedEventId, createdAt, updatedAt) " +
                    "VALUES ('page-1', 0, NULL, 'blank', 'native', NULL, NULL, 1, 1)"
            )
            execSQL(
                "INSERT INTO task (id, dayDate, taskPageIndex, rowIndex, pageId, isCompleted, priority, typedText, " +
                    "migrationCount, dueDay, createdAt, updatedAt) VALUES ('t-1', '2026-10-02', 0, 0, 'page-1', 0, '', 'Call', 0, '2026-10-03', 1, 1)"
            )
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(dbName, 28, true, MIGRATION_27_28)

        val column = getColumnInfo(migratedDb, "task")["reminderMinuteOfDay"]
        assertTrue(column != null)
        migratedDb.query("SELECT dueDay, reminderMinuteOfDay FROM task WHERE id = 't-1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("2026-10-03", c.getString(0))
            assertTrue(c.isNull(1))  // existing tasks keep the shared default time
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate28To29_dropsWhatsNewTableAndKeepsTasks() {
        val dbName = "migration-test-28-29"
        helper.createDatabase(dbName, 28).apply {
            execSQL(
                "INSERT INTO Page (id, scroll, notebookId, background, backgroundType, parentFolderId, linkedEventId, createdAt, updatedAt) " +
                    "VALUES ('page-1', 0, NULL, 'blank', 'native', NULL, NULL, 1, 1)"
            )
            execSQL(
                "INSERT INTO task (id, dayDate, taskPageIndex, rowIndex, pageId, isCompleted, priority, typedText, " +
                    "migrationCount, dueDay, reminderMinuteOfDay, createdAt, updatedAt) " +
                    "VALUES ('t-1', '2026-10-02', 0, 0, 'page-1', 0, '', 'Call', 0, '2026-10-03', 540, 1, 1)"
            )
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(dbName, 29, true, MIGRATION_28_29)

        assertFalse(tableExists(migratedDb, "whats_new_message"))
        migratedDb.query("SELECT typedText, dueDay, reminderMinuteOfDay FROM task WHERE id = 't-1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Call", c.getString(0))
            assertEquals("2026-10-03", c.getString(1))
            assertEquals(540, c.getInt(2))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate29To30_addsTaskSyncTablesAndAccountServer() {
        val dbName = "migration-test-29-30"
        helper.createDatabase(dbName, 29).apply {
            execSQL(
                "INSERT INTO connected_accounts (id, providerId, accountType, email, displayName, isActive, createdAt, lastSyncAt) " +
                    "VALUES ('g-1', 'GOOGLE', 'GOOGLE_PERSONAL', 'me@gmail.com', 'Me', 1, 1, NULL)"
            )
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(dbName, 30, true, MIGRATION_29_30)

        assertTrue(tableExists(migratedDb, "task_sync_link"))
        assertTrue(tableExists(migratedDb, "task_sync_outbox"))
        assertTrue(tableExists(migratedDb, "task_sync_list"))
        assertTrue(getIndexNames(migratedDb, "connected_accounts").contains("index_connected_accounts_providerId_serverUrl_email"))
        assertFalse(getIndexNames(migratedDb, "connected_accounts").contains("index_connected_accounts_email"))
        migratedDb.query("SELECT serverUrl FROM connected_accounts WHERE id = 'g-1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("", c.getString(0))
        }
        migratedDb.query(
            "SELECT 1 FROM sqlite_master WHERE type = 'trigger' AND name = '${TaskSyncSchema.ROW_DELETED_TRIGGER}'"
        ).use { c -> assertTrue(c.moveToFirst()) }
    }

    @Test
    @Throws(IOException::class)
    fun migrate30To31_tracksTheServiceParentAndKeptTasks() {
        val dbName = "migration-test-30-31"
        helper.createDatabase(dbName, 30).apply {
            execSQL(
                "INSERT INTO task_sync_link (taskId, provider, accountEmail, serverUrl, listId, remoteId, syncedCompleted, " +
                    "syncedParentRemoteId, remoteUndated, syncedAt) VALUES ('t-1', 'GOOGLE_TASKS', 'me@gmail.com', '', 'l-1', 'r-1', 0, 'r-p', 0, 1)"
            )
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(dbName, 31, true, MIGRATION_30_31)

        migratedDb.query("SELECT remoteParentId, keptOnTablet FROM task_sync_link WHERE taskId = 't-1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("r-p", c.getString(0))
            assertEquals(0, c.getInt(1))
        }
    }

    @Test
    @Ignore("Missing exported schema assets for versions 30 and 31 in this repository")
    @Throws(IOException::class)
    fun migrate30To31_autoMigration() {
        val dbName = "migration-test"

        // 1. Create DB with version 30 schema
        val db = helper.createDatabase(dbName, 30)

// Insert required parent data first
        db.execSQL(
            """
    INSERT INTO Notebook (
        id,
        title,
        openPageId,
        pageIds,
        parentFolderId,
        defaultNativeTemplate,
        createdAt,
        updatedAt
    ) VALUES (
        'notebook1',
        'Test Notebook',
        NULL,
        '[]',
        NULL,
        'blank',
        1620000000000,
        1620000000000
    )
    """.trimIndent()
        )


        db.execSQL(
            """
    INSERT INTO Folder (id, title, createdAt, updatedAt)
    VALUES ('TEST_FOLDER_ID', 'Test Folder', 1620000000, 1620000000)
    """.trimIndent()
        )

        // Insert with column name from version 30: 'nativeTemplate'
        db.execSQL(
            """
    INSERT INTO Page (
        id,
        notebookId,
        nativeTemplate,
        parentFolderId,
        scroll,
        createdAt,
        updatedAt
    ) VALUES (
        'page1',
        'notebook1',
        'grid',
        'TEST_FOLDER_ID',
        0.0,
        1620000000,
        1620000000
    )
    """.trimIndent()
        )


        db.close()

        // 2. Reopen DB with version 31 to trigger migration
        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .build().openHelper.writableDatabase

        // 3. Verify renamed column exists with expected data
        val cursor = migratedDb.query("SELECT background FROM Page WHERE id = 'page1'")
        cursor.use {
            assertTrue(it.moveToFirst())
            val background = it.getString(0)
            assertEquals("grid", background)
        }
    }

    private fun insertLegacyBooxNoteIndexRow(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO boox_note_index (
                uniqueId,
                title,
                originalDate,
                originalTimestamp,
                pageCount,
                pageIdsJson,
                shapeFileModified,
                indexedAt
            ) VALUES (
                'note-1',
                'Meeting Notes',
                '2026-03-11',
                1710115200000,
                2,
                '["page-1","page-2"]',
                1710115205000,
                1710115210000
            )
            """.trimIndent()
        )
    }

    private fun insertLegacyBooxNoteLinkRow(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO boox_note_links (
                id,
                noteUniqueId,
                linkedEventId,
                linkedDayDate,
                isManualLink,
                confidence,
                confidenceScore,
                matchReasons,
                suggestedEventIds,
                dismissedEventIds,
                needsAttention,
                createdAt,
                updatedAt,
                lastReviewedAt
            ) VALUES (
                'link-1',
                'note-1',
                NULL,
                '2026-03-11',
                0,
                'NONE',
                0,
                NULL,
                NULL,
                NULL,
                0,
                1710115215000,
                1710115220000,
                NULL
            )
            """.trimIndent()
        )
    }

    private fun getColumnInfo(db: SupportSQLiteDatabase, tableName: String): Map<String, ColumnInfo> {
        val columns = linkedMapOf<String, ColumnInfo>()
        db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val notNullIndex = cursor.getColumnIndexOrThrow("notnull")
            val defaultIndex = cursor.getColumnIndexOrThrow("dflt_value")

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex)
                columns[name] = ColumnInfo(
                    name = name,
                    notNull = cursor.getInt(notNullIndex) == 1,
                    defaultValue = if (cursor.isNull(defaultIndex)) null else cursor.getString(defaultIndex)
                )
            }
        }
        return columns
    }

    private fun getIndexNames(db: SupportSQLiteDatabase, tableName: String): Set<String> {
        val indexNames = mutableSetOf<String>()
        db.query("PRAGMA index_list(`$tableName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                indexNames += cursor.getString(nameIndex)
            }
        }
        return indexNames
    }

    private fun tableExists(db: SupportSQLiteDatabase, tableName: String): Boolean {
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(tableName)
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }
}

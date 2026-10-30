package com.boox.day.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.boox.day.BuildConfig
import com.boox.day.data.getDbDir
import com.boox.day.data.db.MIGRATION_9_10
import com.boox.day.data.db.MIGRATION_10_11
import com.boox.day.data.db.MIGRATION_11_12
import com.boox.day.data.db.MIGRATION_12_13
import com.boox.day.data.db.MIGRATION_13_14
import com.boox.day.data.db.MIGRATION_14_15
import com.boox.day.data.db.MIGRATION_22_23
import com.boox.day.data.db.MIGRATION_23_24
import com.boox.day.data.db.MIGRATION_24_25
import com.boox.day.data.db.MIGRATION_25_26
import com.boox.day.data.db.MIGRATION_26_27
import com.boox.day.data.db.MIGRATION_27_28
import com.boox.day.data.db.MIGRATION_28_29
import com.boox.day.data.db.MIGRATION_21_22
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Date

private const val CONVERTERS_TAG = "Converters"

class Converters {
    @TypeConverter
    fun fromListString(value: List<String>) = Json.encodeToString(value)

    @TypeConverter
    fun toListString(value: String) = Json.decodeFromString<List<String>>(value)


    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromStrokePoints(points: List<StrokePoint>?): ByteArray? {
        if (points == null) return null
        // A row whose blob couldn't be decoded must never be written back: its original bytes
        // stay in the database untouched (they may still be recoverable).
        check(!points.isUndecodableStrokePoints()) { "Refusing to overwrite an undecodable stroke blob" }
        return try {
            val mask = computeStrokeMask(points)
            encodeStrokePoints(points, mask)
        } catch (t: Throwable) {
            Log.e(
                CONVERTERS_TAG,
                "STROKE_ENCODE_ERROR|" +
                    "appVersion=${BuildConfig.VERSION_NAME}|" +
                    "versionCode=${BuildConfig.VERSION_CODE}|" +
                    "pointsCount=${points.size}|" +
                    "exceptionClass=${t.javaClass.name}|" +
                    "exceptionMessage=${t.message}",
                t
            )
            throw t
        }
    }

    @TypeConverter
    fun toStrokePoints(bytes: ByteArray?): List<StrokePoint>? {
        if (bytes == null || bytes.isEmpty()) return emptyList()
        return try {
            decodeStrokePoints(bytes)
        } catch (t: Throwable) {
            Log.e(
                CONVERTERS_TAG,
                "STROKE_DECODE_ERROR|" +
                    "appVersion=${BuildConfig.VERSION_NAME}|" +
                    "versionCode=${BuildConfig.VERSION_CODE}|" +
                    "bytesSize=${bytes.size}|" +
                    "exceptionClass=${t.javaClass.name}|" +
                    "exceptionMessage=${t.message}",
                t
            )
            // One damaged row must not fail the whole query (page load, backup export):
            // return the marker instead. Readers skip it; the converter above refuses to store it.
            UNDECODABLE_STROKE_POINTS
        }
    }
}


@Database(
    entities = [Folder::class, Notebook::class, Page::class, Stroke::class, Image::class, Kv::class, DayPage::class, CalendarEvent::class, TextAnnotation::class, BooxNoteIndex::class, BooxNoteLink::class, BooxNoteSemanticAggregate::class, Task::class, RecurringTaskTemplate::class, RecurringTaskException::class, Attachment::class, LocalEvent::class, LocalCalendar::class, SummaryCache::class, ConnectedAccountEntity::class, CalendarSelectionEntity::class, UnifiedOcrPage::class, UnifiedOcrText::class, EntityCooccurrence::class, UserActivityPattern::class, TaskSyncLink::class, TaskSyncOutboxEntry::class, TaskSyncList::class],
    version = CURRENT_DB_VERSION,  // v30: task sync tables, accounts unique per provider+server+email (v29: What's New dropped)
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun folderDao(): FolderDao
    abstract fun kvDao(): KvDao
    abstract fun notebookDao(): NotebookDao
    abstract fun pageDao(): PageDao
    abstract fun strokeDao(): StrokeDao
    abstract fun ImageDao(): ImageDao
    abstract fun dayPageDao(): DayPageDao
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun textAnnotationDao(): TextAnnotationDao
    abstract fun booxNoteIndexDao(): BooxNoteIndexDao
    abstract fun booxNoteLinkDao(): BooxNoteLinkDao
    abstract fun booxNoteSemanticAggregateDao(): BooxNoteSemanticAggregateDao
    abstract fun taskDao(): TaskDao
    abstract fun recurringTaskTemplateDao(): RecurringTaskTemplateDao
    abstract fun recurringTaskExceptionDao(): RecurringTaskExceptionDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun localEventDao(): LocalEventDao
    abstract fun localCalendarDao(): LocalCalendarDao
    abstract fun summaryCacheDao(): SummaryCacheDao

    // Multi-provider calendar sync DAOs (v18+)
    abstract fun connectedAccountDao(): ConnectedAccountDao
    abstract fun calendarSelectionDao(): CalendarSelectionDao

    // Unified OCR DAO (v20+)
    abstract fun unifiedOcrDao(): UnifiedOcrDao

    // Entity Co-occurrence DAO (Phase 5 - SemanticGenius)
    abstract fun entityCooccurrenceDao(): EntityCooccurrenceDao

    // User Activity Pattern DAO (Phase 6 - SemanticGenius)
    abstract fun userActivityPatternDao(): UserActivityPatternDao

    // Task sync (v30): links to the service's copies, outbox, ticked lists
    abstract fun taskSyncDao(): TaskSyncDao

    companion object {
        // @Volatile + the re-check inside synchronized below: two threads starting at the same
        // time (app start-up work, first screen) must never build two database instances.
        @Volatile
        private var INSTANCE: AppDatabase? = null
        private const val DB_TAG = "AppDatabase"

        // Migration from v8 to v9: Add linkedEventId column to Page table
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add linkedEventId column with index
                database.execSQL("ALTER TABLE Page ADD COLUMN linkedEventId TEXT DEFAULT NULL")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_Page_linkedEventId ON Page(linkedEventId)")
            }
        }

        /**
         * Callback to create FTS4 virtual table and sync triggers for OCR text search.
         *
         * Uses onOpen (not onCreate) because:
         * - onCreate only runs on fresh installs
         * - onOpen runs every time, ensuring FTS table exists
         * - IF NOT EXISTS makes it idempotent
         *
         * FTS4 is used instead of FTS5 because Boox devices run older SQLite versions
         * that may not support FTS5 reliably.
         *
         * The FTS table is external-content (content="unified_ocr_text"): to remove a document,
         * FTS4 reads its old text back from unified_ocr_text. So the FTS delete must run in a
         * BEFORE DELETE / BEFORE UPDATE trigger, while the row still holds the old text
         * (sqlite.org/fts3.html, "External Content FTS4 Tables"). Until Oct 2026 the triggers
         * were AFTER DELETE / AFTER UPDATE, which left every deleted or updated document in the
         * index, and upsertOcrText used REPLACE, which fires no delete trigger at all
         * ("malformed inverted index" in PRAGMA integrity_check, 1,056 FTS documents for 552
         * rows). A database without the BEFORE DELETE trigger gets the new triggers and one
         * full 'rebuild' of the index from unified_ocr_text, in one transaction; the trigger
         * itself marks the database as done, so a restored older database file is fixed too.
         *
         * Tokenizer (Oct 2026): the index uses unicode61 with remove_diacritics=1, so search
         * ignores accents and case beyond ASCII ("cafe" finds "Café"). Until then it used FTS4's
         * default 'simple' tokenizer. A table created without unicode61 is dropped, created again
         * and rebuilt once, in one transaction; its own CREATE statement marks the database as
         * done. remove_diacritics=2 would need SQLite 3.27 (Android 11); minSdk 29 has 3.22.
         * Where unicode61 is not available at all, the table keeps the simple tokenizer.
         */
        internal val FTS4_CALLBACK = object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                try {
                    val existingFtsSql = db.query(
                        "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'unified_ocr_text_fts'"
                    ).use { if (it.moveToFirst()) it.getString(0) ?: "" else null }

                    // Create FTS4 virtual table for full-text search on OCR text
                    // content= links to unified_ocr_text for automatic content sync
                    if (existingFtsSql == null) {
                        createFtsTable(db, unicode61 = isUnicode61Available(db))
                    }

                    // Trigger: After INSERT on unified_ocr_text, insert into FTS
                    db.execSQL("""
                        CREATE TRIGGER IF NOT EXISTS unified_ocr_text_fts_ai
                        AFTER INSERT ON unified_ocr_text BEGIN
                            INSERT INTO unified_ocr_text_fts(docid, text, textNormalized, synopsis)
                            VALUES (new.rowid, new.text, new.textNormalized, new.synopsis);
                        END
                    """.trimIndent())

                    val hasBeforeDeleteTrigger = db.query(
                        "SELECT 1 FROM sqlite_master WHERE type = 'trigger' AND name = 'unified_ocr_text_fts_bd'"
                    ).use { it.moveToFirst() }
                    if (!hasBeforeDeleteTrigger) {
                        db.beginTransaction()
                        try {
                            // The old AFTER triggers (the AFTER UPDATE one also did the delete)
                            db.execSQL("DROP TRIGGER IF EXISTS unified_ocr_text_fts_ad")
                            db.execSQL("DROP TRIGGER IF EXISTS unified_ocr_text_fts_au")

                            // Trigger: Before DELETE on unified_ocr_text, delete from FTS
                            db.execSQL("""
                                CREATE TRIGGER unified_ocr_text_fts_bd
                                BEFORE DELETE ON unified_ocr_text BEGIN
                                    DELETE FROM unified_ocr_text_fts WHERE docid = old.rowid;
                                END
                            """.trimIndent())

                            // Triggers: UPDATE on unified_ocr_text = FTS delete before, insert after
                            db.execSQL("""
                                CREATE TRIGGER unified_ocr_text_fts_bu
                                BEFORE UPDATE ON unified_ocr_text BEGIN
                                    DELETE FROM unified_ocr_text_fts WHERE docid = old.rowid;
                                END
                            """.trimIndent())
                            db.execSQL("""
                                CREATE TRIGGER unified_ocr_text_fts_au
                                AFTER UPDATE ON unified_ocr_text BEGIN
                                    INSERT INTO unified_ocr_text_fts(docid, text, textNormalized, synopsis)
                                    VALUES (new.rowid, new.text, new.textNormalized, new.synopsis);
                                END
                            """.trimIndent())

                            // Drop the stale documents the old triggers left behind
                            db.execSQL("INSERT INTO unified_ocr_text_fts(unified_ocr_text_fts) VALUES('rebuild')")
                            db.setTransactionSuccessful()
                        } finally {
                            db.endTransaction()
                        }
                        Log.i("AppDatabase", "FTS4 triggers replaced and index rebuilt")
                    }

                    // An index made with the simple tokenizer: the same table with unicode61.
                    // The triggers name the table, so they keep working on the new one.
                    if (existingFtsSql != null && !existingFtsSql.contains("unicode61") && isUnicode61Available(db)) {
                        val started = System.currentTimeMillis()
                        db.beginTransaction()
                        try {
                            db.execSQL("DROP TABLE unified_ocr_text_fts")
                            createFtsTable(db, unicode61 = true)
                            db.execSQL("INSERT INTO unified_ocr_text_fts(unified_ocr_text_fts) VALUES('rebuild')")
                            db.setTransactionSuccessful()
                        } finally {
                            db.endTransaction()
                        }
                        Log.i("AppDatabase", "FTS4 index moved to unicode61 in ${System.currentTimeMillis() - started} ms")
                    }

                    Log.d("AppDatabase", "FTS4 virtual table and triggers ensured")
                } catch (e: Exception) {
                    Log.e("AppDatabase", "Failed to create FTS4 table: ${e.message}", e)
                }
            }
        }

        private fun createFtsTable(db: SupportSQLiteDatabase, unicode61: Boolean) {
            val tokenizer = if (unicode61) ", tokenize=unicode61 \"remove_diacritics=1\"" else ""
            db.execSQL("""
                CREATE VIRTUAL TABLE unified_ocr_text_fts
                USING fts4(
                    content="unified_ocr_text",
                    text,
                    textNormalized,
                    synopsis$tokenizer
                )
            """.trimIndent())
        }

        /** Whether this SQLite build has FTS4's unicode61 tokenizer (it is optional at compile time). */
        private fun isUnicode61Available(db: SupportSQLiteDatabase): Boolean = try {
            db.execSQL("CREATE VIRTUAL TABLE temp.fts_tokenizer_probe USING fts4(x, tokenize=unicode61 \"remove_diacritics=1\")")
            db.execSQL("DROP TABLE temp.fts_tokenizer_probe")
            true
        } catch (e: Exception) {
            Log.w("AppDatabase", "FTS4 unicode61 tokenizer not available: ${e.message}")
            false
        }

        fun getDatabase(context: Context): AppDatabase {
            INSTANCE?.let { return it }
            synchronized(this) {
                // Another thread may have built it while this one waited for the lock.
                INSTANCE?.let { return it }
                run {
                    val dbDir = getDbDir(context)
                    val dbFile = File(dbDir, APP_DATABASE_FILE_NAME)

                    resetPrePublicTestDatabaseIfNeeded(dbFile)

                    // Use Room to build the database
                    //
                    // ⚠️ WARNING: allowMainThreadQueries() is a technical debt item.
                    // It allows DB queries on the main thread, which can cause ANRs.
                    // TODO: Post-launch, convert all DAO functions to suspend and remove this.
                    // Monitor Play Console for ANR reports - if >0.5%, prioritize fixing.
                    // Estimated effort: 8-16 hours to refactor all DAO callers.
                    //
                    // Every build must fail safely if a migration path is missing — data is
                    // never silently wiped (see the note at the fallback below).
                    val builder =
                        Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
                            .allowMainThreadQueries()
                            .addCallback(FTS4_CALLBACK)
                            .addCallback(TaskSyncSchema.CALLBACK)
                            .addMigrations(
                                MIGRATION_8_9,
                                MIGRATION_9_10,
                                MIGRATION_10_11,
                                MIGRATION_11_12,
                                MIGRATION_12_13,
                                MIGRATION_13_14,
                                MIGRATION_14_15,
                                MIGRATION_15_16,
                                MIGRATION_16_17,
                                MIGRATION_17_18,
                                MIGRATION_20_21,
                                MIGRATION_21_22,
                                MIGRATION_22_23,
                                MIGRATION_23_24,
                                MIGRATION_24_25,
                                MIGRATION_25_26,
                                MIGRATION_26_27,
                                MIGRATION_27_28,
                                MIGRATION_28_29,
                                MIGRATION_29_30
                            )

                    // No destructive fallback in any build (plan T2, Oct 2026): the real-data
                    // Go 10.3 runs debug builds, so a missing/failed migration must fail loudly
                    // instead of silently wiping the database.

                    val database = builder.build()
                    INSTANCE = database
                    return database
                }
            }
        }

        /**
         * Pre-public test builds used schema versions below FIRST_PUBLIC_DB_VERSION.
         * Those databases are not supported upgrade targets, so wipe them before
         * Room opens the file and tries to run production migrations.
         */
        private fun resetPrePublicTestDatabaseIfNeeded(dbFile: File) {
            if (!dbFile.exists()) return

            val dbVersion = readDatabaseVersion(dbFile) ?: return
            if (dbVersion >= FIRST_PUBLIC_DB_VERSION) return

            Log.w(
                DB_TAG,
                "Deleting pre-public test database version $dbVersion before Room init"
            )

            val sidecarFiles = listOf(
                dbFile,
                File("${dbFile.absolutePath}-wal"),
                File("${dbFile.absolutePath}-shm"),
                File("${dbFile.absolutePath}-journal")
            )

            sidecarFiles.forEach { file ->
                if (file.exists() && !file.delete()) {
                    Log.w(DB_TAG, "Failed to delete legacy database file: ${file.absolutePath}")
                }
            }
        }

        private fun readDatabaseVersion(dbFile: File): Int? {
            var sqliteDb: SQLiteDatabase? = null
            return try {
                sqliteDb = SQLiteDatabase.openDatabase(
                    dbFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY
                )
                sqliteDb.version
            } catch (e: Exception) {
                Log.w(DB_TAG, "Unable to inspect database version for ${dbFile.absolutePath}", e)
                null
            } finally {
                sqliteDb?.close()
            }
        }

        /**
         * Close the database and clear the instance.
         * Required before full database restore to release file locks.
         *
         * IMPORTANT: After calling this, getDatabase() must be called to
         * reinitialize the database before any DAO operations.
         */
        fun closeDatabase() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
                Log.d(DB_TAG, "Database closed and instance cleared")
            }
        }
    }
}

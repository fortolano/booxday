package com.boox.day.data.db

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * v29 → v30 on the PC: a v29 database is built from Room's own exported schema (29.json), then
 * opened with MIGRATION_29_30, so Room checks the migrated tables against the v30 entities.
 * (The instrumented MigrationTest can't run here: no emulator, and never on the tablets.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class Migration29To30Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dbFile: File
    private var room: AppDatabase? = null

    @Before
    fun setUp() {
        dbFile = File(context.filesDir, "migration-29-30.db")
        listOf("", "-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
    }

    @After
    fun tearDown() {
        room?.close()
    }

    @Test
    fun migratesAccountsAndAddsTaskSyncTables() {
        createV29 { db ->
            db.execSQL(
                "INSERT INTO connected_accounts (id, providerId, accountType, email, displayName, isActive, createdAt, lastSyncAt) " +
                    "VALUES ('g-1', 'GOOGLE', 'GOOGLE_PERSONAL', 'me@gmail.com', 'Me', 1, 1, NULL)"
            )
            db.execSQL(
                "INSERT INTO calendar_selections (id, accountId, calendarId, calendarName, calendarColor, isFetchEnabled, isDefaultForAccount, displayOrder) " +
                    "VALUES ('g-1-work', 'g-1', 'work', 'Work', NULL, 1, 1, 0)"
            )
            insertPageAndTask(db, taskId = "t-1", text = "Call")
        }

        val db = openMigrated()
        val sql = db.openHelper.writableDatabase

        sql.query("SELECT email, serverUrl FROM connected_accounts WHERE id = 'g-1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("me@gmail.com", c.getString(0))
            assertEquals("Google accounts have no server address", "", c.getString(1))
        }
        sql.query("SELECT COUNT(*) FROM calendar_selections WHERE accountId = 'g-1'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("calendar choices survive", 1, c.getInt(0))
        }
        sql.query("SELECT typedText FROM task WHERE id = 't-1'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("Call", c.getString(0))
        }
        sql.query("SELECT 1 FROM sqlite_master WHERE type = 'trigger' AND name = ?", arrayOf(TaskSyncSchema.ROW_DELETED_TRIGGER)).use { c ->
            assertTrue("the row-deleted trigger exists after the migration", c.moveToFirst())
        }
    }

    @Test
    fun theSameEmailCanBelongToTwoProvidersButNotTwiceToOne() {
        createV29 { db ->
            db.execSQL(
                "INSERT INTO connected_accounts (id, providerId, accountType, email, displayName, isActive, createdAt, lastSyncAt) " +
                    "VALUES ('g-1', 'GOOGLE', 'GOOGLE_PERSONAL', 'me@gmail.com', 'Me', 1, 1, NULL)"
            )
        }
        val sql = openMigrated().openHelper.writableDatabase

        // An Apple ID can be a Gmail address (CalDAV later); Outlook stands in for any other provider here.
        sql.execSQL(
            "INSERT INTO connected_accounts (id, providerId, accountType, email, displayName, isActive, createdAt, lastSyncAt, serverUrl) " +
                "VALUES ('o-1', 'OUTLOOK', 'OUTLOOK_PERSONAL', 'me@gmail.com', 'Me', 1, 2, NULL, '')"
        )
        try {
            sql.execSQL(
                "INSERT INTO connected_accounts (id, providerId, accountType, email, displayName, isActive, createdAt, lastSyncAt, serverUrl) " +
                    "VALUES ('g-2', 'GOOGLE', 'GOOGLE_PERSONAL', 'me@gmail.com', 'Me', 1, 3, NULL, '')"
            )
            fail("a second Google account with the same email must be refused")
        } catch (expected: SQLiteConstraintException) {
        }
        sql.query("SELECT COUNT(*) FROM connected_accounts WHERE email = 'me@gmail.com'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(2, c.getInt(0))
        }
    }

    // ---------- helpers ----------

    private fun createV29(seed: (SupportSQLiteDatabase) -> Unit) {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.absolutePath)
            .callback(object : SupportSQLiteOpenHelper.Callback(29) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    schemaStatements(29).forEach(db::execSQL)
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(config).use { helper ->
            seed(helper.writableDatabase)
        }
    }

    private fun openMigrated(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
            .addMigrations(MIGRATION_29_30)
            .addCallback(TaskSyncSchema.CALLBACK)
            .allowMainThreadQueries()
            .build()
            .also {
                room = it
                it.openHelper.writableDatabase  // runs the migration and Room's schema check
            }

    private fun insertPageAndTask(db: SupportSQLiteDatabase, taskId: String, text: String) {
        db.execSQL(
            "INSERT OR IGNORE INTO Page (id, scroll, notebookId, background, backgroundType, parentFolderId, linkedEventId, createdAt, updatedAt) " +
                "VALUES ('page-1', 0, NULL, 'blank', 'native', NULL, NULL, 1, 1)"
        )
        db.execSQL(
            "INSERT INTO task (id, dayDate, taskPageIndex, rowIndex, pageId, isCompleted, priority, typedText, " +
                "migrationCount, createdAt, updatedAt) VALUES (?, '2026-10-30', 0, 0, 'page-1', 0, '', ?, 0, 1, 1)",
            arrayOf<Any?>(taskId, text)
        )
    }

    /** Every CREATE statement of an exported Room schema, plus its setup queries (identity hash). */
    private fun schemaStatements(version: Int): List<String> {
        val path = "schemas/com.boox.day.data.db.AppDatabase/$version.json"
        val file = listOf(File(path), File("app/$path")).first { it.exists() }
        val database = Json.parseToJsonElement(file.readText()).jsonObject.getValue("database").jsonObject
        val statements = mutableListOf<String>()
        for (entity in database.getValue("entities").jsonArray) {
            val e = entity as JsonObject
            val table = e.getValue("tableName").jsonPrimitive.content
            statements += e.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)
            e["indices"]?.jsonArray?.forEach { index ->
                statements += index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)
            }
        }
        database["views"]?.jsonArray?.forEach { view ->
            val v = view.jsonObject
            statements += v.getValue("createSql").jsonPrimitive.content.replace("\${VIEW_NAME}", v.getValue("viewName").jsonPrimitive.content)
        }
        database.getValue("setupQueries").jsonArray.forEach { statements += it.jsonPrimitive.content }
        return statements
    }
}

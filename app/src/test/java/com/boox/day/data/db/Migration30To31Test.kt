package com.boox.day.data.db

import android.app.Application
import android.content.Context
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * v30 → v31 on the PC (task sync merge): a v30 database built from Room's exported schema
 * (30.json) with a synced task, opened with MIGRATION_30_31, so Room checks it against the v31
 * entities. The Go 10.3 runs v30 (task sync Groundwork), so this is the path its data takes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class Migration30To31Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dbFile: File
    private var room: AppDatabase? = null

    @Before
    fun setUp() {
        dbFile = File(context.filesDir, "migration-30-31.db")
        listOf("", "-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
    }

    @After
    fun tearDown() {
        room?.close()
    }

    @Test
    fun aLinkKeepsItsSnapshotAndGetsTheServiceParentAndNoKeptMark() {
        createV30 { db ->
            db.execSQL(
                "INSERT INTO Page (id, scroll, notebookId, background, backgroundType, parentFolderId, linkedEventId, createdAt, updatedAt) " +
                    "VALUES ('page-1', 0, NULL, 'blank', 'native', NULL, NULL, 1, 1)"
            )
            db.execSQL(
                "INSERT INTO task (id, dayDate, taskPageIndex, rowIndex, pageId, isCompleted, priority, typedText, " +
                    "migrationCount, createdAt, updatedAt) VALUES ('t-1', '2026-10-30', 0, 0, 'page-1', 0, '', 'Buy milk', 0, 1, 1)"
            )
            db.execSQL(
                "INSERT INTO task_sync_link (taskId, provider, accountEmail, serverUrl, listId, remoteId, remoteVersion, syncedTitle, " +
                    "syncedCompleted, syncedDueDay, syncedParentRemoteId, contentHash, remoteUndated, orphanedAt, syncedAt) " +
                    "VALUES ('t-1', 'GOOGLE_TASKS', 'me@gmail.com', '', 'l-1', 'r-1', 'v1', 'Buy milk', 0, '2026-10-30', 'r-parent', 'h', 0, NULL, 5)"
            )
            db.execSQL(
                "INSERT INTO task_sync_link (taskId, provider, accountEmail, serverUrl, listId, remoteId, syncedCompleted, " +
                    "remoteUndated, syncedAt) VALUES ('t-2', 'GOOGLE_TASKS', 'me@gmail.com', '', 'l-1', 'r-2', 0, 0, 6)"
            )
        }

        val sync = openMigrated().taskSyncDao()

        val link = sync.getLink("t-1")!!
        assertEquals("Buy milk", link.syncedTitle)
        assertEquals("v1", link.remoteVersion)
        assertEquals("r-parent", link.syncedParentRemoteId)
        assertEquals("the service's parent starts as the one last sent", "r-parent", link.remoteParentId)
        assertFalse(link.keptOnTablet)
        assertNull(sync.getLink("t-2")!!.remoteParentId)
        assertEquals("nothing is kept on the tablet after the migration", 0, sync.countKeptOnTablet())
    }

    @Test
    fun theRowDeletedTriggerLeavesOutTasksKeptOnTheTablet() {
        createV30 { db ->
            db.execSQL(
                "INSERT INTO Page (id, scroll, notebookId, background, backgroundType, parentFolderId, linkedEventId, createdAt, updatedAt) " +
                    "VALUES ('page-1', 0, NULL, 'blank', 'native', NULL, NULL, 1, 1)"
            )
            for ((id, row) in listOf("t-1" to 0, "t-2" to 1)) {
                db.execSQL(
                    "INSERT INTO task (id, dayDate, taskPageIndex, rowIndex, pageId, isCompleted, priority, typedText, " +
                        "migrationCount, createdAt, updatedAt) VALUES ('$id', '2026-10-30', 0, $row, 'page-1', 0, '', 'x', 0, 1, 1)"
                )
                db.execSQL(
                    "INSERT INTO task_sync_link (taskId, provider, accountEmail, serverUrl, listId, remoteId, syncedCompleted, " +
                        "remoteUndated, syncedAt) VALUES ('$id', 'GOOGLE_TASKS', 'me@gmail.com', '', 'l-1', 'r-$id', 0, 0, 1)"
                )
            }
        }
        val room = openMigrated()
        val sql = room.openHelper.writableDatabase
        room.taskSyncDao().markKeptOnTablet("t-2", TaskSyncKept.CHANGED_HERE)

        sql.execSQL("DELETE FROM task")

        assertEquals("only the synced task's copy is deleted", listOf("r-t-1"), room.taskSyncDao().getOutbox().map { it.remoteId })
    }

    // ---------- helpers (as Migration29To30Test) ----------

    private fun createV30(seed: (SupportSQLiteDatabase) -> Unit) {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.absolutePath)
            .callback(object : SupportSQLiteOpenHelper.Callback(30) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    schemaStatements(30).forEach(db::execSQL)
                    TaskSyncSchema.createTriggersV30(db)
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
            .addMigrations(MIGRATION_30_31)
            .addCallback(TaskSyncSchema.CALLBACK)
            .allowMainThreadQueries()
            .build()
            .also {
                room = it
                it.openHelper.writableDatabase  // runs the migration and Room's schema check
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

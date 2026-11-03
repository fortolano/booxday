package com.boox.day.data.db

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * v32 → v33 on the PC (CalDAV task lists, plan step 4.2): a v32 database built from Room's exported
 * schema (32.json) with a task link, opened with MIGRATION_32_33, so Room checks it against the v33
 * entities. The Go 10.3 runs v32 (CalDAV calendars), so this is the path its data takes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class Migration32To33Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dbFile: File
    private var room: AppDatabase? = null

    @Before
    fun setUp() {
        dbFile = File(context.filesDir, "migration-32-33.db")
        listOf("", "-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
    }

    @After
    fun tearDown() {
        room?.close()
    }

    @Test
    fun linksKeepTheirSnapshotAndTaskFilesGoWithTheirAccount() = runBlocking {
        createV32 { db ->
            db.execSQL(
                "INSERT INTO connected_accounts (id, providerId, accountType, email, displayName, isActive, createdAt, lastSyncAt, serverUrl) " +
                    "VALUES ('acc-1', 'CALDAV', 'CALDAV_OTHER', 'me', NULL, 1, 1, NULL, 'https://dav.example.com/')"
            )
            db.execSQL(
                "INSERT INTO task_sync_link (taskId, provider, accountEmail, serverUrl, listId, remoteId, syncedTitle, syncedCompleted, " +
                    "syncedDueDay, remoteUndated, syncedAt, keptOnTablet) VALUES ('t-1', 'GOOGLE_TASKS', 'me@gmail.com', '', 'l-1', 'r-1', 'Milk', 0, '2026-11-03', 0, 1, 0)"
            )
            db.execSQL("INSERT INTO caldav_account (accountId, principalUrl, calendarHomeUrl, username, pinnedCertSha256) VALUES ('acc-1', '/p/', '/h/', 'me', NULL)")
        }
        val room = openMigrated()
        // v34 (owner decision 78): an account from before keeps using its calendars
        assertEquals(true, room.calDavDao().getAccount("acc-1")?.useCalendars)
        val link = room.taskSyncDao().getLink("t-1")!!
        assertEquals("Milk", link.syncedTitle)
        assertNull("nothing carried priority before v33", link.syncedPriority)

        val dav = room.calDavDao()
        dav.upsertTaskFile(CalDavTaskFile("acc-1", "/me/tasks/a.ics", "/me/tasks/", "\"e1\"", "BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n", "a", 1))
        assertEquals("a", dav.getTaskFileByUid("acc-1", "/me/tasks/", "a")?.uid)
        room.connectedAccountDao().deleteById("acc-1")
        assertNull("its task files go with the account", dav.getTaskFile("acc-1", "/me/tasks/a.ics"))
    }

    // ---------- helpers (as Migration31To32Test) ----------

    private fun createV32(seed: (SupportSQLiteDatabase) -> Unit) {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.absolutePath)
            .callback(object : SupportSQLiteOpenHelper.Callback(32) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    schemaStatements(32).forEach(db::execSQL)
                    TaskSyncSchema.createTriggers(db)
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
            .addMigrations(MIGRATION_32_33, MIGRATION_33_34)
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

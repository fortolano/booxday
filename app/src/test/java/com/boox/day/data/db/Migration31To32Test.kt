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
 * v31 → v32 on the PC (CalDAV calendars, plan step 3.2): a v31 database built from Room's exported
 * schema (31.json) with a connected account, opened with MIGRATION_31_32, so Room checks it against
 * the v32 entities. The Go 10.3 runs v31 (task sync), so this is the path its data takes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class Migration31To32Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dbFile: File
    private var room: AppDatabase? = null

    @Before
    fun setUp() {
        dbFile = File(context.filesDir, "migration-31-32.db")
        listOf("", "-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
    }

    @After
    fun tearDown() {
        room?.close()
    }

    @Test
    fun accountsStayAndTheCalDavTablesGoWithTheirAccount() = runBlocking {
        createV31 { db ->
            db.execSQL(
                "INSERT INTO connected_accounts (id, providerId, accountType, email, displayName, isActive, createdAt, lastSyncAt, serverUrl) " +
                    "VALUES ('acc-1', 'GOOGLE', 'GOOGLE_PERSONAL', 'me@gmail.com', NULL, 1, 1, NULL, '')"
            )
        }
        val room = openMigrated()
        assertEquals("me@gmail.com", room.connectedAccountDao().getById("acc-1")?.email)

        // A CalDAV account's rows, then the account removed (Calendar Accounts → Disconnect)
        room.openHelper.writableDatabase.execSQL(
            "INSERT INTO connected_accounts (id, providerId, accountType, email, displayName, isActive, createdAt, lastSyncAt, serverUrl) " +
                "VALUES ('acc-2', 'CALDAV', 'CALDAV_ICLOUD', 'me@icloud.com', NULL, 1, 1, NULL, 'https://caldav.icloud.com')"
        )
        val dav = room.calDavDao()
        dav.upsertAccount(CalDavAccount("acc-2", "/123/principal/", "/123/calendars/", "me@icloud.com"))
        dav.upsertCalendar(CalDavCalendar("acc-2", "/123/calendars/home/", ctag = "c1"))
        dav.upsertResource(CalDavResource("acc-2", "/123/calendars/home/a.ics", "/123/calendars/home/", "\"e1\"", "BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n", "a", 1))
        assertEquals("c1", dav.getCalendar("acc-2", "/123/calendars/home/")?.ctag)

        room.connectedAccountDao().deleteById("acc-2")

        assertNull(dav.getAccount("acc-2"))
        assertNull(dav.getCalendar("acc-2", "/123/calendars/home/"))
        assertNull("its event files go too", dav.getResource("acc-2", "/123/calendars/home/a.ics"))
    }

    // ---------- helpers (as Migration30To31Test) ----------

    private fun createV31(seed: (SupportSQLiteDatabase) -> Unit) {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.absolutePath)
            .callback(object : SupportSQLiteOpenHelper.Callback(31) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    schemaStatements(31).forEach(db::execSQL)
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
            .addMigrations(MIGRATION_31_32, MIGRATION_32_33)
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

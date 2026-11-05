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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * v34 → v35: existing note links survive, and a note can link to a tablet-only event, which isn't in
 * calendar_events. On v34 that closed the app (Go 10.3, 5 Nov 2026, Choose Event).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class Migration34To35Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dbFile: File
    private var room: AppDatabase? = null

    @Before
    fun setUp() {
        dbFile = File(context.filesDir, "migration-34-35.db")
        listOf("", "-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
    }

    @After
    fun tearDown() {
        room?.close()
    }

    @Test
    fun linksSurviveAndATabletOnlyEventCanBeLinked() = runBlocking {
        createV34 { db ->
            db.execSQL(
                "INSERT INTO boox_note_links (id, noteUniqueId, linkedEventId, linkedDayDate, isManualLink, confidence, " +
                    "confidenceScore, matchReasons, suggestedEventIds, dismissedEventIds, needsAttention, createdAt, updatedAt, " +
                    "lastReviewedAt, decisionMode, engineVersion, dataQualityFlagsJson) VALUES ('link-1', 'note-1', NULL, " +
                    "'2026-11-04', 1, 'HIGH', 90, NULL, NULL, '[\"event-9\"]', 0, 1, 2, 3, 'MANUAL_LINK', 'v1', NULL)"
            )
        }

        val links = openMigrated().booxNoteLinkDao()

        val kept = links.getLinkForNote("note-1")!!
        assertEquals("link-1", kept.id)
        assertEquals("[\"event-9\"]", kept.dismissedEventIds)
        assertEquals("2026-11-04", kept.linkedDayDate)

        links.upsert(kept.copy(linkedEventId = "tablet-only-event"))
        assertEquals("tablet-only-event", links.getLinkForNote("note-1")?.linkedEventId)
    }

    private fun createV34(seed: (SupportSQLiteDatabase) -> Unit) {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.absolutePath)
            .callback(object : SupportSQLiteOpenHelper.Callback(34) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    schemaStatements(34).forEach(db::execSQL)
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
            .addMigrations(MIGRATION_34_35)
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

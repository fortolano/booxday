package com.boox.day.booxnotes

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.boox.day.booxnotes.FleeceReaderTest.Companion.NOTE
import com.boox.day.booxnotes.FleeceReaderTest.Companion.SHARED_KEYS
import com.boox.day.booxnotes.FleeceReaderTest.Companion.bytes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Boox's NOTE_TREE database is read from a copy (with its WAL, where recent notes live), never in place.
 * Runs on the JVM (Robolectric) with a real SQLite database laid out like the tablet's .ksync/couch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BooxNoteCreationTimesTest {

    private lateinit var ksync: File
    private lateinit var workDir: File

    @Before
    fun setUp() {
        val base = ApplicationProvider.getApplicationContext<Application>().cacheDir
        ksync = File(base, "ksync").apply { deleteRecursively(); mkdirs() }
        workDir = File(base, "work").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun tearDown() {
        ksync.deleteRecursively()
        workDir.deleteRecursively()
    }

    @Test
    fun readsCreationTimesFromACopyIncludingTheWal() {
        val booxDb = noteTreeDatabase("659ca7cff3edba1fe6af1770-NOTE_TREE.cblite2")
        // Another user's shared notes: never read
        noteTreeDatabase("share_user-NOTE_TREE.cblite2").use { it.insertNote("easter", NOTE_CREATED_ELSEWHERE) }

        booxDb.use { db ->
            db.insertNote("easter", NOTE)
            db.insertNote("no-date", bytes(0x70, 0x01, 0x00, 0x00, 0x41, 0x78, 0x80, 0x03))   // {title: "x"}
            db.insertNote("bad-date", bytes(0x70, 0x01, 0x00, 0x01, 0x00, 0x05, 0x80, 0x03))  // {createdAt: 5}
            db.insertNote("damaged", bytes(0x15, 0x00))
            val source = File(db.path)
            val before = source.length() to source.lastModified()
            assertTrue("rows stay in the WAL while Boox has the database open", File(db.path + "-wal").length() > 0)

            val times = BooxNoteCreationTimes(workDir, ksync.path).load()

            assertEquals(mapOf("easter" to 1_774_832_200_000L), times)
            assertEquals(before, source.length() to source.lastModified())
        }
        assertFalse(File(workDir, "boox_note_tree_copy").exists())
    }

    @Test
    fun noBooxDatabaseGivesNoTimes() {
        assertEquals(emptyMap<String, Long>(), BooxNoteCreationTimes(workDir, ksync.path).load())
    }

    private fun noteTreeDatabase(folder: String): SQLiteDatabase {
        val file = File(File(ksync, "couch/$folder").apply { mkdirs() }, "db.sqlite3")
        val db = SQLiteDatabase.openDatabase(
            file.path,
            null,
            SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING
        )
        db.rawQuery("PRAGMA wal_autocheckpoint = 0", null).use { it.moveToFirst() }
        db.execSQL("CREATE TABLE kv_info (key TEXT PRIMARY KEY, body BLOB)")
        db.execSQL("CREATE TABLE kv_default (key TEXT PRIMARY KEY, body BLOB)")
        db.execSQL("INSERT INTO kv_info VALUES ('SharedKeys', ?)", arrayOf<Any>(SHARED_KEYS))
        return db
    }

    private fun SQLiteDatabase.insertNote(id: String, body: ByteArray) =
        execSQL("INSERT INTO kv_default VALUES (?, ?)", arrayOf<Any>(id, body))

    private companion object {
        /** {createdAt: 1700000000000} under shared key 1 */
        val NOTE_CREATED_ELSEWHERE = bytes(
            0x15, 0x00, 0x68, 0xE5, 0xCF, 0x8B, 0x01, 0x00,
            0x70, 0x01, 0x00, 0x01, 0x80, 0x06,
            0x80, 0x03
        )
    }
}

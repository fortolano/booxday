package com.boox.day.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boox.day.data.db.APP_DATABASE_FILE_NAME
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.CURRENT_DB_VERSION
import com.boox.day.data.getDbDir
import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DatabaseBaselineTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        resetDatabaseState()
    }

    @After
    fun tearDown() {
        resetDatabaseState()
    }

    @Test
    fun freshDatabaseOpensAtCurrentVersion() {
        val db = AppDatabase.getDatabase(context)
        val actualVersion = db.openHelper.readableDatabase.version

        assertEquals(CURRENT_DB_VERSION, actualVersion)
    }

    // Phase 6 (Oct 2026): the full-SQLite backup/restore services these tests covered were never
    // wired into the app and are deleted; backups are the JSON export (ExportService/ImportService).

    private fun resetDatabaseState() {
        AppDatabase.closeDatabase()

        val dbDir = getDbDir(context)
        dbDir.listFiles()?.forEach { file ->
            file.deleteRecursively()
        }

        deleteIfExists(File(dbDir, APP_DATABASE_FILE_NAME))
        deleteIfExists(File(dbDir, "$APP_DATABASE_FILE_NAME-wal"))
        deleteIfExists(File(dbDir, "$APP_DATABASE_FILE_NAME-shm"))
    }

    private fun deleteIfExists(file: File) {
        if (file.exists()) {
            file.deleteRecursively()
        }
    }
}

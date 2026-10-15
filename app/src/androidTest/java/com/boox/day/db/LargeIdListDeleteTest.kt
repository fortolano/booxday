package com.boox.day.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.TextAnnotation
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Plan 2026-10-01, chunked deletes (Phase 0 test d): `DELETE ... WHERE id IN (:ids)` binds one
 * SQLite variable per ID. SQLite allows 999 variables before 3.32 (Android 10/11) and 32,766 after
 * (Android 12/13), so a list longer than 32,766 fails on every supported Android version unless
 * the delete is chunked.
 */
@RunWith(AndroidJUnit4::class)
class LargeIdListDeleteTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = TestDb.inMemory(context)
        db.pageDao().create(TestDb.page(PAGE_ID))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun strokeDeleteAll_overSqliteVariableLimit() {
        db.strokeDao().create(listOf("keep", "gone-1", "gone-2").map { TestDb.stroke(PAGE_ID, id = it) })

        db.strokeDao().deleteAll(hugeIdList("gone-1", "gone-2"))

        assertEquals(listOf("keep"), db.strokeDao().getAllForPage(PAGE_ID).map { it.id })
    }

    @Test
    fun imageDeleteAll_overSqliteVariableLimit() {
        db.ImageDao().create(listOf("keep", "gone-1", "gone-2").map { TestDb.image(PAGE_ID, id = it) })

        db.ImageDao().deleteAll(hugeIdList("gone-1", "gone-2"))

        assertEquals(listOf("keep"), db.ImageDao().getAllImages().map { it.id })
    }

    @Test
    fun attachmentDeleteAll_overSqliteVariableLimit() {
        db.attachmentDao().create(listOf("keep", "gone-1", "gone-2").map { TestDb.attachment(PAGE_ID, id = it) })

        db.attachmentDao().deleteAll(hugeIdList("gone-1", "gone-2"))

        assertEquals(listOf("keep"), db.attachmentDao().getAllAttachments().map { it.id })
    }

    @Test
    fun textAnnotationDeleteByIds_overSqliteVariableLimit() = runBlocking {
        db.textAnnotationDao().insertAll(
            listOf("keep", "gone-1", "gone-2").map {
                TextAnnotation(id = it, content = it, x = 0f, y = 0f, width = 10f, height = 10f, pageId = PAGE_ID)
            }
        )

        db.textAnnotationDao().deleteByIds(hugeIdList("gone-1", "gone-2"))

        assertEquals(listOf("keep"), db.textAnnotationDao().getByPageId(PAGE_ID).map { it.id })
    }

    /** The real IDs plus enough IDs of rows that don't exist to pass 32,766 bound variables. */
    private fun hugeIdList(vararg realIds: String): List<String> =
        realIds.toList() + (0 until OVER_LIMIT).map { "missing-$it" }

    private companion object {
        const val PAGE_ID = "page-large-delete"
        const val OVER_LIMIT = 33_000
    }
}

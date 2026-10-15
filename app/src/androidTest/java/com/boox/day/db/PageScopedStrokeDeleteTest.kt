package com.boox.day.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.StrokeRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Plan 2026-10-01, A1 layer 1 (Phase 0 test b): the database delete that PageView.removeStrokes()
 * issues for one page must never remove another page's strokes, even when it is handed their IDs
 * (that is what a cross-day undo does).
 */
@RunWith(AndroidJUnit4::class)
class PageScopedStrokeDeleteTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = TestDb.inMemory(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun deletingStrokeIdsForPageA_neverRemovesPageBStrokes() {
        db.pageDao().create(TestDb.page("page-a"))
        db.pageDao().create(TestDb.page("page-b"))
        val pageA = listOf(TestDb.stroke("page-a", id = "a-1"), TestDb.stroke("page-a", id = "a-2", dx = 30f))
        val pageB = listOf(TestDb.stroke("page-b", id = "b-1"), TestDb.stroke("page-b", id = "b-2", dx = 30f))
        db.strokeDao().create(pageA + pageB)

        // Undo on page A carrying a stale batch that also holds page B's stroke IDs.
        deleteStrokesAsPageViewDoes(pageId = "page-a", strokeIds = listOf("a-1", "b-1", "b-2"))

        assertEquals(listOf("b-1", "b-2"), db.strokeDao().getAllForPage("page-b").map { it.id }.sorted())
        assertEquals(listOf("a-2"), db.strokeDao().getAllForPage("page-a").map { it.id })
    }

    @Test
    fun pageScopedDelete_overSqliteVariableLimit_deletesOnlyThatPage() {
        db.pageDao().create(TestDb.page("page-a"))
        db.pageDao().create(TestDb.page("page-b"))
        db.strokeDao().create(listOf(TestDb.stroke("page-a", id = "a-1"), TestDb.stroke("page-b", id = "b-1")))
        val ids = listOf("a-1", "b-1") + (0 until 33_000).map { "missing-$it" }

        deleteStrokesAsPageViewDoes(pageId = "page-a", strokeIds = ids)

        assertEquals(emptyList<String>(), db.strokeDao().getAllForPage("page-a").map { it.id })
        assertEquals(listOf("b-1"), db.strokeDao().getAllForPage("page-b").map { it.id })
    }

    /**
     * The repository call PageView.removeStrokesFromPersistLayer() makes for [pageId]
     * (before the A1 fix it was StrokeRepository.deleteAll(ids), which ignored the page).
     */
    private fun deleteStrokesAsPageViewDoes(pageId: String, strokeIds: List<String>) {
        val repository = StrokeRepository(context).apply { db = this@PageScopedStrokeDeleteTest.db.strokeDao() }
        repository.deleteByIdsOnPage(pageId, strokeIds)
    }
}

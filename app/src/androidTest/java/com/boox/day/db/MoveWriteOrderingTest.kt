package com.boox.day.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boox.day.data.PagePersistenceGate
import com.boox.day.data.PagePersistenceWriter
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.Image
import com.boox.day.data.db.Stroke
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Plan 2026-10-01, A1b (Phase 0 test c): a lasso move deletes the selected rows and inserts the
 * moved copies under the SAME IDs. The delete is issued first, so the rows must exist afterwards.
 */
@RunWith(AndroidJUnit4::class)
class MoveWriteOrderingTest {

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
    fun deleteThenInsertOfSameIds_endsWithRowsPresent() = runBlocking {
        db.pageDao().create(TestDb.page(PAGE_ID))
        val strokes = (0 until 20).map { TestDb.stroke(PAGE_ID, id = "stroke-$it", dx = it.toFloat()) }
        val images = (0 until 5).map { TestDb.image(PAGE_ID, id = "image-$it", dx = it) }
        db.strokeDao().create(strokes)
        db.ImageDao().create(images)

        var roundsLosingStrokes = 0
        var roundsLosingImages = 0
        repeat(ROUNDS) { round ->
            val movedStrokes = strokes.map { it.copy(left = it.left + round, right = it.right + round) }
            val movedImages = images.map { it.copy(x = it.x + round) }

            persistMoveAsPageViewDoes(movedStrokes, movedImages).joinAll()

            if (db.strokeDao().getAllForPage(PAGE_ID).size != strokes.size) {
                roundsLosingStrokes++
                db.strokeDao().create(strokes)
            }
            if (db.ImageDao().getAllImages().size != images.size) {
                roundsLosingImages++
                db.ImageDao().create(images)
            }
        }

        assertEquals(
            "rounds (of $ROUNDS) whose moved rows vanished: strokes=$roundsLosingStrokes images=$roundsLosingImages",
            0,
            roundsLosingStrokes + roundsLosingImages
        )
    }

    /**
     * The writes SelectionState.applySelectionDisplace() triggers, in its order:
     * PageView.removeStrokes, addStrokes, removeImages, addImage. PageView submits each to
     * PagePersistenceWriter (before the A1b fix each was its own Dispatchers.IO coroutine,
     * so nothing ordered a delete before the insert that follows it).
     */
    private fun persistMoveAsPageViewDoes(movedStrokes: List<Stroke>, movedImages: List<Image>): List<Job> = listOf(
        PagePersistenceWriter.submit("test delete strokes") { db.strokeDao().deleteAll(movedStrokes.map { it.id }) },
        PagePersistenceWriter.submit("test insert strokes") {
            PagePersistenceGate.withWrite(PAGE_ID) {
                if (db.pageDao().getById(PAGE_ID) == null) return@withWrite false
                db.strokeDao().create(movedStrokes)
                true
            }
        },
        PagePersistenceWriter.submit("test delete images") { db.ImageDao().deleteAll(movedImages.map { it.id }) },
        PagePersistenceWriter.submit("test insert images") { db.ImageDao().create(movedImages) }
    )

    private companion object {
        const val PAGE_ID = "page-move"
        const val ROUNDS = 200
    }
}

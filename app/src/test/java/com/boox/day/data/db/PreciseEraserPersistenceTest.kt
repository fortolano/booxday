package com.boox.day.data.db

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boox.day.editor.utils.Pen
import com.boox.day.editor.utils.PreciseEraser
import com.boox.day.data.model.SimplePointF
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35, 36], application = Application::class)
class PreciseEraserPersistenceTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: StrokeDao
    private fun original() = Stroke(id="original", pageId="memo", pen=Pen.FOUNTAIN, size=3f,
        left=-3f, right=103f, top=-3f, bottom=3f,
        points=listOf(StrokePoint(0f,0f,300f,1,2,0u), StrokePoint(100f,0f,3000f,5,6,100u)))
    @Before fun setup() {
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        dao=db.strokeDao()
        db.pageDao().create(Page(id="memo"))
        db.pageDao().create(Page(id="other"))
    }
    @After fun close() { db.close() }
    @Test fun eraseUndoAndRedoRoundTripCanonicalPointsAndMetadata() {
        val original=original()
        dao.create(original)
        val parts=PreciseEraser(listOf(SimplePointF(50f,-20f),SimplePointF(50f,20f)),15f).cut(original)!!
        // Existing SB1 storage truncates pressure to integer hardware units.
        val storedParts = parts.map { part -> part.copy(points = part.points.map {
            it.copy(pressure = it.pressure?.toInt()?.toFloat())
        }) }
        dao.replaceOnPage("memo",listOf(original.id),parts)
        assertEquals(storedParts.sortedBy { it.id },dao.getAllForPage("memo").sortedBy { it.id })
        dao.replaceOnPage("memo",parts.map { it.id },listOf(original))
        assertEquals(listOf(original),dao.getAllForPage("memo"))
        dao.replaceOnPage("memo",listOf(original.id),parts)
        assertEquals(storedParts.sortedBy { it.id },dao.getAllForPage("memo").sortedBy { it.id })
    }
    @Test fun replacementFailureRollsBackDeletion() {
        val original=original()
        dao.create(original)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_fragment BEFORE INSERT ON Stroke WHEN NEW.id = 'fragment' BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try {
            dao.replaceOnPage("memo",listOf(original.id),listOf(original.copy(id="fragment")))
            fail("Expected insert failure")
        } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(listOf(original),dao.getAllForPage("memo"))
    }
    @Test fun staleIdsCannotDeleteInkFromAnotherPage() {
        val other=original().copy(pageId="other")
        dao.create(other)
        dao.replaceOnPage("memo",listOf(other.id),emptyList())
        assertEquals(listOf(other),dao.getAllForPage("other"))
    }
    @Test fun replacementsCannotWriteIntoAnotherPage() {
        val original=original()
        dao.create(original)
        try {
            dao.replaceOnPage("memo",listOf(original.id),listOf(original.copy(id="fragment",pageId="other")))
            fail("Expected page guard")
        } catch (_: IllegalArgumentException) { }
        assertEquals(listOf(original),dao.getAllForPage("memo"))
    }
}

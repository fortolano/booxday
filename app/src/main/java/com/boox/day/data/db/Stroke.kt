package com.boox.day.data.db

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.room.*
import com.boox.day.editor.utils.Pen
import kotlinx.serialization.SerialName
import java.util.Date
import java.util.UUID

@kotlinx.serialization.Serializable
data class StrokePoint(
    val x: Float,                   // with scroll
    var y: Float,                   // with scroll
    val pressure: Float? = null,    // relative pressure values 1 to 4096, usually whole number
    val tiltX: Int? = null,         // tilt values in degrees, -90 to 90
    val tiltY: Int? = null,
    val dt: UShort? = null,         // delta time in milliseconds, from first point in stroke, not used yet.
    @SerialName("timestamp") private val legacyTimestamp: Long? = null,
    @SerialName("size") private val legacySize: Float? = null,
)

@Entity(
    foreignKeys = [ForeignKey(
        entity = Page::class,
        parentColumns = arrayOf("id"),
        childColumns = arrayOf("pageId"),
        onDelete = ForeignKey.CASCADE
    )]
)
data class Stroke(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val size: Float,
    val pen: Pen,
    @ColumnInfo(defaultValue = "0xFF000000")
    val color: Int = 0xFF000000.toInt(),
    @ColumnInfo(defaultValue = "4096")
    val maxPressure: Int = 4096,   // might be useful for synchronization between devices

    var top: Float,
    var bottom: Float,
    var left: Float,
    var right: Float,

    val points: List<StrokePoint>,

    @ColumnInfo(index = true)
    val pageId: String,

    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
)

/**
 * Marker the Room converter returns for a stroke whose stored points blob can't be decoded
 * (Converters.toStrokePoints). Compared by identity: a distinct empty list, never `emptyList()`.
 * The page loader and the backup export skip such strokes; the converter refuses to encode the
 * marker, so the damaged blob is never overwritten.
 */
val UNDECODABLE_STROKE_POINTS: List<StrokePoint> = java.util.Collections.unmodifiableList(ArrayList<StrokePoint>())

fun List<StrokePoint>.isUndecodableStrokePoints(): Boolean = this === UNDECODABLE_STROKE_POINTS

val Stroke.isUndecodable: Boolean
    get() = points.isUndecodableStrokePoints()

// DAO
@Dao
interface StrokeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun create(stroke: Stroke): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun create(strokes: List<Stroke>)

    @Update
    fun update(stroke: Stroke)

    // One chunk of deleteAll; callers use deleteAll.
    @Query("DELETE FROM stroke WHERE id IN (:ids)")
    fun deleteChunk(ids: List<String>)

    /** Deletes any number of strokes, [SQLITE_ID_CHUNK_SIZE] IDs per statement, in one transaction. */
    @Transaction
    fun deleteAll(ids: List<String>) {
        ids.chunked(SQLITE_ID_CHUNK_SIZE).forEach { deleteChunk(it) }
    }

    // One chunk of deleteByIdsOnPage; callers use deleteByIdsOnPage.
    @Query("DELETE FROM stroke WHERE pageId = :pageId AND id IN (:ids)")
    fun deleteChunkOnPage(pageId: String, ids: List<String>)

    /**
     * Deletes the given strokes only where they belong to [pageId]. Used by the editor, whose
     * delete list can be stale (e.g. an undo right after a day switch) - another page's ink must
     * never be removed by it.
     */
    @Transaction
    fun deleteByIdsOnPage(pageId: String, ids: List<String>) {
        ids.chunked(SQLITE_ID_CHUNK_SIZE).forEach { deleteChunkOnPage(pageId, it) }
    }

    /**
     * Atomically migrate strokes: delete source strokes and create new ones.
     * This ensures that a crash during migration doesn't leave data in an
     * inconsistent state (strokes deleted but not recreated).
     *
     * @param sourceIds IDs of strokes to delete
     * @param newStrokes New strokes to create at target location
     */
    @Transaction
    fun migrateStrokes(sourceIds: List<String>, newStrokes: List<Stroke>) {
        deleteAll(sourceIds)
        create(newStrokes)
    }

    @Transaction
    @Query("SELECT * FROM stroke WHERE id =:strokeId")
    fun getById(strokeId: String): Stroke

    @Query("SELECT EXISTS(SELECT 1 FROM stroke WHERE pageId = :pageId LIMIT 1)")
    fun hasStrokesForPage(pageId: String): Boolean

    @Query("DELETE FROM stroke WHERE pageId = :pageId")
    fun deleteAllForPage(pageId: String)

    @Query("SELECT DISTINCT pageId FROM stroke WHERE pageId IN (:pageIds)")
    fun getPagesWithStrokes(pageIds: List<String>): List<String>

    @Query("SELECT * FROM stroke WHERE pageId = :pageId")
    fun getAllForPage(pageId: String): List<Stroke>

    /**
     * Batch fetch strokes for multiple pages in one query.
     * PERFORMANCE: Avoids N+1 pattern when checking multiple pages.
     */
    @Query("SELECT * FROM stroke WHERE pageId IN (:pageIds)")
    fun getAllForPages(pageIds: List<String>): List<Stroke>

    /**
     * Check if a page has any content (strokes, images, or text annotations) in one query.
     * PERFORMANCE: Single query vs 3 sequential queries.
     */
    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM stroke WHERE pageId = :pageId
            UNION ALL SELECT 1 FROM image WHERE pageId = :pageId
            UNION ALL SELECT 1 FROM text_annotations WHERE pageId = :pageId
            LIMIT 1
        )
    """)
    fun pageHasAnyContent(pageId: String): Boolean

    // Get all strokes (for backup export)
    @Query("SELECT * FROM stroke")
    fun getAllStrokes(): List<Stroke>

    // Backup export reads strokes in id order, one batch at a time, so memory stays bounded.
    @Query("SELECT * FROM stroke WHERE id > :afterId ORDER BY id LIMIT :limit")
    fun getBatchAfter(afterId: String, limit: Int): List<Stroke>
}

class StrokeRepository(context: Context) {
    var db = AppDatabase.getDatabase(context).strokeDao()

    fun create(stroke: Stroke): Long {
        return db.create(stroke)
    }

    fun create(strokes: List<Stroke>) {
        return db.create(strokes)
    }

    fun update(stroke: Stroke) {
        return db.update(stroke)
    }

    fun deleteAll(ids: List<String>) {
        return db.deleteAll(ids)
    }

    /** Deletes the given strokes only where they belong to [pageId] (see StrokeDao.deleteByIdsOnPage). */
    fun deleteByIdsOnPage(pageId: String, ids: List<String>) {
        db.deleteByIdsOnPage(pageId, ids)
    }

    /**
     * Atomically migrate strokes: delete source strokes and create new ones.
     * Wraps the transactional DAO method for safe stroke migration.
     */
    fun migrateStrokes(sourceIds: List<String>, newStrokes: List<Stroke>) {
        db.migrateStrokes(sourceIds, newStrokes)
    }

    fun getStrokeWithPointsById(strokeId: String): Stroke {
        return db.getById(strokeId)
    }

    fun deleteAllForPage(pageId: String) {
        return db.deleteAllForPage(pageId)
    }

    fun getAllForPage(pageId: String): List<Stroke> {
        return db.getAllForPage(pageId)
    }

    fun getAllForPages(pageIds: List<String>): List<Stroke> {
        if (pageIds.isEmpty()) return emptyList()
        return db.getAllForPages(pageIds)
    }
}

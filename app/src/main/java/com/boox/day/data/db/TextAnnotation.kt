package com.boox.day.data.db

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.Date
import java.util.UUID

/**
 * Text annotation entity - stores recognized handwriting or voice-to-text on canvas.
 * Shared infrastructure for both ML Kit Digital Ink Recognition and Voice Input features.
 */
@Entity(
    tableName = "text_annotations",
    foreignKeys = [
        ForeignKey(
            entity = Page::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("pageId"), Index("content")]
)
data class TextAnnotation(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),

    // Content
    val content: String,

    // Position & Size (in page coordinates)
    var x: Float,
    var y: Float,
    var width: Float,
    var height: Float,

    // Styling
    var fontSize: Float = 16f,
    var fontFamily: String = "sans-serif",
    var fontWeight: Int = 400,  // 400=normal, 700=bold
    var fontStyle: String = "normal",  // normal, italic
    var textColor: Int = 0xFF000000.toInt(),
    var backgroundColor: Int = 0x00000000,  // Transparent default
    var textAlign: String = "left",  // left, center, right
    var padding: Float = 8f,

    // Rich Text Formatting
    val spans: String? = null,  // JSON array of RichTextSpan objects
    val sourceFormat: String = "plain",  // "plain", "markdown", "rich"

    // Metadata
    val pageId: String,
    val sourceType: String = "handwriting",  // "handwriting", "voice", "typed", "ai_conversion"
    val sourceStrokeIds: String? = null,  // Original stroke IDs (comma-separated) for handwriting
    val recognitionLanguage: String? = null,
    val recognitionConfidence: Float? = null,

    // Timestamps
    val createdAt: Date = Date(),
    var updatedAt: Date = Date()
)

/**
 * Data class for search results
 */
data class TextSearchResult(
    val annotationId: String,
    val content: String,
    val pageId: String,
    val dayDate: String?,
    val snippet: String
)

/**
 * DAO for TextAnnotation operations
 */
@Dao
interface TextAnnotationDao {

    // Basic CRUD
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(annotation: TextAnnotation)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(annotations: List<TextAnnotation>)

    @Update
    suspend fun update(annotation: TextAnnotation)

    @Delete
    suspend fun delete(annotation: TextAnnotation)

    @Query("DELETE FROM text_annotations WHERE id = :id")
    suspend fun deleteById(id: String)

    // One chunk of deleteByIds; callers use deleteByIds.
    @Query("DELETE FROM text_annotations WHERE id IN (:ids)")
    suspend fun deleteChunk(ids: List<String>)

    /** Deletes any number of text boxes, [SQLITE_ID_CHUNK_SIZE] IDs per statement, in one transaction. */
    @Transaction
    suspend fun deleteByIds(ids: List<String>) {
        ids.chunked(SQLITE_ID_CHUNK_SIZE).forEach { deleteChunk(it) }
    }

    // Queries by page
    @Query("SELECT * FROM text_annotations WHERE pageId = :pageId ORDER BY createdAt ASC")
    suspend fun getByPageId(pageId: String): List<TextAnnotation>

    @Query("SELECT * FROM text_annotations WHERE pageId = :pageId ORDER BY createdAt ASC")
    fun getByPageIdFlow(pageId: String): Flow<List<TextAnnotation>>

    /** Full screen Tasks (Oct 2026, tasks/TaskPagination): a page's text boxes, inside a blocking transaction. */
    @Query("SELECT * FROM text_annotations WHERE pageId = :pageId")
    fun getForPageNow(pageId: String): List<TextAnnotation>

    /** Full screen Tasks: a text box follows its row to another row and maybe another page. */
    @Query("UPDATE text_annotations SET pageId = :pageId, y = :y WHERE id = :id")
    fun moveTo(id: String, pageId: String, y: Float)

    @Query("SELECT * FROM text_annotations WHERE id = :id")
    suspend fun getById(id: String): TextAnnotation?

    // Simple search across all text annotations
    @Query("""
        SELECT
            id as annotationId,
            content,
            pageId,
            NULL as dayDate,
            content as snippet
        FROM text_annotations
        WHERE content LIKE '%' || :query || '%'
        ORDER BY createdAt DESC
        LIMIT :limit
    """)
    suspend fun searchText(query: String, limit: Int = 50): List<TextSearchResult>

    // Count annotations
    @Query("SELECT COUNT(*) FROM text_annotations WHERE pageId = :pageId")
    suspend fun countByPageId(pageId: String): Int

    @Query("SELECT COUNT(*) FROM text_annotations")
    suspend fun countAll(): Int

    // Non-suspend delete for use in DayPageRepository (called from non-coroutine context)
    @Query("DELETE FROM text_annotations WHERE pageId = :pageId")
    fun deleteAllForPage(pageId: String)

    // Check if page has any text annotations
    @Query("SELECT COUNT(*) > 0 FROM text_annotations WHERE pageId = :pageId")
    fun hasTextAnnotationsForPage(pageId: String): Boolean

    @Query("SELECT DISTINCT pageId FROM text_annotations WHERE pageId IN (:pageIds)")
    fun getPagesWithTextAnnotations(pageIds: List<String>): List<String>

    // Get all annotations (for backup export)
    @Query("SELECT * FROM text_annotations ORDER BY createdAt ASC")
    fun getAllAnnotations(): List<TextAnnotation>
}

/**
 * Repository for TextAnnotation operations
 */
class TextAnnotationRepository(context: Context) {
    private val dao = AppDatabase.getDatabase(context).textAnnotationDao()

    suspend fun insert(annotation: TextAnnotation) = dao.insert(annotation)
    suspend fun update(annotation: TextAnnotation) = dao.update(annotation.copy(updatedAt = Date()))
    suspend fun delete(annotation: TextAnnotation) = dao.delete(annotation)
    suspend fun deleteById(id: String) = dao.deleteById(id)
    suspend fun deleteByIds(ids: List<String>) = dao.deleteByIds(ids)

    suspend fun getByPageId(pageId: String) = dao.getByPageId(pageId)
    fun getByPageIdFlow(pageId: String) = dao.getByPageIdFlow(pageId)
    suspend fun getById(id: String) = dao.getById(id)

    suspend fun search(query: String, limit: Int = 50) = dao.searchText(query, limit)
}

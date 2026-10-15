package com.boox.day.data.db

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import java.util.Date
import java.util.UUID


/**
 * Entity class for file attachments (links to external files like epubs, PDFs).
 * Unlike Images which embed file copies, Attachments store references to external files.
 */
@Entity(
    foreignKeys = [ForeignKey(
        entity = Page::class,
        parentColumns = arrayOf("id"),
        childColumns = arrayOf("pageId"),
        onDelete = ForeignKey.CASCADE
    )]
)
data class Attachment(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    var x: Int = 0,
    var y: Int = 0,
    val width: Int = 340,
    val height: Int = 130,

    val filePath: String,        // Absolute path to the file
    val displayName: String,     // Human-readable name (extracted title or filename)
    val fileType: String,        // "epub", "pdf", etc.

    @ColumnInfo(index = true)
    val pageId: String,

    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
)

// DAO for attachment operations
@Dao
interface AttachmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun create(attachment: Attachment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun create(attachments: List<Attachment>)

    @Update
    fun update(attachment: Attachment)

    // One chunk of deleteAll; callers use deleteAll.
    @Query("DELETE FROM Attachment WHERE id IN (:ids)")
    fun deleteChunk(ids: List<String>)

    /** Deletes any number of attachments, [SQLITE_ID_CHUNK_SIZE] IDs per statement, in one transaction. */
    @Transaction
    fun deleteAll(ids: List<String>) {
        ids.chunked(SQLITE_ID_CHUNK_SIZE).forEach { deleteChunk(it) }
    }

    @Query("SELECT * FROM Attachment WHERE id = :attachmentId")
    fun getById(attachmentId: String): Attachment?

    @Query("SELECT * FROM Attachment WHERE pageId = :pageId")
    fun getByPageId(pageId: String): List<Attachment>

    @Query("SELECT EXISTS(SELECT 1 FROM Attachment WHERE pageId = :pageId LIMIT 1)")
    fun hasAttachmentsForPage(pageId: String): Boolean

    @Query("DELETE FROM Attachment WHERE pageId = :pageId")
    fun deleteAllForPage(pageId: String)

    @Query("SELECT * FROM Attachment")
    fun getAllAttachments(): List<Attachment>
}

// Repository for attachment operations
class AttachmentRepository(context: Context) {
    private val db = AppDatabase.getDatabase(context).attachmentDao()

    fun create(attachment: Attachment): Long {
        return db.create(attachment)
    }

    fun create(attachments: List<Attachment>) {
        db.create(attachments)
    }

    fun update(attachment: Attachment) {
        db.update(attachment)
    }

    fun deleteAll(ids: List<String>) {
        db.deleteAll(ids)
    }

    fun getById(attachmentId: String): Attachment? {
        return db.getById(attachmentId)
    }

    fun getByPageId(pageId: String): List<Attachment> {
        return db.getByPageId(pageId)
    }

    fun hasAttachmentsForPage(pageId: String): Boolean {
        return db.hasAttachmentsForPage(pageId)
    }

    fun deleteAllForPage(pageId: String) {
        db.deleteAllForPage(pageId)
    }
}

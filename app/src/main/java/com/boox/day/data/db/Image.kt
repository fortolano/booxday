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


// Entity class for images
@Entity(
    foreignKeys = [ForeignKey(
        entity = Page::class,
        parentColumns = arrayOf("id"),
        childColumns = arrayOf("pageId"),
        onDelete = ForeignKey.CASCADE
    )]
)
data class Image(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    var x: Int = 0,
    var y: Int = 0,
    val height: Int,
    val width: Int,

    // use uri instead of bytearray
    //val bitmap: ByteArray,
    val uri: String? = null,

    @ColumnInfo(index = true)
    val pageId: String,

    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
)

// DAO for image operations
@Dao
interface ImageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun create(image: Image): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun create(images: List<Image>)

    @Update
    fun update(image: Image)

    // One chunk of deleteAll; callers use deleteAll.
    @Query("DELETE FROM Image WHERE id IN (:ids)")
    fun deleteChunk(ids: List<String>)

    /** Deletes any number of images, [SQLITE_ID_CHUNK_SIZE] IDs per statement, in one transaction. */
    @Transaction
    fun deleteAll(ids: List<String>) {
        ids.chunked(SQLITE_ID_CHUNK_SIZE).forEach { deleteChunk(it) }
    }

    @Transaction
    @Query("SELECT * FROM Image WHERE id = :imageId")
    fun getById(imageId: String): Image

    @Query("SELECT EXISTS(SELECT 1 FROM image WHERE pageId = :pageId LIMIT 1)")
    fun hasImagesForPage(pageId: String): Boolean

    /** Lowest image edge on the page in canvas pixels, null when it has none (full screen memo OCR, Oct 2026). */
    @Query("SELECT MAX(y + height) FROM image WHERE pageId = :pageId")
    fun maxBottomForPage(pageId: String): Int?

    @Query("DELETE FROM image WHERE pageId = :pageId")
    fun deleteAllForPage(pageId: String)

    @Query("SELECT DISTINCT pageId FROM image WHERE pageId IN (:pageIds)")
    fun getPagesWithImages(pageIds: List<String>): List<String>

    // Get all images (for backup export)
    @Query("SELECT * FROM image")
    fun getAllImages(): List<Image>
}

// Repository for stroke operations
class ImageRepository(context: Context) {
    private val db = AppDatabase.getDatabase(context).ImageDao()

    fun create(image: Image): Long {
        return db.create(image)
    }

    fun create(
        imageUri: String,
        //position on canvas
        x: Int,
        y: Int,
        pageId: String,
        //size on canvas
        width: Int,
        height: Int
    ): Long {
        // Prepare the Image object with specified placement
        val imageToSave = Image(
            x = x,
            y = y,
            width = width,
            height = height,
            uri = imageUri,
            pageId = pageId
        )

        // Save the image to the database
        return db.create(imageToSave)
    }

    fun create(images: List<Image>) {
        db.create(images)
    }

    fun update(image: Image) {
        db.update(image)
    }

    fun deleteAll(ids: List<String>) {
        db.deleteAll(ids)
    }

    fun getImageWithPointsById(imageId: String): Image {
        return db.getById(imageId)
    }
}



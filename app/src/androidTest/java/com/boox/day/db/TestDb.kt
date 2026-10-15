package com.boox.day.db

import android.content.Context
import androidx.room.Room
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.Attachment
import com.boox.day.data.db.Image
import com.boox.day.data.db.Page
import com.boox.day.data.db.Stroke
import com.boox.day.data.db.StrokePoint
import com.boox.day.editor.utils.Pen
import java.lang.reflect.Modifier
import java.util.Date
import java.util.UUID

/**
 * Shared helpers for the data-integrity tests (Phase 0/1 of the 2026-10-01 plan).
 * Every database here is in-memory; nothing touches the app's real database file.
 */
object TestDb {

    fun inMemory(context: Context): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()

    fun page(id: String = UUID.randomUUID().toString()) = Page(
        id = id,
        createdAt = Date(1_759_000_000_000),
        updatedAt = Date(1_759_000_100_000)
    )

    fun stroke(pageId: String, id: String = UUID.randomUUID().toString(), dx: Float = 0f) = Stroke(
        id = id,
        size = 3f,
        pen = Pen.BALLPEN,
        top = 10f,
        bottom = 20f,
        left = 10f + dx,
        right = 30f + dx,
        points = listOf(
            StrokePoint(x = 10f + dx, y = 10f, pressure = 1200f),
            StrokePoint(x = 20f + dx, y = 15f, pressure = 1300f),
            StrokePoint(x = 30f + dx, y = 20f, pressure = 1400f)
        ),
        pageId = pageId,
        createdAt = Date(1_759_000_200_000),
        updatedAt = Date(1_759_000_300_000)
    )

    fun image(pageId: String, id: String = UUID.randomUUID().toString(), dx: Int = 0) = Image(
        id = id,
        x = 5 + dx,
        y = 6,
        height = 40,
        width = 50,
        uri = "file:///images/$id.png",
        pageId = pageId,
        createdAt = Date(1_759_000_400_000),
        updatedAt = Date(1_759_000_500_000)
    )

    fun attachment(pageId: String, id: String = UUID.randomUUID().toString()) = Attachment(
        id = id,
        x = 7,
        y = 8,
        filePath = "/storage/emulated/0/Books/$id.pdf",
        displayName = "Handbook $id",
        fileType = "pdf",
        pageId = pageId,
        createdAt = Date(1_759_000_600_000),
        updatedAt = Date(1_759_000_700_000)
    )

    /**
     * Field-by-field differences between [expected] and [actual] rows, matched by [key].
     * Returns human-readable lines so one failing test reports every gap at once.
     */
    fun <T : Any> diff(table: String, expected: List<T>, actual: List<T>, key: (T) -> Any): List<String> {
        val problems = mutableListOf<String>()
        val actualByKey = actual.associateBy(key)
        if (expected.isEmpty()) problems += "$table: test fixture is empty (nothing compared)"
        for (row in expected) {
            val k = key(row)
            val other = actualByKey[k]
            if (other == null) {
                problems += "$table[$k]: missing after restore"
                continue
            }
            if (row == other) continue
            val fields = row.javaClass.declaredFields.filter {
                !Modifier.isStatic(it.modifiers) && !it.isSynthetic
            }
            for (field in fields) {
                field.isAccessible = true
                val a = field.get(row)
                val b = field.get(other)
                val same = if (a is ByteArray && b is ByteArray) a.contentEquals(b) else a == b
                if (!same) problems += "$table[$k].${field.name}: expected=$a actual=$b"
            }
        }
        val expectedKeys = expected.map(key).toSet()
        actual.filter { key(it) !in expectedKeys }.forEach { problems += "$table[${key(it)}]: unexpected extra row" }
        return problems
    }
}

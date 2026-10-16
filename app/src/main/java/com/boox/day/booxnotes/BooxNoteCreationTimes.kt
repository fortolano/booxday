package com.boox.day.booxnotes

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File

/**
 * When Boox created each note (`createdAt` in its NOTE_TREE database), by note id.
 *
 * For a note with no page files on the tablet this is the only real time. The old fallback, the note
 * folder's last change, moves whenever Boox rewrites the folder (Oct 2026: one Boox sync put notes
 * written from March to September at 3 Oct 09:23, inside a General Conference session).
 *
 * Reads a copy of the database in [workDir], never Boox's own file. Any problem = empty map
 * (callers then keep the folder time).
 */
internal class BooxNoteCreationTimes(
    private val workDir: File,
    private val ksyncBase: String = BooxNative.KSYNC_BASE_PATH
) {
    fun load(): Map<String, Long> {
        val source = findNoteTreeDatabase() ?: return emptyMap()
        val copyDir = File(workDir, COPY_DIR)
        return try {
            copyDir.deleteRecursively()
            copyDir.mkdirs()
            val copy = File(copyDir, source.name)
            // WAL first: if Boox checkpoints in between, the database copy already holds those pages
            File(source.path + "-wal").takeIf { it.isFile }?.copyTo(File(copy.path + "-wal"))
            source.copyTo(copy)
            readCreationTimes(copy)
        } catch (e: Exception) {
            Log.w(TAG, "Could not read Boox note creation dates: ${e.message}")
            emptyMap()
        } finally {
            copyDir.deleteRecursively()
        }
    }

    /** Same database the native note list reads (lib.rs load_note_metadata_with_debug) */
    private fun findNoteTreeDatabase(): File? =
        File(ksyncBase, "couch").listFiles()
            ?.firstOrNull { it.isDirectory && it.name.endsWith("-NOTE_TREE.cblite2") && !it.name.startsWith("share_user-") }
            ?.let { File(it, "db.sqlite3") }
            ?.takeIf { it.isFile }

    private fun readCreationTimes(database: File): Map<String, Long> =
        SQLiteDatabase.openDatabase(
            database.path,
            null,
            SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        ).use { db ->
            val sharedKeys = db.rawQuery("SELECT body FROM kv_info WHERE key = 'SharedKeys'", null).use { cursor ->
                if (cursor.moveToFirst()) FleeceReader.read(cursor.getBlob(0)) as? List<*> else null
            }?.map { it as? String ?: "" }.orEmpty()

            val times = HashMap<String, Long>()
            db.rawQuery("SELECT key, body FROM kv_default", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val body = cursor.getBlob(1) ?: continue
                    val note = FleeceReader.read(body, sharedKeys) as? Map<*, *> ?: continue
                    val createdAt = (note["createdAt"] as? Number)?.toLong() ?: continue
                    if (createdAt in PLAUSIBLE_TIMES) times[cursor.getString(0)] = createdAt
                }
            }
            times
        }

    companion object {
        private const val TAG = "BooxNoteCreationTimes"
        private const val COPY_DIR = "boox_note_tree_copy"

        /** 2015 to 2035, the range page-file times are accepted in (BooxNotesIntegration.getShapeFileTimestamps) */
        private val PLAUSIBLE_TIMES = 1_420_070_400_000L..2_051_222_400_000L
    }
}

/**
 * Minimal reader for Fleece, the binary format Couchbase Lite (Boox's note database) stores documents
 * in: numbers, booleans, strings, arrays and dicts. Dict keys are strings or indexes into the
 * database's SharedKeys list. Returns null for data it cannot read.
 */
internal object FleeceReader {

    /** Deeper than any Boox note; stops damaged data that points back into itself */
    private const val MAX_DEPTH = 32

    fun read(data: ByteArray, sharedKeys: List<String> = emptyList()): Any? =
        try {
            if (data.size < 2) null else valueAt(data, root(data), wide = false, sharedKeys = sharedKeys, depth = 0)
        } catch (_: RuntimeException) {
            null
        }

    /** The last 2 bytes point at the root (via a wide pointer when the data is large) */
    private fun root(data: ByteArray): Int {
        var pos = data.size - 2
        if (isPointer(data, pos)) pos = deref(data, pos, wide = false)
        if (isPointer(data, pos)) pos = deref(data, pos, wide = true)
        return pos
    }

    private fun u8(data: ByteArray, pos: Int): Int = data[pos].toInt() and 0xFF

    private fun isPointer(data: ByteArray, pos: Int): Boolean = u8(data, pos) >= 0x80

    /** Pointers count backwards in 2-byte units; external pointers (into another document) are not supported */
    private fun deref(data: ByteArray, pos: Int, wide: Boolean): Int {
        val raw = if (wide) {
            (u8(data, pos) shl 24) or (u8(data, pos + 1) shl 16) or (u8(data, pos + 2) shl 8) or u8(data, pos + 3)
        } else {
            (u8(data, pos) shl 8) or u8(data, pos + 1)
        }
        val externFlag = if (wide) 0x4000_0000 else 0x4000
        require(raw and externFlag == 0) { "external pointer" }
        val target = pos - (raw and (externFlag - 1)) * 2
        require(target in 0 until pos) { "bad pointer" }
        return target
    }

    private fun valueAt(data: ByteArray, slot: Int, wide: Boolean, sharedKeys: List<String>, depth: Int): Any? {
        check(depth <= MAX_DEPTH) { "too deep" }
        val pos = if (isPointer(data, slot)) deref(data, slot, wide) else slot
        val b0 = u8(data, pos)
        return when (b0 shr 4) {
            0x0 -> {  // short int, 12-bit signed
                val v = ((b0 and 0x0F) shl 8) or u8(data, pos + 1)
                (if (v and 0x800 != 0) v - 0x1000 else v).toLong()
            }
            0x1 -> readInt(data, pos, b0)
            0x2 -> if (b0 and 0x08 != 0) {
                java.lang.Double.longBitsToDouble(littleEndian(data, pos + 2, 8))
            } else {
                java.lang.Float.intBitsToFloat(littleEndian(data, pos + 2, 4).toInt()).toDouble()
            }
            0x3 -> when (b0 and 0x0C) {
                0x04 -> false
                0x08 -> true
                else -> null
            }
            0x4 -> String(bytesOf(data, pos, b0), Charsets.UTF_8)
            0x5 -> bytesOf(data, pos, b0)
            0x6, 0x7 -> collection(data, pos, b0, sharedKeys, depth)
            else -> null
        }
    }

    private fun readInt(data: ByteArray, pos: Int, b0: Int): Long {
        val size = (b0 and 0x07) + 1
        val value = littleEndian(data, pos + 1, size)
        val unsigned = b0 and 0x08 != 0
        return if (!unsigned && size < 8) {
            val shift = 64 - size * 8
            (value shl shift) shr shift  // sign-extend
        } else {
            value
        }
    }

    private fun littleEndian(data: ByteArray, start: Int, size: Int): Long {
        var value = 0L
        for (i in size - 1 downTo 0) value = (value shl 8) or u8(data, start + i).toLong()
        return value
    }

    /** Strings and binary data: length in the low 4 bits, or 15 + a varint length */
    private fun bytesOf(data: ByteArray, pos: Int, b0: Int): ByteArray {
        var length = b0 and 0x0F
        var start = pos + 1
        if (length == 0x0F) {
            val (value, size) = varint(data, start)
            length = value
            start += size
        }
        return data.copyOfRange(start, start + length)
    }

    private fun varint(data: ByteArray, start: Int): Pair<Int, Int> {
        var value = 0
        var shift = 0
        var pos = start
        while (true) {
            val b = u8(data, pos++)
            value = value or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) return value to (pos - start)
            shift += 7
            require(shift < 32) { "bad varint" }
        }
    }

    private fun collection(data: ByteArray, pos: Int, b0: Int, sharedKeys: List<String>, depth: Int): Any {
        val wide = b0 and 0x08 != 0
        val width = if (wide) 4 else 2
        var count = ((b0 and 0x07) shl 8) or u8(data, pos + 1)
        var first = pos + 2
        if (count == 0x7FF) {  // long collection: the rest of the count follows as a varint
            val (extra, size) = varint(data, first)
            count += extra
            first += size + (size and 1)
        }
        if (b0 shr 4 == 0x6) {
            return List(count) { i -> valueAt(data, first + i * width, wide, sharedKeys, depth + 1) }
        }
        val dict = LinkedHashMap<String, Any?>(count)
        for (i in 0 until count) {
            val keySlot = first + 2 * i * width
            val key = when (val k = valueAt(data, keySlot, wide, sharedKeys, depth + 1)) {
                is Long -> sharedKeys.getOrNull(k.toInt()) ?: "#$k"
                is String -> k
                else -> continue
            }
            dict[key] = valueAt(data, keySlot + width, wide, sharedKeys, depth + 1)
        }
        return dict
    }
}

package com.boox.day.booxnotes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Fleece is how Boox's note database (Couchbase Lite) stores each note. The bytes below are built by
 * hand and were checked against the decoder that read the Go 10.3's real NOTE_TREE database.
 */
class FleeceReaderTest {

    @Test
    fun `reads a note's title and creation time through shared keys`() {
        val sharedKeys = FleeceReader.read(SHARED_KEYS)

        assertEquals(listOf("title", "createdAt"), sharedKeys)
        assertEquals(
            mapOf("title" to "easter devotional", "createdAt" to 1_774_832_200_000L),
            FleeceReader.read(NOTE, listOf("title", "createdAt"))
        )
    }

    @Test
    fun `reads string keys and small values`() {
        assertEquals(mapOf("a" to 5L), FleeceReader.read(bytes(0x70, 0x01, 0x41, 0x61, 0x00, 0x05, 0x80, 0x03)))
        assertEquals(-1L, FleeceReader.read(bytes(0x0F, 0xFF)))
        assertEquals(true, FleeceReader.read(bytes(0x38, 0x00)))
        assertEquals(false, FleeceReader.read(bytes(0x34, 0x00)))
    }

    @Test
    fun `unknown shared key keeps its index`() {
        assertEquals(mapOf("#1" to 5L), FleeceReader.read(bytes(0x70, 0x01, 0x00, 0x01, 0x00, 0x05, 0x80, 0x03)))
    }

    @Test
    fun `bad data gives null instead of an error`() {
        assertNull(FleeceReader.read(bytes()))
        assertNull(FleeceReader.read(bytes(0x80, 0x00)))             // root pointer to itself
        assertNull(FleeceReader.read(bytes(0x70, 0x05, 0x80, 0x01))) // dict whose key points back at the dict
        assertNull(FleeceReader.read(bytes(0x15, 0x00)))             // 6-byte int cut off
    }

    companion object {
        fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

        /** ["title", "createdAt"] */
        val SHARED_KEYS = bytes(
            0x45, 't'.code, 'i'.code, 't'.code, 'l'.code, 'e'.code,
            0x49, 'c'.code, 'r'.code, 'e'.code, 'a'.code, 't'.code, 'e'.code, 'd'.code, 'A'.code, 't'.code,
            0x60, 0x02, 0x80, 0x09, 0x80, 0x07,
            0x80, 0x03
        )

        /** {title: "easter devotional", createdAt: 1774832200000} with shared keys 0 and 1 */
        val NOTE = bytes(
            0x15, 0x40, 0x29, 0x3E, 0x3C, 0x9D, 0x01, 0x00,             // 6-byte int, padded
            0x4F, 0x11, *"easter devotional".map { it.code }.toIntArray(), 0x00, // 17-char string, padded
            0x70, 0x02, 0x00, 0x00, 0x80, 0x0C, 0x00, 0x01, 0x80, 0x12, // dict: 0 -> string, 1 -> int
            0x80, 0x05                                                  // root
        )
    }
}

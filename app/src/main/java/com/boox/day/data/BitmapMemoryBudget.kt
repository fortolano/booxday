package com.boox.day.data

/** Bounds optional page caches by heap bytes, independently of a tablet's pixel density. */
internal object BitmapMemoryBudget {
    private const val MIB = 1024L * 1024L

    fun previewBytes(maxHeapBytes: Long): Int =
        (maxHeapBytes.coerceAtLeast(8L) / 8).coerceAtMost(32L * MIB).coerceAtLeast(1L).toInt()

    fun pageCacheBytes(maxHeapBytes: Long): Long =
        (maxHeapBytes.coerceAtLeast(4L) / 4).coerceAtMost(64L * MIB).coerceAtLeast(1L)
}

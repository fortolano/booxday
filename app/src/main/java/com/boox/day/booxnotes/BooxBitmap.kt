/**
 * BooxBitmap - Data transfer object for rendered bitmap data from Rust FFI
 *
 * This class serves as the bridge between the native Rust rendering engine and
 * Android's Bitmap system. The Rust library renders Boox note pages to raw ARGB
 * pixel data, which is then wrapped in this class and returned across the JNI boundary.
 *
 * ## Architecture Overview
 *
 * ```
 * Rust FFI (lib.rs)                    Kotlin/JVM
 * ┌─────────────────┐                  ┌─────────────────┐
 * │ raqote canvas   │ ──JNI return──> │ BooxBitmap      │
 * │ renders strokes │                  │ (this class)    │
 * │ to ARGB buffer  │                  │                 │
 * └─────────────────┘                  └────────┬────────┘
 *                                               │
 *                                               ▼
 *                                      ┌─────────────────┐
 *                                      │ Android Bitmap  │
 *                                      │ ARGB_8888       │
 *                                      └─────────────────┘
 * ```
 *
 * ## Usage
 *
 * ```kotlin
 * // Get rendered page from native library
 * val booxBitmap = BooxNative.renderPageOnDevice(basePath, noteId, pageId)
 *
 * // Convert to Android Bitmap for display
 * val bitmap = Bitmap.createBitmap(booxBitmap.width, booxBitmap.height, Bitmap.Config.ARGB_8888)
 * bitmap.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(booxBitmap.buffer))
 * imageView.setImageBitmap(bitmap)
 * ```
 *
 * ## Memory Layout
 *
 * The buffer contains raw ARGB_8888 pixel data in row-major order:
 * - Each pixel is 4 bytes: Alpha, Red, Green, Blue (0xAARRGGBB)
 * - Total buffer size = width × height × 4 bytes
 * - Stride indicates bytes per row (usually width × 4, but may include padding)
 *
 * ## Important Notes for Calendar App Integration
 *
 * 1. This class is created by JNI code in lib.rs - the field names MUST match exactly
 * 2. The buffer is a copy of the Rust data - it's safe to use after the JNI call returns
 * 3. For large notes, consider downscaling on the Rust side for thumbnails
 * 4. The stride field allows for row padding, but current implementation uses tight packing
 *
 * @property width  The width of the rendered bitmap in pixels
 * @property height The height of the rendered bitmap in pixels
 * @property stride The number of bytes per row (width × 4 for ARGB_8888 without padding)
 * @property buffer Raw ARGB_8888 pixel data, size = stride × height bytes
 *
 * @see BooxNative.renderPageOnDevice For the function that creates these objects
 */
package com.boox.day.booxnotes

data class BooxBitmap(
    val width: Int,
    val height: Int,
    val stride: Int,
    val buffer: ByteArray,
) {
    /**
     * Check if the bitmap has valid dimensions for rendering.
     * A bitmap with zero or negative dimensions cannot be displayed.
     */
    fun isValid(): Boolean = width > 0 && height > 0 && buffer.isNotEmpty()

    /**
     * Calculate the expected buffer size based on dimensions.
     * Useful for validation before attempting to create an Android Bitmap.
     */
    fun expectedBufferSize(): Int = stride * height

    // Auto-generated equals/hashCode need to handle ByteArray properly
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BooxBitmap

        if (width != other.width) return false
        if (height != other.height) return false
        if (stride != other.stride) return false
        if (!buffer.contentEquals(other.buffer)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + stride
        result = 31 * result + buffer.contentHashCode()
        return result
    }
}

package com.boox.day.editor.utils

import android.graphics.Rect
import com.boox.day.editor.PageView
import kotlin.math.max

/**
 * Finds optimal placement for a new element avoiding overlap with existing content.
 *
 * Strategy:
 * 1. Grid-scan visible viewport for first available space
 * 2. If viewport full, scan extended area below
 * 3. Fallback: place below all existing content
 */
fun findOptimalPlacement(
    page: PageView,
    elementWidth: Int = 340,
    elementHeight: Int = 130,
    padding: Int = 20,
    gridStep: Int = 50
): Pair<Int, Int> {

    // Collect all occupied rectangles with padding
    val occupiedRects = collectOccupiedRects(page, padding)

    // Define viewport in page coordinates
    val scrollX = page.scroll.x.toInt()
    val scrollY = page.scroll.y.toInt()
    val viewportRect = Rect(
        scrollX + padding,
        scrollY + padding,
        scrollX + page.viewWidth - padding,
        scrollY + page.viewHeight - padding
    )

    // Grid scan viewport first
    gridScan(viewportRect, elementWidth, elementHeight, occupiedRects, gridStep)?.let {
        return it
    }

    // Extend search below viewport (2x viewport height)
    val extendedRect = Rect(
        viewportRect.left,
        viewportRect.bottom,
        viewportRect.right,
        viewportRect.bottom + page.viewHeight * 2
    )
    gridScan(extendedRect, elementWidth, elementHeight, occupiedRects, gridStep)?.let {
        return it
    }

    // Fallback: place below all content, centered horizontally
    return getFallbackPosition(page, occupiedRects, elementWidth, elementHeight, padding)
}

private fun gridScan(
    searchArea: Rect,
    elementWidth: Int,
    elementHeight: Int,
    occupiedRects: List<Rect>,
    gridStep: Int
): Pair<Int, Int>? {
    var y = searchArea.top
    while (y <= searchArea.bottom - elementHeight) {
        var x = searchArea.left
        while (x <= searchArea.right - elementWidth) {
            val candidate = Rect(x, y, x + elementWidth, y + elementHeight)
            if (!intersectsAny(candidate, occupiedRects)) {
                return Pair(x, y)
            }
            x += gridStep
        }
        y += gridStep
    }
    return null
}

private fun collectOccupiedRects(page: PageView, padding: Int): List<Rect> {
    val rects = mutableListOf<Rect>()

    // Strokes (bounds are pre-computed on entity)
    page.strokes.forEach { stroke ->
        rects.add(Rect(
            (stroke.left - padding).toInt(),
            (stroke.top - padding).toInt(),
            (stroke.right + padding).toInt(),
            (stroke.bottom + padding).toInt()
        ))
    }

    // Images
    page.images.forEach { image ->
        rects.add(Rect(
            image.x - padding,
            image.y - padding,
            image.x + image.width + padding,
            image.y + image.height + padding
        ))
    }

    // Existing attachments
    page.attachments.forEach { attachment ->
        rects.add(Rect(
            attachment.x - padding,
            attachment.y - padding,
            attachment.x + attachment.width + padding,
            attachment.y + attachment.height + padding
        ))
    }

    // Text annotations
    page.textAnnotations.forEach { annotation ->
        rects.add(Rect(
            (annotation.x - padding).toInt(),
            (annotation.y - padding).toInt(),
            (annotation.x + annotation.width + padding).toInt(),
            (annotation.y + annotation.height + padding).toInt()
        ))
    }

    return rects
}

private fun intersectsAny(candidate: Rect, occupiedRects: List<Rect>): Boolean {
    return occupiedRects.any { Rect.intersects(candidate, it) }
}

private fun getFallbackPosition(
    page: PageView,
    occupiedRects: List<Rect>,
    elementWidth: Int,
    elementHeight: Int,
    padding: Int
): Pair<Int, Int> {
    // Find lowest point of all content
    val maxBottom = occupiedRects.maxOfOrNull { it.bottom } ?: (page.scroll.y.toInt() + 200)

    // Center horizontally in viewport, place below all content
    val x = page.scroll.x.toInt() + (page.viewWidth - elementWidth) / 2
    val y = maxBottom + padding

    return Pair(x, y)
}

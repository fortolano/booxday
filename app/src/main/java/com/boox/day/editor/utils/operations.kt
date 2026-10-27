package com.boox.day.editor.utils

import android.graphics.Path
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import androidx.compose.ui.geometry.Offset
import androidx.core.graphics.toRect
import androidx.core.graphics.toRegion
import com.boox.day.data.db.Attachment
import com.boox.day.data.db.Image
import com.boox.day.data.db.Stroke
import com.boox.day.data.db.StrokePoint
import com.boox.day.data.db.TextAnnotation
import com.boox.day.data.model.SimplePointF
import com.onyx.android.sdk.data.note.TouchPoint


fun pointsToPath(points: List<SimplePointF>): Path {
    val path = Path()
    val prePoint = PointF(points[0].x, points[0].y)
    path.moveTo(prePoint.x, prePoint.y)

    for (point in points) {
        // skip strange jump point.
        //if (abs(prePoint.y - point.y) >= 30) continue
        path.quadTo(prePoint.x, prePoint.y, point.x, point.y)
        prePoint.x = point.x
        prePoint.y = point.y
    }
    return path
}


fun scaleRect(rect: Rect, scale: Float): Rect {
    return Rect(
        (rect.left / scale).toInt(),
        (rect.top / scale).toInt(),
        (rect.right / scale).toInt(),
        (rect.bottom / scale).toInt()
    )
}

fun toPageCoordinates(rect: Rect, scale: Float, scroll: Offset): Rect {
    return Rect(
        (rect.left.toFloat() / scale + scroll.x).toInt(),
        (rect.top.toFloat() / scale + scroll.y).toInt(),
        (rect.right.toFloat() / scale + scroll.x).toInt(),
        (rect.bottom.toFloat() / scale + scroll.y).toInt()
    )
}


//fun pageAreaToCanvasArea(pageArea: Rect, scroll: Int, scale: Float = 1f): Rect {
//    return scaleRect(
//        Rect(
//            pageArea.left, pageArea.top - scroll, pageArea.right, pageArea.bottom - scroll
//        ), scale
//    )
//}

fun strokeBounds(stroke: Stroke): RectF {
    return RectF(
        stroke.left, stroke.top, stroke.right, stroke.bottom
    )
}

fun imageBounds(image: Image): RectF {
    return RectF(
        image.x.toFloat(),
        image.y.toFloat(),
        image.x + image.width.toFloat(),
        image.y + image.height.toFloat()
    )
}

fun imagePoints(image: Image): Array<Point> {
    return arrayOf(
        Point(image.x, image.y),
        Point(image.x, image.y + image.height),
        Point(image.x + image.width, image.y),
        Point(image.x + image.width, image.y + image.height),
    )
}

/**
 * Every pixel these strokes were drawn on, for redrawing after they are erased or undone.
 * Rounds outward like the area a new stroke is drawn in (strokeBounds(stroke).toRect()). Cutting
 * the edges down with toInt() left a stroke's last pixel column and row on the page after erasing
 * it (Oct 2026, a Brush stroke: column 883 and row 650 stayed).
 */
fun strokeBounds(strokes: List<Stroke>): Rect {
    if (strokes.isEmpty()) return Rect()
    val rect = Rect()
    strokes.forEach { rect.union(strokeBounds(it).toRect()) }
    return rect
}

fun imageBoundsInt(image: Image, padding: Int = 0): Rect {
    return Rect(
        image.x + padding,
        image.y + padding,
        image.x + image.width + padding,
        image.y + image.height + padding
    )
}

fun imageBoundsInt(images: List<Image>): Rect {
    if (images.isEmpty()) return Rect()
    val rect = imageBoundsInt(images[0])
    images.forEach {
        rect.union(
            imageBoundsInt(it)
        )
    }
    return rect
}

fun textAnnotationBoundsInt(annotation: TextAnnotation, padding: Int = 0): Rect {
    return Rect(
        annotation.x.toInt() + padding,
        annotation.y.toInt() + padding,
        (annotation.x + annotation.width).toInt() + padding,
        (annotation.y + annotation.height).toInt() + padding
    )
}

fun textAnnotationBoundsInt(annotations: List<TextAnnotation>): Rect {
    if (annotations.isEmpty()) return Rect()
    val rect = textAnnotationBoundsInt(annotations[0])
    annotations.forEach {
        rect.union(
            textAnnotationBoundsInt(it)
        )
    }
    return rect
}


fun pathToRegion(path: Path): Region {
    val bounds = RectF()
    // TODO: it deprecated, find replacement.
    path.computeBounds(bounds, true)
    val region = Region()
    region.setPath(
        path,
        bounds.toRegion()
    )
    return region
}

fun divideStrokesFromCut(
    strokes: List<Stroke>,
    cutLine: List<SimplePointF>
): Pair<List<Stroke>, List<Stroke>> {
    val maxY = cutLine.maxOfOrNull { it.y }
    val cutArea = listOf(SimplePointF(0f, maxY!!)) + cutLine + listOf(
        SimplePointF(
            cutLine.last().x,
            maxY
        )
    )
    val cutPath = pointsToPath(cutArea)
    cutPath.close()

    val bounds = RectF().apply {
        cutPath.computeBounds(this, true)
    }
    val cutRegion = pathToRegion(cutPath)

    val strokesOver: MutableList<Stroke> = mutableListOf()
    val strokesUnder: MutableList<Stroke> = mutableListOf()

    strokes.forEach { stroke ->
        if (stroke.top > bounds.bottom) strokesUnder.add(stroke)
        else if (stroke.bottom < bounds.top) strokesOver.add(stroke)
        else {
            if (stroke.points.any { point ->
                    cutRegion.contains(
                        point.x.toInt(),
                        point.y.toInt()
                    )
                }) strokesUnder.add(stroke)
            else strokesOver.add(stroke)
        }
    }

    return strokesOver to strokesUnder
}

fun offsetStroke(stroke: Stroke, offset: Offset): Stroke {
    return stroke.copy(
        points = stroke.points.map { p -> p.copy(x = p.x + offset.x, y = p.y + offset.y) },
        top = stroke.top + offset.y,
        bottom = stroke.bottom + offset.y,
        left = stroke.left + offset.x,
        right = stroke.right + offset.x,
    )
}

fun offsetImage(image: Image, offset: Offset): Image {
    return image.copy(
        x = image.x + offset.x.toInt(),
        y = image.y + offset.y.toInt(),
        height = image.height,
        width = image.width,
        uri = image.uri,
        pageId = image.pageId
    )
}

fun attachmentBoundsInt(attachment: Attachment, padding: Int = 0): Rect {
    return Rect(
        attachment.x + padding,
        attachment.y + padding,
        attachment.x + attachment.width + padding,
        attachment.y + attachment.height + padding
    )
}

fun attachmentBoundsInt(attachments: List<Attachment>): Rect {
    if (attachments.isEmpty()) return Rect()
    val rect = attachmentBoundsInt(attachments[0])
    attachments.forEach {
        rect.union(
            attachmentBoundsInt(it)
        )
    }
    return rect
}

fun attachmentPoints(attachment: Attachment): Array<Point> {
    return arrayOf(
        Point(attachment.x, attachment.y),
        Point(attachment.x, attachment.y + attachment.height),
        Point(attachment.x + attachment.width, attachment.y),
        Point(attachment.x + attachment.width, attachment.y + attachment.height),
    )
}

fun offsetAttachment(attachment: Attachment, offset: Offset): Attachment {
    return attachment.copy(
        x = attachment.x + offset.x.toInt(),
        y = attachment.y + offset.y.toInt()
    )
}

fun getModifiedStrokeEndpoints(
    points: List<TouchPoint>,
    scroll: Offset,
    zoomLevel: Float
): Pair<StrokePoint, StrokePoint> {
    if (points.isEmpty()) throw IllegalArgumentException("points list is empty")

    val startIdx = points.size / 10
    val endIdx = (9 * points.size) / 10

    val baseStartPoint = points.first().toStrokePoint(scroll, zoomLevel)
    val baseEndPoint = points.last().toStrokePoint(scroll, zoomLevel)

    val startPoint = baseStartPoint.copy(
        tiltX = points[startIdx].tiltX,
        tiltY = points[startIdx].tiltY,
        pressure = points[startIdx].pressure
    )

    val endPoint = baseEndPoint.copy(
        tiltX = points[endIdx].tiltX,
        tiltY = points[endIdx].tiltY,
        pressure = points[endIdx].pressure
    )

    return Pair(startPoint, endPoint)
}
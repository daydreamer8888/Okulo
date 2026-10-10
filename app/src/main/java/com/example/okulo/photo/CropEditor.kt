@file:Suppress("MagicNumber") // Overlay dimensions and normalized gesture coordinates.

package com.example.okulo.photo

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.okulo.composition.CropBox
import com.example.okulo.composition.CropCorner
import com.example.okulo.composition.CropEdge
import com.example.okulo.composition.CropPoint
import com.example.okulo.composition.resizeCrop
import com.example.okulo.composition.resizeCropEdge
import com.example.okulo.composition.transformCrop
import kotlin.math.abs

@Composable
internal fun CropEditor(bitmap: Bitmap, crop: CropBox, actions: CropActions, lockedRatio: Float? = 1f) {
    Box(Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height)) {
        Image(bitmap.asImageBitmap(), contentDescription = "可调整裁剪框的原图", modifier = Modifier.fillMaxSize())
        CropFrame(crop, actions, Modifier.fillMaxSize().testTag("crop-editor"), lockedRatio)
    }
}

@Composable
internal fun CropFrame(crop: CropBox, actions: CropActions, modifier: Modifier = Modifier, lockedRatio: Float? = null) {
    val current by rememberUpdatedState(crop)
    val callbacks by rememberUpdatedState(actions)
    val locked by rememberUpdatedState(lockedRatio)
    Box(
        modifier.semantics(mergeDescendants = true) {
            customActions = cropAccessibilityActions(current, callbacks)
        }.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var box = current
                val drag = CropDrag(box, down.position, size, 24.dp.toPx())
                if (!drag.accepted) return@awaitEachGesture
                down.consume()
                callbacks.change(box)
                do {
                    val event = awaitPointerEvent()
                    if (event.changes.none { it.pressed }) {
                        event.changes.forEach { it.consume() }
                        break
                    }
                    box = drag.update(box, event, locked)
                    callbacks.change(box)
                    event.changes.forEach { it.consume() }
                } while (true)
                callbacks.finish()
            }
        }
    ) {
        CropOverlay(crop)
    }
}

private class CropDrag(box: CropBox, point: Offset, private val size: IntSize, radius: Float) {
    private var corner = hitCorner(box, point, size.width.toFloat(), size.height.toFloat(), radius)
    private var edge = if (corner == null) {
        hitEdge(box, point, size.width.toFloat(), size.height.toFloat(), radius)
    } else {
        null
    }
    val accepted = corner != null || edge != null || containsPoint(box, point)

    fun update(box: CropBox, event: PointerEvent, ratio: Float?): CropBox {
        if (event.changes.count { it.pressed } > 1) {
            corner = null
            edge = null
        }
        val pan = event.calculatePan()
        val focus = event.calculateCentroid(useCurrent = false)
        val activeCorner = corner
        val activeEdge = edge
        return when {
            activeCorner != null -> resizeCrop(box, activeCorner, pan.x / size.width, pan.y / size.height, ratio)
            activeEdge != null -> resizeCropEdge(
                box,
                activeEdge,
                if (activeEdge.horizontal) pan.x / size.width else pan.y / size.height,
                ratio
            )
            else -> transformCrop(
                box,
                CropPoint(pan.x / size.width, pan.y / size.height),
                event.calculateZoom(),
                CropPoint(focus.x / size.width, focus.y / size.height),
                ratio ?: (box.width / box.height)
            )
        }
    }

    private fun containsPoint(box: CropBox, point: Offset): Boolean =
        point.x / size.width in box.left..box.right && point.y / size.height in box.top..box.bottom
}

@Composable
private fun CropOverlay(crop: CropBox) {
    Canvas(Modifier.fillMaxSize()) {
        val bounds = androidx.compose.ui.geometry.Rect(
            crop.left * size.width,
            crop.top * size.height,
            crop.right * size.width,
            crop.bottom * size.height
        )
        val shade = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size))
            addRect(bounds)
        }
        drawPath(shade, Color.Black.copy(alpha = 0.4f))
        drawRect(Color.White, bounds.topLeft, bounds.size, style = Stroke(3.dp.toPx()))
        for (fraction in listOf(1f / 3f, 2f / 3f)) {
            val x = bounds.left + bounds.width * fraction
            val y = bounds.top + bounds.height * fraction
            val guide = Color.White.copy(alpha = 0.35f)
            drawLine(guide, Offset(x, bounds.top), Offset(x, bounds.bottom), 1.dp.toPx())
            drawLine(guide, Offset(bounds.left, y), Offset(bounds.right, y), 1.dp.toPx())
        }
        val stroke = 3.dp.toPx()
        val length = minOf(12.dp.toPx(), bounds.width / 2f, bounds.height / 2f)
        CropCorner.entries.forEach { corner ->
            val point = cornerPosition(crop, corner, size.width, size.height) +
                Offset(-corner.horizontal * stroke / 2f, -corner.vertical * stroke / 2f)
            drawLine(Color.White, point, point + Offset(-corner.horizontal * length, 0f), stroke)
            drawLine(Color.White, point, point + Offset(0f, -corner.vertical * length), stroke)
        }
    }
}

private fun cornerPosition(box: CropBox, corner: CropCorner, width: Float, height: Float): Offset = Offset(
    (if (corner.horizontal < 0) box.left else box.right) * width,
    (if (corner.vertical < 0) box.top else box.bottom) * height
)

private fun hitCorner(box: CropBox, point: Offset, width: Float, height: Float, radius: Float): CropCorner? =
    CropCorner.entries.minByOrNull { (cornerPosition(box, it, width, height) - point).getDistance() }
        ?.takeIf { (cornerPosition(box, it, width, height) - point).getDistance() <= radius }

private fun hitEdge(box: CropBox, point: Offset, width: Float, height: Float, radius: Float): CropEdge? {
    val x = point.x / width
    val y = point.y / height
    return CropEdge.entries.firstOrNull { edge ->
        if (edge.horizontal) {
            y in box.top..box.bottom && abs(x - if (edge.direction < 0) box.left else box.right) <= radius / width
        } else {
            x in box.left..box.right && abs(y - if (edge.direction < 0) box.top else box.bottom) <= radius / height
        }
    }
}

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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.okulo.composition.CropBox
import com.example.okulo.composition.CropCorner
import com.example.okulo.composition.CropPoint
import com.example.okulo.composition.resizeCrop
import com.example.okulo.composition.transformCrop

@Composable
internal fun CropEditor(bitmap: Bitmap, crop: CropBox, actions: CropActions, lockedRatio: Float? = 1f) {
    val current by rememberUpdatedState(crop)
    val callbacks by rememberUpdatedState(actions)
    val locked by rememberUpdatedState(lockedRatio)
    Box(
        Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height)
            .testTag("crop-editor").pointerInput(bitmap) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var box = current
                    var corner =
                        hitCorner(box, down.position, size.width.toFloat(), size.height.toFloat(), 24.dp.toPx())
                    val x = down.position.x / size.width
                    val y = down.position.y / size.height
                    if (corner == null && (x !in box.left..box.right || y !in box.top..box.bottom)) {
                        return@awaitEachGesture
                    }
                    down.consume()
                    callbacks.change(box)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.none { it.pressed }) {
                            event.changes.forEach { it.consume() }
                            break
                        }
                        val pan = event.calculatePan()
                        val focus = event.calculateCentroid(useCurrent = false)
                        if (event.changes.count { it.pressed } > 1) corner = null
                        box = if (corner != null) {
                            resizeCrop(
                                box = box,
                                corner = corner,
                                dx = pan.x / size.width,
                                dy = pan.y / size.height,
                                lockedRatio = locked
                            )
                        } else {
                            transformCrop(
                                box = box,
                                delta = CropPoint(pan.x / size.width, pan.y / size.height),
                                zoom = event.calculateZoom(),
                                focus = CropPoint(focus.x / size.width, focus.y / size.height),
                                ratio = locked ?: (box.width / box.height)
                            )
                        }
                        callbacks.change(box)
                        event.changes.forEach { it.consume() }
                    } while (true)
                    callbacks.finish()
                }
            }
    ) {
        Image(bitmap.asImageBitmap(), contentDescription = "可调整裁剪框的原图", modifier = Modifier.fillMaxSize())
        CropOverlay(crop)
    }
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
        CropCorner.entries.forEach { corner ->
            drawCircle(Color.White, 6.dp.toPx(), cornerPosition(crop, corner, size.width, size.height))
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

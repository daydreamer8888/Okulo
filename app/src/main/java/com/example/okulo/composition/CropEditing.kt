package com.example.okulo.composition

internal const val MIN_EDIT_SIDE = 0.1f

data class CropPoint(val x: Float, val y: Float)

enum class CropCorner(val horizontal: Int, val vertical: Int) {
    TopLeft(-1, -1), TopRight(1, -1), BottomLeft(-1, 1), BottomRight(1, 1)
}

/** Translation and pinch zoom retain the current crop's aspect ratio. */
fun transformCrop(
    box: CropBox,
    delta: CropPoint,
    zoom: Float,
    focus: CropPoint,
    ratio: Float = 1f
): CropBox {
    val side = (box.width * zoom).coerceIn(MIN_EDIT_SIDE * minOf(1f, ratio), minOf(1f, ratio))
    val height = side / ratio
    val left = (focus.x - (focus.x - box.left) / box.width * side + delta.x).coerceIn(0f, 1f - side)
    val top = (focus.y - (focus.y - box.top) / box.height * height + delta.y).coerceIn(0f, 1f - height)
    return CropBox(left, top, left + side, top + height)
}

fun resizeCrop(
    box: CropBox,
    corner: CropCorner,
    dx: Float,
    dy: Float,
    lockedRatio: Float? = 1f
): CropBox {
    val anchorX = if (corner.horizontal > 0) box.left else box.right
    val anchorY = if (corner.vertical > 0) box.top else box.bottom
    val availableX = if (corner.horizontal > 0) 1f - anchorX else anchorX
    val availableY = if (corner.vertical > 0) 1f - anchorY else anchorY
    val width: Float
    val height: Float
    if (lockedRatio == null) {
        width = (box.width + dx * corner.horizontal).coerceIn(minOf(MIN_EDIT_SIDE, availableX), availableX)
        height = (box.height + dy * corner.vertical).coerceIn(minOf(MIN_EDIT_SIDE, availableY), availableY)
    } else {
        val change = (dx * corner.horizontal + dy * corner.vertical * lockedRatio) / 2
        val maximum = minOf(availableX, availableY * lockedRatio)
        val minimum = MIN_EDIT_SIDE * minOf(1f, lockedRatio)
        width = (box.width + change).coerceIn(minOf(minimum, maximum), maximum)
        height = width / lockedRatio
    }
    val left = if (corner.horizontal > 0) anchorX else anchorX - width
    val top = if (corner.vertical > 0) anchorY else anchorY - height
    return CropBox(left, top, left + width, top + height)
}

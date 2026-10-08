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

enum class CropEdge(val horizontal: Boolean, val direction: Int) {
    Left(true, -1), Top(false, -1), Right(true, 1), Bottom(false, 1)
}

/** Keep the opposite edge fixed and center the other axis when an aspect is locked. */
fun resizeCropEdge(box: CropBox, edge: CropEdge, delta: Float, lockedRatio: Float? = 1f): CropBox {
    if (edge.horizontal) return resizeHorizontalEdge(box, edge.direction, delta, lockedRatio)
    val transposed = CropBox(box.top, box.left, box.bottom, box.right)
    val resized = resizeHorizontalEdge(transposed, edge.direction, delta, lockedRatio?.let { 1f / it })
    return CropBox(resized.top, resized.left, resized.bottom, resized.right)
}

private fun resizeHorizontalEdge(box: CropBox, direction: Int, delta: Float, ratio: Float?): CropBox {
    val center = (box.top + box.bottom) / 2
    val anchor = if (direction > 0) box.left else box.right
    val available = if (direction > 0) 1f - anchor else anchor
    val maximum = ratio?.let { minOf(available, 2 * minOf(center, 1f - center) * it) } ?: available
    val minimum = MIN_EDIT_SIDE * minOf(1f, ratio ?: 1f)
    val width = (box.width + delta * direction).coerceIn(minOf(minimum, maximum), maximum)
    val left = if (direction > 0) anchor else anchor - width
    val right = if (direction > 0) anchor + width else anchor
    val height = ratio?.let { width / it }
    val top = height?.let { (center - it / 2).coerceAtLeast(0f) } ?: box.top
    val bottom = height?.let { (center + it / 2).coerceAtMost(1f) } ?: box.bottom
    return CropBox(left, top, right, bottom)
}

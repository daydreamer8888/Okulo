package com.example.okulo.composition

internal const val MIN_EDIT_SIDE = 0.1f

data class CropPoint(val x: Float, val y: Float)

enum class CropCorner(val horizontal: Int, val vertical: Int) {
    TopLeft(-1, -1), TopRight(1, -1), BottomLeft(-1, 1), BottomRight(1, 1)
}

/** Normalized square edges preserve the source photo's pixel aspect ratio. */
fun transformCrop(box: CropBox, delta: CropPoint, zoom: Float, focus: CropPoint): CropBox {
    val side = (box.width * zoom).coerceIn(MIN_EDIT_SIDE, 1f)
    val left = (focus.x - (focus.x - box.left) / box.width * side + delta.x).coerceIn(0f, 1f - side)
    val top = (focus.y - (focus.y - box.top) / box.height * side + delta.y).coerceIn(0f, 1f - side)
    return CropBox(left, top, left + side, top + side)
}

fun resizeCrop(box: CropBox, corner: CropCorner, dx: Float, dy: Float): CropBox {
    val anchorX = if (corner.horizontal > 0) box.left else box.right
    val anchorY = if (corner.vertical > 0) box.top else box.bottom
    val availableX = if (corner.horizontal > 0) 1f - anchorX else anchorX
    val availableY = if (corner.vertical > 0) 1f - anchorY else anchorY
    val change = (dx * corner.horizontal + dy * corner.vertical) / 2
    val maximum = minOf(availableX, availableY)
    val side = (box.width + change).coerceIn(minOf(MIN_EDIT_SIDE, maximum), maximum)
    val left = if (corner.horizontal > 0) anchorX else anchorX - side
    val top = if (corner.vertical > 0) anchorY else anchorY - side
    return CropBox(left, top, left + side, top + side)
}

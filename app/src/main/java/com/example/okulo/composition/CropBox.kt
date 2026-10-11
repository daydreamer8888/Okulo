package com.example.okulo.composition

data class CropBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val area: Float get() = width * height

    companion object {
        val FullFrame = CropBox(0f, 0f, 1f, 1f)
    }
}

fun intersectionOverUnion(a: CropBox, b: CropBox): Float {
    val intersection = (minOf(a.right, b.right) - maxOf(a.left, b.left)).coerceAtLeast(0f) *
        (minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)).coerceAtLeast(0f)
    return intersection / (a.area + b.area - intersection)
}

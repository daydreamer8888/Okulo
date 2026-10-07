package com.example.okulo.composition

import kotlin.math.sqrt

data class CropBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val area: Float get() = width * height

    companion object {
        val FullFrame = CropBox(0f, 0f, 1f, 1f)
    }
}

private const val AREA_LEVELS = 10
private const val MIN_AREA = 0.5
private const val AREA_STEP = 0.05
private const val GRID_POINTS = 5

/** Normalized edge coordinates, in the same order as the validated search grid. */
fun cropCandidates(): List<CropBox> = buildList {
    add(CropBox.FullFrame)
    repeat(AREA_LEVELS) { areaStep ->
        val side = sqrt(MIN_AREA + areaStep * AREA_STEP)
        repeat(GRID_POINTS) { column ->
            repeat(GRID_POINTS) { row ->
                val left = (1.0 - side) * column / (GRID_POINTS - 1)
                val top = (1.0 - side) * row / (GRID_POINTS - 1)
                add(CropBox(left.toFloat(), top.toFloat(), (left + side).toFloat(), (top + side).toFloat()))
            }
        }
    }
}

fun intersectionOverUnion(a: CropBox, b: CropBox): Float {
    val intersection = (minOf(a.right, b.right) - maxOf(a.left, b.left)).coerceAtLeast(0f) *
        (minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)).coerceAtLeast(0f)
    return intersection / (a.area + b.area - intersection)
}

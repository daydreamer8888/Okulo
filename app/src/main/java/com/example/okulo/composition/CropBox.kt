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

/** The first entry is the source-score baseline, followed by target-aspect candidates. */
fun cropCandidates(normalizedRatio: Float): List<CropBox> {
    require(normalizedRatio.isFinite() && normalizedRatio > 0f)
    if (normalizedRatio == 1f) return cropCandidates()
    val areas = (0..AREA_LEVELS).map { 1.0 - it * AREA_STEP }
    return listOf(CropBox.FullFrame) + aspectGrid(normalizedRatio, GRID_POINTS, areas)
}

/** Sample multiple aspects without multiplying the inference candidate budget. */
fun freeCropCandidates(width: Int, height: Int): List<CropBox> {
    require(width > 0 && height > 0)
    val ratios = CropAspect.entries.mapNotNull { it.normalizedRatio(width, height) }.distinct()
    val candidates = ratios.flatMap { aspectGrid(it, FREE_GRID_POINTS, FREE_AREAS) }.distinct()
    return listOf(CropBox.FullFrame) + candidates
}

private const val FREE_GRID_POINTS = 3

@Suppress("MagicNumber") // Retained area fractions of each aspect's largest fitting crop.
private val FREE_AREAS = listOf(1.0, 0.8, 0.65, 0.5)

private fun aspectGrid(ratio: Float, points: Int, areas: List<Double>): List<CropBox> = buildList {
    val maximumWidth = minOf(1.0, ratio.toDouble())
    val maximumHeight = minOf(1.0, 1.0 / ratio)
    for (area in areas) {
        val width = maximumWidth * sqrt(area)
        val height = maximumHeight * sqrt(area)
        repeat(points) { column ->
            repeat(points) { row ->
                val left = (1.0 - width) * column / (points - 1)
                val top = (1.0 - height) * row / (points - 1)
                add(CropBox(left.toFloat(), top.toFloat(), (left + width).toFloat(), (top + height).toFloat()))
            }
        }
    }
}.distinct()

fun intersectionOverUnion(a: CropBox, b: CropBox): Float {
    val intersection = (minOf(a.right, b.right) - maxOf(a.left, b.left)).coerceAtLeast(0f) *
        (minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)).coerceAtLeast(0f)
    return intersection / (a.area + b.area - intersection)
}

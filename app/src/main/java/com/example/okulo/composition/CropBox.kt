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
fun cropCandidates(normalizedRatio: Float, minimumArea: Float = MIN_AREA.toFloat()): List<CropBox> {
    require(normalizedRatio.isFinite() && normalizedRatio > 0f)
    require(minimumArea > 0f && minimumArea <= 1f)
    if (normalizedRatio == 1f && minimumArea == MIN_AREA.toFloat()) return cropCandidates()
    val maximumArea = minOf(normalizedRatio.toDouble(), 1.0 / normalizedRatio)
    val areas = if (normalizedRatio == 1f) {
        (0 until AREA_LEVELS).map { minimumArea + (1.0 - minimumArea) * it / AREA_LEVELS }
    } else {
        areaLevels(maximumArea, minimumArea.toDouble(), AREA_LEVELS)
    }
    return listOf(CropBox.FullFrame) + aspectGrid(normalizedRatio, GRID_POINTS, areas)
}

/** Balance aspect coverage within four hundred unique coarse-search crops. */
fun freeCropCandidates(width: Int, height: Int, minimumArea: Float = MIN_AREA.toFloat()): List<CropBox> {
    require(width > 0 && height > 0)
    require(minimumArea > 0f && minimumArea <= 1f)
    val ratios = CropAspect.entries.mapNotNull { it.normalizedRatio(width, height) }.distinct().filter {
        minOf(it, 1f / it) >= minimumArea
    }
    val levels = (FREE_COARSE_BUDGET / ratios.size - FREE_GRID_POINTS) /
        (FREE_GRID_POINTS * FREE_GRID_POINTS) + 1
    val candidates = ratios.flatMap { ratio ->
        val maximumArea = minOf(ratio.toDouble(), 1.0 / ratio)
        aspectGrid(ratio, FREE_GRID_POINTS, areaLevels(maximumArea, minimumArea.toDouble(), levels))
    }.distinct()
    return listOf(CropBox.FullFrame) + candidates.filterNot { it == CropBox.FullFrame }
}

private fun areaLevels(maximum: Double, minimum: Double, count: Int): List<Double> =
    if (minimum >= maximum) {
        listOf(maximum)
    } else {
        (0 until count).map { maximum - (maximum - minimum) * it / (count - 1) }
    }

private const val FREE_GRID_POINTS = 3
private const val FREE_COARSE_BUDGET = 400

private fun aspectGrid(ratio: Float, points: Int, areas: List<Double>): List<CropBox> = buildList {
    val maximumWidth = minOf(1.0, ratio.toDouble())
    val maximumHeight = minOf(1.0, 1.0 / ratio)
    for (area in areas) {
        val scale = sqrt(area / (maximumWidth * maximumHeight))
        val width = maximumWidth * scale
        val height = maximumHeight * scale
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

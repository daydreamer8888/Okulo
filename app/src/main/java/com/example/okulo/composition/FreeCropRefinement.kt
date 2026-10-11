package com.example.okulo.composition

import kotlin.math.abs
import kotlin.math.sqrt

private const val SEEDS_PER_ASPECT = 2
private const val MAX_SEED_OVERLAP = 0.85f
private const val TRANSLATION_STEP = 0.25f
private const val SCALE_STEP = 0.025f
private const val ASPECT_STEP = 0.05f
private const val AREA_TOLERANCE = 1e-6f
private const val REFINEMENT_BUDGET = 200

/** Reserve refinement for each aspect and avoid spending it on overlapping winners. */
internal fun refineFreeCrops(
    width: Int,
    height: Int,
    crops: List<CropBox>,
    scores: FloatArray,
    minimumArea: Float
): List<CropBox> {
    val ratios = CropAspect.entries.mapNotNull { it.normalizedRatio(width, height) }.distinct()
    val groups = (1 until crops.size).groupBy { index ->
        ratios.minBy { abs(crops[index].width / crops[index].height / it - 1f) }
    }
    val seen = crops.toMutableSet()
    return ratios.flatMap { ratio ->
        val seeds = mutableListOf<CropBox>()
        groups[ratio].orEmpty().sortedByDescending { scores[it] }.forEach { index ->
            val crop = crops[index]
            if (seeds.size < SEEDS_PER_ASPECT && seeds.all { intersectionOverUnion(it, crop) < MAX_SEED_OVERLAP }) {
                seeds.add(crop)
            }
        }
        seeds.flatMap { neighbors(it, minimumArea) }
    }.filter { seen.add(it) }.take(REFINEMENT_BUDGET)
}

private fun neighbors(crop: CropBox, minimumArea: Float): List<CropBox> = buildList {
    val dx = (1f - crop.width) * TRANSLATION_STEP
    val dy = (1f - crop.height) * TRANSLATION_STEP
    for (x in -1..1) {
        for (y in -1..1) {
            if (x != 0 || y != 0) add(fittedCrop(crop, x * dx, y * dy))
        }
    }
    for (direction in listOf(-1, 1)) {
        val scale = 1f + direction * SCALE_STEP
        add(fittedCrop(crop, width = crop.width * scale, height = crop.height * scale))
        val aspectScale = sqrt(1f + direction * ASPECT_STEP)
        add(fittedCrop(crop, width = crop.width * aspectScale, height = crop.height / aspectScale))
    }
}.filter { box ->
    box.area >= minimumArea - AREA_TOLERANCE
}

private fun fittedCrop(
    source: CropBox,
    dx: Float = 0f,
    dy: Float = 0f,
    width: Float = source.width,
    height: Float = source.height
): CropBox {
    val fit = minOf(1f, 1f / width, 1f / height)
    val fittedWidth = width * fit
    val fittedHeight = height * fit
    val left = ((source.left + source.right - fittedWidth) / 2 + dx).coerceIn(0f, 1f - fittedWidth)
    val top = ((source.top + source.bottom - fittedHeight) / 2 + dy).coerceIn(0f, 1f - fittedHeight)
    return CropBox(left, top, left + fittedWidth, top + fittedHeight)
}

@file:Suppress("MagicNumber") // Common photo and video aspect ratios.

package com.example.okulo.composition

import kotlin.math.sqrt

enum class CropAspect(val title: String, private val pixelRatio: Float? = null) {
    Original("原图"), Free("自由"), Square("1:1", 1f),
    FourThree("4:3", 4f / 3f), ThreeTwo("3:2", 3f / 2f),
    Wide("16:9", 16f / 9f), Portrait("9:16", 9f / 16f),
    PortraitFourThree("3:4", 3f / 4f), PortraitThreeTwo("2:3", 2f / 3f);

    fun rotated(): CropAspect = when (this) {
        Wide -> Portrait
        Portrait -> Wide
        FourThree -> PortraitFourThree
        PortraitFourThree -> FourThree
        ThreeTwo -> PortraitThreeTwo
        PortraitThreeTwo -> ThreeTwo
        else -> this
    }

    fun normalizedRatio(width: Int, height: Int): Float? = when (this) {
        Free -> null
        Original -> 1f
        else -> checkNotNull(pixelRatio) * height / width
    }
}

/** Preserve the crop's area and center as far as the photo bounds allow. */
fun fitCropAspect(box: CropBox, ratio: Float?): CropBox {
    if (ratio == null) return box
    val width = sqrt(box.area * ratio).coerceAtMost(minOf(1f, ratio))
    val height = width / ratio
    val left = ((box.left + box.right - width) / 2).coerceIn(0f, 1f - width)
    val top = ((box.top + box.bottom - height) / 2).coerceIn(0f, 1f - height)
    return CropBox(left, top, left + width, top + height)
}

package com.example.okulo.composition

/** Normalized crop width/height, or null for free search. Area is relative to the full source image. */
data class CropSearch(val normalizedRatio: Float?, val minimumArea: Float) {
    init {
        require(normalizedRatio == null || normalizedRatio.isFinite() && normalizedRatio > 0f)
        require(minimumArea.isFinite() && minimumArea > 0f && minimumArea <= 1f)
    }
}

internal const val DEFAULT_CROP_AREA_PERCENT = 20
internal const val DEFAULT_MINIMUM_CROP_AREA = DEFAULT_CROP_AREA_PERCENT / 100f

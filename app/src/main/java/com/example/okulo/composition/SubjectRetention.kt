package com.example.okulo.composition

/** Search geometry owns subject retention, independently of scoring-model inputs. */
internal fun subjectBounds(region: CropBox): CropBox {
    require(listOf(region.left, region.top, region.right, region.bottom).all { it.isFinite() })
    require(region.left >= 0f && region.top >= 0f && region.right <= 1f && region.bottom <= 1f)
    require(region.width > 0f && region.height > 0f)
    val horizontal = region.width * SUBJECT_MARGIN
    val vertical = region.height * SUBJECT_MARGIN
    return CropBox(
        (region.left - horizontal).coerceAtLeast(0f),
        (region.top - vertical).coerceAtLeast(0f),
        (region.right + horizontal).coerceAtMost(1f),
        (region.bottom + vertical).coerceAtMost(1f)
    )
}

internal fun CropBox.contains(region: CropBox): Boolean =
    left <= region.left && top <= region.top && right >= region.right && bottom >= region.bottom

private const val SUBJECT_MARGIN = 0.1f

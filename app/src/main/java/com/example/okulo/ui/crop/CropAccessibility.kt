package com.example.okulo.ui.crop

import androidx.compose.ui.semantics.CustomAccessibilityAction
import com.example.okulo.composition.CropBox
import com.example.okulo.composition.CropPoint
import com.example.okulo.composition.transformCrop

internal fun cropAccessibilityActions(crop: CropBox, actions: CropActions): List<CustomAccessibilityAction> {
    val center = CropPoint((crop.left + crop.right) / 2, (crop.top + crop.bottom) / 2)
    fun edit(label: String, delta: CropPoint, zoom: Float = 1f) = CustomAccessibilityAction(label) {
        val next = transformCrop(crop, delta, zoom, center, crop.width / crop.height)
        if (next == crop) {
            false
        } else {
            actions.change(next)
            actions.finish()
            true
        }
    }
    return listOf(
        edit("向左移动", CropPoint(-NUDGE, 0f)),
        edit("向右移动", CropPoint(NUDGE, 0f)),
        edit("向上移动", CropPoint(0f, -NUDGE)),
        edit("向下移动", CropPoint(0f, NUDGE)),
        edit("放大", CropPoint(0f, 0f), ZOOM_STEP),
        edit("缩小", CropPoint(0f, 0f), 1f / ZOOM_STEP)
    )
}

private const val NUDGE = 0.05f
private const val ZOOM_STEP = 1.1f

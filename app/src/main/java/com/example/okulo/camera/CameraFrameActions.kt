package com.example.okulo.camera

import android.graphics.Bitmap
import com.example.okulo.composition.DEFAULT_MINIMUM_CROP_AREA

internal class CameraFrameActions(
    private val composition: CameraComposition,
    private val source: () -> Bitmap?,

    private val minimumArea: () -> Float = { DEFAULT_MINIMUM_CROP_AREA },
    private val frozenCapture: (((FrozenCameraPhoto?) -> Unit) -> Unit)? = null
) {
    fun selectSubject() { composition.beginSubjectSelection(frozenCapture) }

    fun recommend() {
        if (composition.state.value.selectingSubject) return
        frozenCapture?.let { capture ->
            composition.recommendFrozen(capture, minimumArea())
            return
        }
        val frame = source()
        if (frame != null) composition.recommend(frame, minimumArea()) else composition.previewUnavailable()
    }

    fun score() {
        val frame = composition.state.value.frozenPhoto?.image?.copy(Bitmap.Config.ARGB_8888, false) ?: source()
        if (frame != null) composition.evaluateCrop(frame) else composition.previewUnavailable()
    }
}

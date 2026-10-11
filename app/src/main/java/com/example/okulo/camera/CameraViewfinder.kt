package com.example.okulo.camera

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import com.example.okulo.composition.CropBox
import com.example.okulo.ui.crop.CropActions
import com.example.okulo.ui.crop.CropFrame

@Composable
internal fun CameraViewfinder(
    crop: CropBox?,
    actions: CropActions,
    gestureModifier: Modifier = Modifier,
    controls: @Composable BoxScope.() -> Unit = {},
    frozenPhoto: FrozenCameraPhoto? = null,
    preview: @Composable () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Box(gestureModifier.fillMaxSize()) {
            preview()
            if (frozenPhoto != null) {
                Image(
                    frozenPhoto.image.asImageBitmap(),
                    contentDescription = "定格画面",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().pointerInput(frozenPhoto) { detectTapGestures {} }
                )
            }
            if (crop != null) CropFrame(crop, actions, Modifier.fillMaxSize().testTag("camera-crop"))
        }
        controls()
    }
}

package com.example.okulo.camera

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.okulo.composition.CropBox
import com.example.okulo.photo.CropActions
import com.example.okulo.photo.CropFrame

@Composable
internal fun CameraViewfinder(crop: CropBox?, actions: CropActions, preview: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        preview()
        if (crop != null) CropFrame(crop, actions, Modifier.fillMaxSize().testTag("camera-crop"))
    }
}

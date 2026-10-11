package com.example.okulo.photo

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import com.example.okulo.composition.DEFAULT_CROP_AREA_PERCENT
import com.example.okulo.image.cropPreview
import com.example.okulo.ui.crop.CropActions
import com.example.okulo.ui.crop.CropEditor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhotoViewPanel(
    state: PhotoState,
    photo: Bitmap,
    actions: CropActions,
    modifier: Modifier = Modifier,
    showModelScores: Boolean = false,
    cropWarningPercent: Int = DEFAULT_CROP_AREA_PERCENT
) {
    var showPreview by rememberSaveable(photo) { mutableStateOf(false) }
    val crop = state.displayedCrop
    val preview = showPreview && crop != null
    Column(modifier) {
        PrimaryTabRow(selectedTabIndex = if (preview) 1 else 0) {
            Tab(selected = !preview, onClick = { showPreview = false }, text = { Text("编辑") })
            Tab(selected = preview, onClick = { showPreview = true }, enabled = crop != null, text = { Text("预览") })
        }
        PhotoImageArea(
            state,
            photo.width.toFloat() / photo.height,
            showModelScores,
            Modifier.weight(1f).fillMaxWidth().testTag("photo-image-area"),
            cropWarningPercent
        ) {
            Crossfade(preview, animationSpec = tween(VIEW_FADE_MILLIS), label = "photo-view") { showingPreview ->
                if (showingPreview && crop != null) {
                    val bitmap = remember(photo, crop) { cropPreview(photo, crop) }
                    Image(bitmap.asImageBitmap(), "裁剪预览", Modifier.fillMaxSize())
                } else if (crop != null) {
                    CropEditor(photo, crop, actions, state.aspect.normalizedRatio(photo.width, photo.height))
                } else {
                    Image(photo.asImageBitmap(), "原图", Modifier.fillMaxSize())
                }
            }
        }
    }
}

private const val VIEW_FADE_MILLIS = 120

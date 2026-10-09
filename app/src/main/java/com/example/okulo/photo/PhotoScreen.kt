@file:Suppress("MagicNumber") // Spacing and typography values in the photo screen.

package com.example.okulo.photo

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.okulo.R
import com.example.okulo.composition.CropAspect
import com.example.okulo.composition.CropBox
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

@Composable
internal fun PhotoScreen(
    state: PhotoState,
    onPhoto: (Uri) -> Unit,
    onAnalyze: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = onAnalyze,
    cropActions: CropActions = CropActions(),
    onBack: () -> Unit = {},
    onSettings: () -> Unit = {},
    showModelScores: Boolean = false,
    onAspect: (CropAspect) -> Unit = {},
    onTransform: (PhotoOperation) -> Unit = {},
    onSave: () -> Unit = {},
    saving: Boolean = false
) {
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri -> uri?.let(onPhoto) }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PhotoToolbar(onBack, onSettings)
        Box(Modifier.fillMaxWidth().height(4.dp)) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.photo == null) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Icon(
                        painterResource(R.drawable.ic_photo),
                        null,
                        Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }) {
                        Text("选择照片")
                    }
                }
            } else {
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }) {
                    Text("换一张照片")
                }
            }
            if (state.photo != null) PhotoAspectControls(state, onAspect)
            if (state.photo != null || state.busy) {
                PhotoActionRow(
                    state,
                    PhotoEditActions(
                        onAnalyze,
                        onCancel,
                        rememberPhotoSaveAction(onSave),
                        cropActions.restore,
                        onTransform
                    ),
                    saving
                )
            }
            AnalysisFeedback(state, onRetry)
            state.photo?.let { photo -> PhotoResults(state, photo, cropActions) }
            if (showModelScores && state.originalScore != null) {
                val cropScore = state.displayedScore?.let { String.format(Locale.getDefault(), "%.3f", it) } ?: "~"
                Text(String.format(Locale.getDefault(), "原图 %.3f · 裁剪 %s", state.originalScore, cropScore))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PhotoResults(state: PhotoState, photo: Bitmap, actions: CropActions) {
    val crop = state.displayedCrop
    if (crop == null) {
        PhotoCard("原图", photo)
        return
    }
    Text("原图", style = MaterialTheme.typography.titleSmall)
    CropEditor(photo, crop, actions, state.aspect.normalizedRatio(photo.width, photo.height))
    val preview = remember(photo, crop) { cropPreview(photo, crop) }
    PhotoCard("裁剪预览", preview, smallCrop = crop.area < 0.5f)
}

@Composable
private fun AnalysisFeedback(state: PhotoState, onRetry: () -> Unit) {
    if (state.error != null) {
        Text(state.error, color = MaterialTheme.colorScheme.error)
        Button(onClick = onRetry) { Text("重试") }
    }
}

@Composable
private fun PhotoCard(title: String, bitmap: Bitmap, smallCrop: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (smallCrop) {
                Text(
                    "裁剪范围较小",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        Image(
            bitmap.asImageBitmap(),
            contentDescription = title,
            modifier = Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height)
        )
    }
}

internal fun cropPreview(bitmap: Bitmap, box: CropBox): Bitmap {
    val left = floor(box.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
    val top = floor(box.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
    val right = ceil(box.right * bitmap.width).toInt().coerceIn(left + 1, bitmap.width)
    val bottom = ceil(box.bottom * bitmap.height).toInt().coerceIn(top + 1, bitmap.height)
    return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
}

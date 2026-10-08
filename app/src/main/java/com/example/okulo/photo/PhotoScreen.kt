@file:Suppress("MagicNumber") // Spacing and typography values in the photo screen.

package com.example.okulo.photo

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.okulo.R
import com.example.okulo.composition.AnalysisMode
import com.example.okulo.composition.CropAspect
import com.example.okulo.composition.CropBox
import com.example.okulo.ui.ActionIconButton
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

@Composable
internal fun PhotoScreen(
    state: PhotoState,
    onPhoto: (Uri) -> Unit,
    onMode: (AnalysisMode) -> Unit,
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
        PhotoToolbar(
            onBack = onBack,
            onSettings = onSettings,
            onSave = rememberPhotoSaveAction(onSave),
            canSave = state.displayedCrop != null && !state.busy && !saving,
            onRestore = if (state.result != null && state.manualCrop != null) cropActions.restore else null
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.photo == null) {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_photo),
                        null,
                        Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }) {
                Text(if (state.photo == null) "选择照片" else "换一张照片")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AnalysisMode.entries.forEach { mode ->
                    FilterChip(selected = state.mode == mode, onClick = { onMode(mode) }, label = { Text(mode.title) })
                }
            }
            AnalysisActions(state, onAnalyze, onCancel, onRetry)
            state.photo?.let { photo ->
                PhotoEditingControls(state, onAspect, onTransform)
                PhotoResults(state, photo, cropActions)
            }
            if (showModelScores && state.result != null && state.displayedScore != null) {
                Text(
                    String.format(
                        Locale.getDefault(),
                        "原图 %.3f · 裁剪 %.3f",
                        state.result.originalScore,
                        state.displayedScore
                    )
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PhotoEditingControls(
    state: PhotoState,
    onAspect: (CropAspect) -> Unit,
    onTransform: (PhotoOperation) -> Unit
) {
    if (state.displayedCrop != null) {
        Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp)) {
            CropAspect.entries.forEach { aspect ->
                FilterChip(
                    selected = state.aspect == aspect,
                    onClick = { onAspect(aspect) },
                    enabled = !state.busy,
                    label = { Text(aspect.title) }
                )
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionIconButton(
            R.drawable.ic_rotate_right,
            "向右旋转",
            { onTransform(PhotoOperation.RotateClockwise) },
            enabled = !state.busy
        )
        ActionIconButton(
            R.drawable.ic_flip,
            "水平翻转",
            { onTransform(PhotoOperation.FlipHorizontal) },
            enabled = !state.busy
        )
        ActionIconButton(
            R.drawable.ic_flip,
            "垂直翻转",
            { onTransform(PhotoOperation.FlipVertical) },
            enabled = !state.busy,
            iconModifier = Modifier.rotate(90f)
        )
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
    PhotoCard("裁剪预览", preview)
}

@Composable
private fun AnalysisActions(
    state: PhotoState,
    onAnalyze: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    if (state.busy && state.result == null) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        TextButton(onClick = onCancel) { Text("取消") }
    } else if (state.error != null) {
        Text(state.error, color = MaterialTheme.colorScheme.error)
        Button(onClick = onRetry) { Text("重试") }
    } else if (state.result == null) {
        Button(onClick = onAnalyze, enabled = state.photo != null) { Text("分析构图") }
    }
}

@Composable
private fun PhotoCard(title: String, bitmap: Bitmap) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
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

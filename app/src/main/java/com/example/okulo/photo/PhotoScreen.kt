@file:Suppress("MagicNumber") // Spacing and typography values in the photo screen.

package com.example.okulo.photo

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.okulo.R
import com.example.okulo.composition.CropAspect
import com.example.okulo.composition.CropBox
import com.example.okulo.settings.DEFAULT_CROP_WARNING_PERCENT
import com.example.okulo.ui.FeedbackSnackbar
import com.example.okulo.ui.rememberFeedbackMessage
import kotlinx.coroutines.flow.Flow
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
    saving: Boolean = false,
    saveEvents: Flow<String>? = null,
    cropWarningPercent: Int = DEFAULT_CROP_WARNING_PERCENT
) {
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri -> uri?.let(onPhoto) }
    val feedback = rememberFeedbackMessage(saveEvents)
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize()) {
            PhotoToolbar(onBack, onSettings)
            Box(Modifier.fillMaxWidth().height(4.dp)) {
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Box(Modifier.weight(1f)) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (state.photo == null) {
                        EmptyPhoto { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }
                    }
                    AnalysisFeedback(state, onRetry)
                    state.photo?.let { photo ->
                        PhotoViewPanel(
                            state,
                            photo,
                            cropActions,
                            Modifier.weight(1f),
                            showModelScores,
                            cropWarningPercent
                        )
                    }
                }
                feedback?.let {
                    FeedbackSnackbar(
                        it,
                        Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    )
                }
            }
            if (state.photo != null || state.busy) {
                PhotoBottomTools(
                    state,
                    { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
                    onAspect,
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
        }
    }
}

@Composable
private fun EmptyPhoto(onChoose: () -> Unit) {
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
        FilledTonalButton(onClick = onChoose) { Text("选择照片") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhotoBottomTools(
    state: PhotoState,
    onPick: () -> Unit,
    onAspect: (CropAspect) -> Unit,
    actions: PhotoEditActions,
    saving: Boolean
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (state.photo != null) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(onClick = onPick) { Text("换一张照片") }
                PhotoAspectControls(state, onAspect)
            }
        }
        PhotoActionRow(state, actions, saving)
    }
}

@Composable
private fun AnalysisFeedback(state: PhotoState, onRetry: () -> Unit) {
    if (state.error != null) {
        Text(state.error, color = MaterialTheme.colorScheme.error)
        FilledTonalButton(onClick = onRetry) { Text("重试") }
    }
}

internal fun cropPreview(bitmap: Bitmap, box: CropBox): Bitmap {
    val left = floor(box.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
    val top = floor(box.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
    val right = ceil(box.right * bitmap.width).toInt().coerceIn(left + 1, bitmap.width)
    val bottom = ceil(box.bottom * bitmap.height).toInt().coerceIn(top + 1, bitmap.height)
    return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
}

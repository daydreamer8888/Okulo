package com.example.okulo.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.okulo.R
import com.example.okulo.ui.ActionIconButton

private const val CAMERA_ASPECT_RATIO = 4f / 3f

@Composable
internal fun CameraLayout(
    onImportPhoto: () -> Unit,
    modifier: Modifier = Modifier,
    capture: CaptureUiState = CaptureUiState(),
    onCapture: () -> Unit = {},
    onViewPhoto: () -> Unit = {},
    onMessageDismissed: () -> Unit = {},
    onSettings: () -> Unit = {},
    composition: CameraCompositionState = CameraCompositionState(),
    compositionActions: CameraCompositionActions = CameraCompositionActions(),
    showScores: Boolean = false,
    recommendationEnabled: Boolean = capture.ready,
    recentPhoto: @Composable () -> Unit = { SavedPhotoButton(capture.savedPhoto, onClick = onViewPhoto) },
    viewfinder: @Composable () -> Unit
) {
    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        val ratio = if (maxWidth > maxHeight) CAMERA_ASPECT_RATIO else 1f / CAMERA_ASPECT_RATIO
        val previewWidth = minOf(maxWidth, (maxHeight - 264.dp).coerceAtLeast(0.dp) * ratio)
        val recentPhotoWidth = (maxWidth - 88.dp) / 2
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            CameraToolbar(composition, compositionActions, capture.saving, onImportPhoto, onSettings)
            Box(Modifier.width(previewWidth).aspectRatio(ratio)) { viewfinder() }
            CameraCompositionTools(composition, showScores)
            Column(
                Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp)) {
                    CaptureFeedback(capture.copy(message = composition.error ?: capture.message)) {
                        onMessageDismissed()
                        compositionActions.dismissError()
                    }
                }
                Box(Modifier.fillMaxWidth().height(88.dp)) {
                    Box(
                        Modifier.align(Alignment.CenterStart).width(recentPhotoWidth),
                        contentAlignment = Alignment.Center
                    ) {
                        recentPhoto()
                    }
                    Box(Modifier.align(Alignment.Center)) {
                        CaptureButton(
                            capture.copy(
                                ready = capture.ready && !(composition.frozenPhoto != null && composition.busy)
                            ),
                            onCapture,
                            frozen = composition.frozenPhoto != null
                        )
                    }
                    Box(
                        Modifier.align(Alignment.CenterEnd).width(recentPhotoWidth),
                        contentAlignment = Alignment.Center
                    ) {
                        CameraRecommendButton(composition, recommendationEnabled, compositionActions, capture.saving)
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraToolbar(
    state: CameraCompositionState,
    actions: CameraCompositionActions,
    saving: Boolean,
    onImportPhoto: () -> Unit,
    onSettings: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.active) {
            ActionIconButton(
                if (state.busy) R.drawable.ic_close else R.drawable.ic_arrow_back,
                if (state.busy) "取消推荐" else "返回取景",
                actions.dismiss,
                enabled = !saving
            )
        }
        if (state.frozenPhoto != null) {
            Text("已定格", style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.weight(1f))
        ActionIconButton(R.drawable.ic_photo, "导入照片", onImportPhoto)
        ActionIconButton(R.drawable.ic_settings, "设置", onSettings)
    }
}

@Composable
private fun CaptureButton(capture: CaptureUiState, onCapture: () -> Unit, frozen: Boolean) {
    IconButton(
        onClick = onCapture,
        enabled = capture.canCapture,
        modifier = Modifier.size(88.dp).border(3.dp, Color.White, CircleShape)
            .semantics { contentDescription = if (frozen) "保存" else "拍照" }
    ) {
        if (capture.saving) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp).semantics { contentDescription = "正在保存" },
                color = Color.White,
                strokeWidth = 3.dp
            )
        } else if (frozen) {
            Icon(painterResource(R.drawable.ic_save), null, Modifier.size(32.dp), tint = Color.White)
        } else {
            Box(
                Modifier.size(68.dp).background(
                    if (capture.ready) Color.White else Color.Gray,
                    CircleShape
                )
            )
        }
    }
}

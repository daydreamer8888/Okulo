package com.example.okulo.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    viewfinder: @Composable () -> Unit
) {
    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        val ratio = if (maxWidth > maxHeight) CAMERA_ASPECT_RATIO else 1f / CAMERA_ASPECT_RATIO
        val previewWidth = minOf(maxWidth, (maxHeight - 208.dp).coerceAtLeast(0.dp) * ratio)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    CaptureFeedback(capture.copy(message = composition.error ?: capture.message)) {
                        onMessageDismissed()
                        compositionActions.dismissError()
                    }
                }
                ActionIconButton(R.drawable.ic_photo, "导入照片", onImportPhoto)
                ActionIconButton(R.drawable.ic_settings, "设置", onSettings)
            }
            Box(Modifier.width(previewWidth).aspectRatio(ratio)) { viewfinder() }
            CameraCompositionTools(composition, compositionActions, showScores)
            Column(
                Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SavedPhotoButton(capture.savedPhoto, onViewPhoto)
                    IconButton(
                        onClick = onCapture,
                        enabled = capture.ready && !capture.saving,
                        modifier = Modifier.size(88.dp).border(3.dp, Color.White, CircleShape)
                            .semantics { contentDescription = "拍照" }
                    ) {
                        Box(
                            Modifier.size(68.dp).background(
                                if (capture.ready && !capture.saving) Color.White else Color.Gray,
                                CircleShape
                            )
                        )
                    }
                    CameraRecommendButton(composition, recommendationEnabled, compositionActions.recommend)
                }
            }
        }
    }
}

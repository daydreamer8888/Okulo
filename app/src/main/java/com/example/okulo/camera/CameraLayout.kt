package com.example.okulo.camera

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private const val CAMERA_ASPECT_RATIO = 4f / 3f

@Composable
internal fun CameraLayout(
    onImportPhoto: () -> Unit,
    modifier: Modifier = Modifier,
    viewfinder: @Composable () -> Unit
) {
    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        val ratio = if (maxWidth > maxHeight) CAMERA_ASPECT_RATIO else 1f / CAMERA_ASPECT_RATIO
        val previewWidth = minOf(maxWidth, (maxHeight - 136.dp).coerceAtLeast(0.dp) * ratio)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Okulo", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text("4:3", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
            Box(Modifier.width(previewWidth).aspectRatio(ratio)) { viewfinder() }
            Row(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onImportPhoto,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                ) { Text("导入照片") }
                Text("主摄 · 1×", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

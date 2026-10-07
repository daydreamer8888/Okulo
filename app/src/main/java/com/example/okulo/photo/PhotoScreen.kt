@file:Suppress("MagicNumber") // Spacing and typography values in the photo screen.

package com.example.okulo.photo

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.okulo.composition.AnalysisMode
import com.example.okulo.composition.CropBox
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
    onRetry: () -> Unit = onAnalyze
) {
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri -> uri?.let(onPhoto) }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Okulo", style = MaterialTheme.typography.headlineLarge)
        Text("寻找照片里的好构图", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }) {
            Text(if (state.photo == null) "选择照片" else "换一张照片")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AnalysisMode.entries.forEach { mode ->
                FilterChip(selected = state.mode == mode, onClick = { onMode(mode) }, label = { Text(mode.title) })
            }
        }
        AnalysisActions(state, onAnalyze, onCancel, onRetry)
        state.photo?.let { photo -> PhotoResults(state, photo) }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PhotoResults(state: PhotoState, photo: Bitmap) {
    val crop = state.result?.crop
    if (crop == null) {
        PhotoCard("原图", photo)
        return
    }
    PhotoCard("原图", photo, crop)
    val preview = remember(photo, crop) { cropPreview(photo, crop) }
    PhotoCard("裁剪预览", preview)
    state.result?.let { result ->
        Text("分析用时 ${seconds(result.analysisMillis)} 秒 · 比较了 ${result.candidateCount} 个方案")
        if (result.loadMillis >= 100) Text("模型准备 ${seconds(result.loadMillis)} 秒")
    }
    ScoreDetails(state)
}

@Composable
private fun ScoreDetails(state: PhotoState) {
    var showScores by remember(state.photo) { mutableStateOf(false) }
    TextButton(onClick = { showScores = !showScores }) {
        Text(if (showScores) "收起模型评分" else "查看模型评分")
    }
    if (showScores) {
        val score = state.result?.cropScore
        Text(
            if (score == null) {
                "裁剪评分待更新"
            } else {
                String.format(Locale.getDefault(), "原图 %.3f · 裁剪 %.3f", state.result?.originalScore, score)
            }
        )
    }
}

@Composable
private fun AnalysisActions(state: PhotoState, onAnalyze: () -> Unit, onCancel: () -> Unit, onRetry: () -> Unit) {
    Text(state.status, style = MaterialTheme.typography.bodyMedium)
    if (state.busy) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        TextButton(onClick = onCancel) { Text("取消") }
    } else if (state.error != null) {
        Text(state.error, color = MaterialTheme.colorScheme.error)
        Button(onClick = onRetry) { Text("重试") }
    } else {
        Button(onClick = onAnalyze, enabled = state.photo != null) {
            Text(if (state.result == null) "分析构图" else "重新分析")
        }
    }
}

@Composable
private fun PhotoCard(title: String, bitmap: Bitmap, crop: CropBox? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Card {
            Box(Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height)) {
                Image(bitmap.asImageBitmap(), contentDescription = title, modifier = Modifier.fillMaxSize())
                CropOutline(crop)
            }
        }
    }
}

@Composable
private fun CropOutline(crop: CropBox?) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxSize()) {
        if (crop != null) {
            drawRect(
                color,
                topLeft = Offset(crop.left * size.width, crop.top * size.height),
                size = Size(crop.width * size.width, crop.height * size.height),
                style = Stroke(3.dp.toPx())
            )
        }
    }
}

internal fun cropPreview(bitmap: Bitmap, box: CropBox): Bitmap {
    val left = floor(box.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
    val top = floor(box.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
    val right = ceil(box.right * bitmap.width).toInt().coerceIn(left + 1, bitmap.width)
    val bottom = ceil(box.bottom * bitmap.height).toInt().coerceIn(top + 1, bitmap.height)
    return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
}

private fun seconds(millis: Long): String = String.format(Locale.getDefault(), "%.2f", millis / 1000.0)

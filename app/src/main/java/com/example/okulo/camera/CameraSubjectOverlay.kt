@file:Suppress("MagicNumber") // Normalized selection bounds and preview overlay contrast.

package com.example.okulo.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.okulo.composition.CropBox
import kotlinx.coroutines.delay

@Composable
internal fun CameraSubjectOverlay(state: CameraCompositionState, actions: CameraSubjectActions) {
    var draft by remember(state.selectingSubject) { mutableStateOf<CropBox?>(null) }
    val callbacks by rememberUpdatedState(actions)
    val region = draft ?: state.retainedRegion
    val gestures = if (state.selectingSubject && !state.busy) {
        Modifier.testTag("subject-selection").semantics {
            customActions = listOf(
                CustomAccessibilityAction("框选画面中央") {
                    callbacks.commit(CropBox(0.25f, 0.25f, 0.75f, 0.75f))
                    true
                }
            )
        }.subjectDrag({ draft = it }, { callbacks.commit(it) })
    } else {
        Modifier
    }
    Box(gestures.fillMaxSize()) {
        if (region != null) SubjectOutline(region, state.crop != null && !state.selectingSubject)
        SubjectSelectionHint(state.selectingSubject && !state.busy)
    }
}

private fun Modifier.subjectDrag(onDraft: (CropBox?) -> Unit, onCommit: (CropBox) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val start = down.position
            down.consume()
            var multiplePointers = false
            var draft: CropBox? = null
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.count { it.pressed } > 1) multiplePointers = true
                    val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                    val end = change.position
                    draft = CropBox(
                        (minOf(start.x, end.x) / size.width).coerceIn(0f, 1f),
                        (minOf(start.y, end.y) / size.height).coerceIn(0f, 1f),
                        (maxOf(start.x, end.x) / size.width).coerceIn(0f, 1f),
                        (maxOf(start.y, end.y) / size.height).coerceIn(0f, 1f)
                    )
                    onDraft(draft)
                    event.changes.forEach { it.consume() }
                    if (!change.pressed) break
                }
                if (!multiplePointers) draft?.takeIf { it.width >= 0.04f && it.height >= 0.04f }?.let(onCommit)
            } finally {
                onDraft(null)
            }
        }
    }

@Composable
private fun SubjectOutline(region: CropBox, subdued: Boolean) {
    Canvas(Modifier.fillMaxSize().testTag("subject-region")) {
        val point = Offset(region.left * size.width, region.top * size.height)
        val dimensions = Size(region.width * size.width, region.height * size.height)
        val dash = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 4.dp.toPx()))
        drawRect(Color.Black.copy(alpha = 0.7f), point, dimensions, style = Stroke(4.dp.toPx(), pathEffect = dash))
        drawRect(
            Color.White.copy(alpha = if (subdued) 0.7f else 1f),
            point,
            dimensions,
            style = Stroke(2.dp.toPx(), pathEffect = dash)
        )
    }
}

@Composable
private fun SubjectSelectionHint(selecting: Boolean) {
    var visible by remember { mutableStateOf(false) }
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(selecting) {
        visible = selecting
        if (selecting) {
            delay(accessibility?.calculateRecommendedTimeoutMillis(3000, containsText = true) ?: 3000)
            visible = false
        }
    }
    if (visible) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Text("框选主体", Modifier.padding(16.dp), color = Color.White)
        }
    }
}

package com.example.okulo.camera

import androidx.camera.core.ZoomState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.okulo.R
import com.example.okulo.ui.ActionIconButton
import kotlinx.coroutines.delay
import java.text.NumberFormat
import kotlin.math.abs

@Composable
internal fun CameraZoomControls(
    state: ZoomState?,
    onRatio: (Float) -> Unit,
    onLinear: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (state == null || state.minZoomRatio >= state.maxZoomRatio) return
    var expanded by remember { mutableStateOf(false) }
    val interactions = remember { MutableInteractionSource() }
    val dragged by interactions.collectIsDraggedAsState()
    val focused by interactions.collectIsFocusedAsState()
    val accessibility = LocalAccessibilityManager.current
    val interacting = dragged || focused
    LaunchedEffect(expanded, state.zoomRatio, interacting, enabled) {
        if (expanded && enabled && !interacting) {
            delay(
                accessibility?.calculateRecommendedTimeoutMillis(ZOOM_HIDE_MILLIS, containsControls = true)
                    ?: ZOOM_HIDE_MILLIS
            )
            expanded = false
        }
    }
    val label = zoomLabel(state.zoomRatio)
    Surface(
        modifier.padding(horizontal = 24.dp, vertical = 12.dp).then(
            if (expanded) Modifier.widthIn(max = 320.dp).fillMaxWidth() else Modifier
        ),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        if (expanded) {
            ZoomSliderRow(state, onLinear, enabled, interactions) { expanded = false }
        } else {
            Row(
                Modifier.height(56.dp).padding(horizontal = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = { expanded = true },
                    enabled = enabled,
                    modifier = Modifier.height(48.dp).semantics { contentDescription = "变焦，当前 $label" }
                ) { Text(label) }
                zoomShortcuts(state).forEach { ratio ->
                    TextButton(onClick = { onRatio(ratio) }, enabled = enabled, modifier = Modifier.height(48.dp)) {
                        Text(zoomLabel(ratio))
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomSliderRow(
    state: ZoomState,
    onLinear: (Float) -> Unit,
    enabled: Boolean,
    interactions: MutableInteractionSource,
    onClose: () -> Unit
) {
    val label = zoomLabel(state.zoomRatio)
    Row(
        Modifier.height(56.dp).padding(horizontal = 8.dp).testTag("zoom-panel"),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, Modifier.widthIn(min = 40.dp), style = MaterialTheme.typography.labelLarge)
        Slider(
            value = state.linearZoom,
            onValueChange = onLinear,
            modifier = Modifier.weight(1f).semantics { contentDescription = "变焦倍率" },
            enabled = enabled,
            interactionSource = interactions
        )
        ActionIconButton(R.drawable.ic_expand_more, "收起变焦调节", onClose)
    }
}

private fun zoomShortcuts(state: ZoomState): List<Float> = buildList {
    if (state.minZoomRatio < 1f) add(state.minZoomRatio)
    add(1f)
    add(2f)
}.filter { it in state.minZoomRatio..state.maxZoomRatio && abs(it - state.zoomRatio) > ZOOM_SHORTCUT_TOLERANCE }

private fun zoomLabel(ratio: Float): String = NumberFormat.getNumberInstance().apply {
    maximumFractionDigits = 1
    isGroupingUsed = false
}.format(ratio) + "×"

private const val ZOOM_HIDE_MILLIS = 2_400L
private const val ZOOM_SHORTCUT_TOLERANCE = 0.05f

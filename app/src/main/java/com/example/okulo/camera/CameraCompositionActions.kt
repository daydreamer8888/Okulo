package com.example.okulo.camera

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.okulo.R
import com.example.okulo.ui.ActionIconButton
import com.example.okulo.ui.ActionIconStyle
import com.example.okulo.ui.CompositionScoreCard

internal data class CameraCompositionActions(
    val recommend: () -> Unit = {},
    val restore: () -> Unit = {},
    val dismiss: () -> Unit = {},
    val dismissError: () -> Unit = {}
)

@Composable
internal fun CameraCompositionTools(
    state: CameraCompositionState,
    showScores: Boolean
) {
    Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
        CompositionScoreCard(
            state.originalScore,
            if (state.busy) state.cropScore ?: state.recommendation?.cropScore else state.cropScore,
            showScores && state.crop != null,
            loading = !state.busy && state.scoring
        )
    }
}

@Composable
internal fun CameraRecommendButton(
    state: CameraCompositionState,
    ready: Boolean,
    actions: CameraCompositionActions
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(state.active, state.busy) { if (!state.active || state.busy) expanded = false }
    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        ActionIconButton(
            R.drawable.ic_analyze,
            if (state.busy) "取消推荐" else "推荐构图",
            {
                when {
                    state.busy -> actions.dismiss()
                    state.crop != null -> expanded = true
                    else -> actions.recommend()
                }
            },
            enabled = ready || state.busy,
            iconModifier = if (state.busy) Modifier.alpha(0f) else Modifier,
            style = ActionIconStyle.Tonal
        )
        if (state.busy) {
            CircularProgressIndicator(
                Modifier.size(24.dp).semantics { contentDescription = "正在推荐构图" },
                strokeWidth = 2.dp
            )
        }
        DropdownMenu(expanded, { expanded = false }, modifier = Modifier.width(128.dp)) {
            DropdownMenuItem(
                text = { Text("恢复") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_restore), contentDescription = null) },
                enabled = state.canRestore,
                onClick = {
                    expanded = false
                    actions.restore()
                }
            )
            DropdownMenuItem(
                text = { Text("关闭") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_close), contentDescription = null) },
                onClick = {
                    expanded = false
                    actions.dismiss()
                }
            )
        }
    }
}

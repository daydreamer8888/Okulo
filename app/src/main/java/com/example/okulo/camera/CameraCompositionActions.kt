package com.example.okulo.camera

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
internal fun CameraCompositionTools(state: CameraCompositionState, showScores: Boolean) {
    Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
        if (state.busy) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).align(Alignment.TopCenter)
                    .semantics { contentDescription = "正在推荐构图" }
            )
        }
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
    actions: CameraCompositionActions,
    saving: Boolean = false
) {
    val hasCrop = state.crop != null
    ActionIconButton(
        if (hasCrop) R.drawable.ic_restore else R.drawable.ic_analyze,
        if (hasCrop) "恢复" else "推荐构图",
        if (hasCrop) actions.restore else actions.recommend,
        enabled = !state.busy && !saving && if (hasCrop) state.canRestore else ready,
        style = ActionIconStyle.Tonal
    )
}

package com.example.okulo.camera

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.okulo.R
import com.example.okulo.composition.CropBox
import com.example.okulo.ui.ActionIconButton

internal data class CameraSubjectActions(
    val start: () -> Unit = {},
    val cancel: () -> Unit = {},
    val commit: (CropBox) -> Unit = {},
    val clear: () -> Unit = {}
)

@Composable
internal fun CameraSubjectButton(state: CameraCompositionState, ready: Boolean, actions: CameraSubjectActions) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(state, ready) { expanded = false }
    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        ActionIconButton(
            subjectIcon,
            if (state.selectingSubject) "取消框选" else "框选主体",
            {
                when {
                    state.selectingSubject -> actions.cancel()
                    state.retainedRegion != null -> expanded = true
                    else -> actions.start()
                }
            },
            enabled = state.selectingSubject || (ready && !state.busy && state.crop == null),
            selected = state.retainedRegion != null || state.selectingSubject
        )
        DropdownMenu(expanded, { expanded = false }, modifier = Modifier.width(128.dp)) {
            DropdownMenuItem(
                text = { Text("重新框选") },
                leadingIcon = { Icon(subjectIcon, contentDescription = null) },
                onClick = {
                    expanded = false
                    actions.start()
                }
            )
            DropdownMenuItem(
                text = { Text("清除") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_close), contentDescription = null) },
                onClick = {
                    expanded = false
                    actions.clear()
                }
            )
        }
    }
}

private val subjectIcon = ImageVector.Builder("Subject", 24.dp, 24.dp, SUBJECT_VIEWPORT, SUBJECT_VIEWPORT).addPath(
    pathData = PathParser().parsePathString(
        "M3 3h6v2H5v4H3V3zm12 0h6v6h-2V5h-4V3zM3 15h2v4h4v2H3v-6z" +
            "m16 0h2v6h-6v-2h4v-4zM8 8h8v8H8V8zm2 2v4h4v-4h-4z"
    ).toNodes(),
    fill = SolidColor(Color.Black)
).build()

private const val SUBJECT_VIEWPORT = 24f

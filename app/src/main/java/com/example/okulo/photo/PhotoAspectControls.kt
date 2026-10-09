package com.example.okulo.photo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.okulo.R
import com.example.okulo.composition.CropAspect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhotoAspectControls(state: PhotoState, onAspect: (CropAspect) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    FilledTonalButton(
        onClick = { expanded = true },
        enabled = !state.busy,
        modifier = Modifier.semantics { contentDescription = "裁剪比例" }
    ) {
        Text(state.aspect.title)
        Spacer(Modifier.width(8.dp))
        Icon(painterResource(R.drawable.ic_expand_more), null)
    }
    if (expanded) {
        ModalBottomSheet(onDismissRequest = { expanded = false }) {
            Text("裁剪比例", Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                CropAspect.entries.forEach { aspect ->
                    val selected = state.aspect == aspect
                    ListItem(
                        headlineContent = { Text(aspect.title) },
                        trailingContent = { RadioButton(selected, onClick = null) },
                        modifier = Modifier.selectable(selected, enabled = !state.busy, role = Role.RadioButton) {
                            expanded = false
                            onAspect(aspect)
                        }
                    )
                }
            }
        }
    }
}

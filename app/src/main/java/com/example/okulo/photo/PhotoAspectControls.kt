package com.example.okulo.photo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.okulo.R
import com.example.okulo.composition.CropAspect

@Composable
internal fun PhotoAspectControls(state: PhotoState, onAspect: (CropAspect) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = !state.busy,
            modifier = Modifier.semantics { contentDescription = "裁剪比例" }
        ) {
            Text(state.aspect.title)
            Spacer(Modifier.width(8.dp))
            Icon(painterResource(R.drawable.ic_expand_more), null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CropAspect.entries.forEach { aspect ->
                DropdownMenuItem(
                    text = { Text(aspect.title) },
                    enabled = !state.busy,
                    modifier = Modifier.semantics { selected = state.aspect == aspect },
                    trailingIcon = {
                        if (state.aspect == aspect) Icon(painterResource(R.drawable.ic_check), null)
                    },
                    onClick = {
                        expanded = false
                        onAspect(aspect)
                    }
                )
            }
        }
    }
}

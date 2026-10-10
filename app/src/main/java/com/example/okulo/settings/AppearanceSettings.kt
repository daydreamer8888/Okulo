package com.example.okulo.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import com.example.okulo.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppearanceSettings(theme: AppTheme, onTheme: (AppTheme) -> Unit) {
    var choosingTheme by remember { mutableStateOf(false) }
    Text("外观", Modifier.padding(16.dp), style = MaterialTheme.typography.labelLarge)
    ListItem(
        headlineContent = { Text("主题") },
        supportingContent = { Text(theme.title) },
        trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), null) },
        modifier = Modifier.clickable { choosingTheme = true }
    )
    if (choosingTheme) {
        ModalBottomSheet(onDismissRequest = { choosingTheme = false }) {
            Text("主题", Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                AppTheme.entries.forEach { option ->
                    ListItem(
                        headlineContent = { Text(option.title) },
                        trailingContent = { RadioButton(theme == option, onClick = null) },
                        modifier = Modifier.selectable(theme == option, role = Role.RadioButton) {
                            choosingTheme = false
                            onTheme(option)
                        }
                    )
                }
            }
        }
    }
}

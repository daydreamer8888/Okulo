package com.example.okulo.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.okulo.R
import com.example.okulo.composition.AnalysisMode
import com.example.okulo.ui.ActionIconButton

@Composable
internal fun SettingsScreen(
    showScores: Boolean,
    onScores: (Boolean) -> Unit,
    onBack: () -> Unit,
    analysisMode: AnalysisMode,
    onMode: (AnalysisMode) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActionIconButton(R.drawable.ic_arrow_back, "返回", onBack)
            Text("设置", style = MaterialTheme.typography.titleMedium)
        }
        Text("分析模式", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
        Column(Modifier.selectableGroup()) {
            AnalysisMode.entries.forEach { mode ->
                Row(
                    Modifier.fillMaxWidth().selectable(
                        selected = analysisMode == mode,
                        role = Role.RadioButton,
                        onClick = { onMode(mode) }
                    ).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RadioButton(selected = analysisMode == mode, onClick = null)
                    Text(mode.title)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().toggleable(showScores, role = Role.Switch, onValueChange = onScores)
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("查看模型评分")
            Switch(checked = showScores, onCheckedChange = null)
        }
    }
}

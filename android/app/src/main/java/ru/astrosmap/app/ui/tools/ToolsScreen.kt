package ru.astrosmap.app.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import ru.astrosmap.app.R
import ru.astrosmap.app.data.ChartDao
import ru.astrosmap.app.data.ChartEntity
import ru.astrosmap.app.ui.theme.AppHeader
import ru.astrosmap.app.ui.theme.AstroPanel
import javax.inject.Inject

@HiltViewModel
class ToolsViewModel @Inject constructor(dao: ChartDao) : ViewModel() {
    val charts = dao.search("").stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

/** Раздел «Прогнозы»: карта + все техники. ✦ — по подписке «Премиум». */
@Composable
fun ToolsScreen(
    onTransits: (Long) -> Unit,
    onProgression: (Long) -> Unit,
    onForecast: (Long) -> Unit,
    onSolar: (Long) -> Unit,
    onLunar: (Long) -> Unit,
    onSynastry: (Long, Long) -> Unit,
    viewModel: ToolsViewModel = hiltViewModel(),
) {
    val charts by viewModel.charts.collectAsState()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickPartner by remember { mutableStateOf(false) }
    val selected = charts.firstOrNull { it.id == selectedId } ?: charts.firstOrNull()

    if (pickPartner && selected != null) {
        AlertDialog(
            onDismissRequest = { pickPartner = false },
            title = { Text(stringResource(R.string.syn_pick_partner)) },
            text = {
                Column {
                    charts.filter { it.id != selected.id }.forEach { chart ->
                        TextButton(onClick = {
                            pickPartner = false
                            onSynastry(selected.id, chart.id)
                        }) { Text("${chart.name} · ${chart.day}.${chart.month}.${chart.year}") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pickPartner = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppHeader(stringResource(R.string.section_tools))

        if (charts.isEmpty()) {
            AstroPanel {
                Text(
                    stringResource(R.string.tools_need_saved),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        AstroPanel {
            Text(
                stringResource(R.string.tools_pick),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            charts.forEach { chart ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    RadioButton(
                        selected = chart.id == selected?.id,
                        onClick = { selectedId = chart.id },
                    )
                    Text("${chart.name} · ${chart.day}.${chart.month}.${chart.year}")
                }
            }
        }

        AstroPanel {
            ToolButton(stringResource(R.string.tools_transits)) { selected?.let { onTransits(it.id) } }
            ToolButton(stringResource(R.string.tools_progression)) { selected?.let { onProgression(it.id) } }
            ToolButton(stringResource(R.string.tools_forecast)) { selected?.let { onForecast(it.id) } }
            ToolButton(stringResource(R.string.tools_solar) + " ✦") { selected?.let { onSolar(it.id) } }
            ToolButton(stringResource(R.string.tools_lunar) + " ✦") { selected?.let { onLunar(it.id) } }
            ToolButton(
                stringResource(R.string.tools_synastry) + " ✦",
                enabled = charts.size >= 2,
            ) { pickPartner = true }
            if (charts.size < 2) {
                Text(
                    stringResource(R.string.syn_need_two),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ToolButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(text)
    }
}

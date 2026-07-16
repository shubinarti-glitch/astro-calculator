package ru.astrosmap.app.ui.tools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Row
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
import javax.inject.Inject

@HiltViewModel
class ToolsViewModel @Inject constructor(dao: ChartDao) : ViewModel() {
    val charts = dao.search("").stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

/** Раздел «Прогнозы»: выбор карты и техники (транзиты — офлайн, соляр — премиум). */
@Composable
fun ToolsScreen(
    onTransits: (Long) -> Unit,
    onSolar: (Long) -> Unit,
    viewModel: ToolsViewModel = hiltViewModel(),
) {
    val charts by viewModel.charts.collectAsState()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selected = charts.firstOrNull { it.id == selectedId } ?: charts.firstOrNull()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            stringResource(R.string.tools_pick),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (charts.isEmpty()) {
            Text(
                stringResource(R.string.tools_need_saved),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            return@Column
        }
        LazyColumn(Modifier.weight(1f, fill = false).padding(vertical = 8.dp)) {
            items(charts, key = { it.id }) { chart ->
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
        Button(
            onClick = { selected?.let { onTransits(it.id) } },
            enabled = selected != null,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text(stringResource(R.string.tools_transits)) }
        OutlinedButton(
            onClick = { selected?.let { onSolar(it.id) } },
            enabled = selected != null,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text(stringResource(R.string.tools_solar) + " ✦") }
    }
}

package ru.astrosmap.app.ui.saved

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import ru.astrosmap.app.R
import ru.astrosmap.app.data.ChartDao
import ru.astrosmap.app.data.ChartEntity
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SavedViewModel @Inject constructor(dao: ChartDao) : ViewModel() {
    val query = MutableStateFlow("")
    val charts = query.flatMapLatest { dao.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

/** Список сохранённых карт с локальным поиском по имени и городу. */
@Composable
fun SavedScreen(
    onOpen: (Long) -> Unit,
    viewModel: SavedViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val charts by viewModel.charts.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.query.value = it },
            label = { Text(stringResource(R.string.search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (charts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.saved_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.padding(top = 12.dp)) {
                items(charts, key = ChartEntity::id) { chart ->
                    ChartRow(chart) { onOpen(chart.id) }
                }
            }
        }
    }
}

@Composable
private fun ChartRow(chart: ChartEntity, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(chart.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${chart.day.toString().padStart(2, '0')}.${chart.month.toString().padStart(2, '0')}.${chart.year} " +
                    "${chart.hour.toString().padStart(2, '0')}:${chart.minute.toString().padStart(2, '0')} · ${chart.city}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

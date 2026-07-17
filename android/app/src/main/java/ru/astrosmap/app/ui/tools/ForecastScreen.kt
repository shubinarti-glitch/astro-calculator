package ru.astrosmap.app.ui.tools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.astrosmap.app.R
import ru.astrosmap.app.data.ChartDao
import ru.astrosmap.app.data.api.AstroApi
import ru.astrosmap.app.data.api.DateDto
import ru.astrosmap.app.data.api.ForecastApiRequest
import ru.astrosmap.app.data.api.toNatalRequest
import ru.astrosmap.app.ui.theme.GoodColor
import java.time.LocalDate
import javax.inject.Inject

/** Прогноз на месяц вперёд: профекция, прогрессивная Луна, сферы, события. */
@HiltViewModel
class ForecastViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dao: ChartDao,
    private val api: AstroApi,
) : ViewModel() {

    private val chartId: Long = savedStateHandle.get<String>("id")?.toLongOrNull() ?: 0L
    private val _state = MutableStateFlow<ReportState>(ReportState.Loading)
    val state: StateFlow<ReportState> = _state

    init {
        load()
    }

    fun load() {
        _state.value = ReportState.Loading
        viewModelScope.launch {
            val entity = dao.byId(chartId) ?: return@launch
            val start = LocalDate.now()
            val end = start.plusDays(30)
            _state.value = loadReport {
                api.forecast(
                    ForecastApiRequest(
                        natal = entity.toNatalRequest(),
                        start = DateDto(start.year, start.monthValue, start.dayOfMonth),
                        end = DateDto(end.year, end.monthValue, end.dayOfMonth),
                    ),
                )
            }
        }
    }
}

@Composable
fun ForecastScreen(viewModel: ForecastViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    ReportScaffold(state, onRetry = viewModel::load) { data ->
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Text(
                    stringResource(R.string.tools_forecast) +
                        " · ${data.s("start").orEmpty()} — ${data.s("end").orEmpty()}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp),
                )
            }
            data.s("summary")?.let {
                item { Text(it, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium) }
            }
            for (key in listOf("profection", "progressed_moon")) {
                val text = data.o(key)?.s("text") ?: continue
                item {
                    Text(
                        text,
                        Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item { ToolSection(stringResource(R.string.forecast_spheres)) }
            items(data.a("sphere_forecast")) { s ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(
                        "${s.s("icon").orEmpty()} ${s.s("name").orEmpty()}",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (s.s("tone") == "favorable") GoodColor else MaterialTheme.colorScheme.secondary,
                    )
                    s.s("text")?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            item { ToolSection(stringResource(R.string.forecast_events)) }
            items(data.a("events")) { e ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Row {
                        Text(
                            e.s("date").orEmpty(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "  ${e.s("p1_symbol").orEmpty()} ${e.s("aspect_symbol").orEmpty()} ${e.s("p2_symbol").orEmpty()}" +
                                "  ${e.s("p1_ru").orEmpty()} — ${e.s("p2_ru").orEmpty()}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    e.s("text")?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

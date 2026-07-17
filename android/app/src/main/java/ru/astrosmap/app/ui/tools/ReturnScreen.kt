package ru.astrosmap.app.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import ru.astrosmap.app.data.RemoteChart
import ru.astrosmap.app.data.api.AstroApi
import ru.astrosmap.app.data.api.ReturnApiRequest
import ru.astrosmap.app.data.api.toNatalRequest
import ru.astrosmap.app.ui.AstroLabels
import ru.astrosmap.app.ui.chart.ChartWheel
import java.time.LocalDate
import javax.inject.Inject

/** Соляр (год) или лунар (месяц) — серверный расчёт, рисуем своим колесом. */
@HiltViewModel
class ReturnViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dao: ChartDao,
    private val api: AstroApi,
) : ViewModel() {

    private val chartId: Long = savedStateHandle.get<String>("id")?.toLongOrNull() ?: 0L
    val isLunar: Boolean = savedStateHandle.get<String>("type") == "lunar"

    var year = LocalDate.now().year
        private set
    var month = LocalDate.now().monthValue
        private set

    private val _state = MutableStateFlow<ReportState>(ReportState.Loading)
    val state: StateFlow<ReportState> = _state

    init {
        load()
    }

    fun shift(delta: Int) {
        if (isLunar) {
            val d = LocalDate.of(year, month, 1).plusMonths(delta.toLong())
            year = d.year
            month = d.monthValue
        } else {
            year += delta
        }
        load()
    }

    fun load() {
        _state.value = ReportState.Loading
        viewModelScope.launch {
            val entity = dao.byId(chartId) ?: return@launch
            _state.value = loadReport {
                api.solarReturn(
                    ReturnApiRequest(
                        natal = entity.toNatalRequest(),
                        year = year,
                        month = if (isLunar) month else null,
                        returnType = if (isLunar) "Lunar" else "Solar",
                    ),
                )
            }
        }
    }
}

@Composable
fun ReturnScreen(viewModel: ReturnViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val title = stringResource(if (viewModel.isLunar) R.string.tools_lunar else R.string.tools_solar)

    ReportScaffold(state, onRetry = viewModel::load) { data ->
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { viewModel.shift(-1) }) { Text("‹", style = MaterialTheme.typography.headlineMedium) }
                    Text(
                        title + " · " + (data.s("period_start")?.take(10) ?: periodLabel(viewModel)),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { viewModel.shift(1) }) { Text("›", style = MaterialTheme.typography.headlineMedium) }
                }
            }
            item {
                RemoteChart.parse(data)?.let { chart ->
                    ChartWheel(
                        chart = chart,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(8.dp),
                    )
                }
            }
            val theme = data.s("theme") ?: data.o("theme")?.s("text")
            if (theme != null) {
                item {
                    Text(
                        theme,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            items(data.a("planets")) { p -> RemotePlanetRow(p) }
        }
    }
}

private fun periodLabel(vm: ReturnViewModel): String =
    if (vm.isLunar) "%02d.%d".format(vm.month, vm.year) else vm.year.toString()

/** Строка позиции из серверного отчёта — поля *_ru уже локализованы бэкендом. */
@Composable
fun RemotePlanetRow(p: kotlinx.serialization.json.JsonObject) {
    val name = p.s("name") ?: return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(AstroLabels.pointGlyphs[name] ?: p.s("symbol") ?: "", color = MaterialTheme.colorScheme.primary)
        Text(if (AstroLabels.isRu()) p.s("name_ru") ?: name else AstroLabels.point(name), Modifier.weight(1f))
        if (p.s("retrograde") == "true") {
            Text("R", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
        }
        val sign = p.s("sign") ?: ""
        Text("${AstroLabels.signGlyphs[sign] ?: ""} ${if (AstroLabels.isRu()) p.s("sign_ru") ?: "" else AstroLabels.sign(sign)}")
        Text("${p.i("deg") ?: 0}°${(p.i("min") ?: 0).toString().padStart(2, '0')}′")
        p.i("house_num")?.let {
            Text(stringResource(R.string.house_short, it), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

package ru.astrosmap.app.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import retrofit2.HttpException
import ru.astrosmap.app.R
import ru.astrosmap.app.astro.NatalChart
import ru.astrosmap.app.data.ChartDao
import ru.astrosmap.app.data.RemoteChart
import ru.astrosmap.app.data.api.AstroApi
import ru.astrosmap.app.data.api.NatalRequest
import ru.astrosmap.app.data.api.ReturnApiRequest
import ru.astrosmap.app.ui.AstroLabels
import ru.astrosmap.app.ui.chart.ChartWheel
import ru.astrosmap.app.ui.openSite
import java.time.LocalDate
import javax.inject.Inject

sealed interface SolarState {
    data object Loading : SolarState
    data object NeedPremium : SolarState
    data object NeedAuth : SolarState
    data object Offline : SolarState
    data class Ready(val chart: NatalChart, val theme: String?, val period: String?) : SolarState
}

@HiltViewModel
class SolarViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dao: ChartDao,
    private val api: AstroApi,
) : ViewModel() {

    private val chartId: Long = savedStateHandle.get<String>("id")?.toLongOrNull() ?: 0L
    private val _state = MutableStateFlow<SolarState>(SolarState.Loading)
    val state: StateFlow<SolarState> = _state

    init {
        load(LocalDate.now().year)
    }

    fun load(year: Int) {
        _state.value = SolarState.Loading
        viewModelScope.launch {
            val entity = dao.byId(chartId) ?: return@launch
            try {
                val resp = api.solarReturn(
                    ReturnApiRequest(
                        natal = NatalRequest(
                            name = entity.name, year = entity.year, month = entity.month, day = entity.day,
                            hour = entity.hour, minute = entity.minute, lat = entity.lat, lng = entity.lng,
                            tzStr = entity.tz, city = entity.city,
                            lang = if (AstroLabels.isRu()) "ru" else "en",
                        ),
                        year = year,
                    ),
                )
                val chart = RemoteChart.parse(resp)
                if (chart == null) {
                    _state.value = SolarState.Offline
                    return@launch
                }
                fun str(k: String) = (resp[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
                val theme = (resp["theme"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                    ?: runCatching {
                        (resp["theme"]?.jsonObject?.get("text") as? JsonPrimitive)?.content
                    }.getOrNull()
                _state.value = SolarState.Ready(
                    chart = chart,
                    theme = theme,
                    period = str("period_start")?.take(10),
                )
            } catch (e: HttpException) {
                _state.value = when (e.code()) {
                    402 -> SolarState.NeedPremium
                    401 -> SolarState.NeedAuth
                    else -> SolarState.Offline
                }
            } catch (e: Exception) {
                _state.value = SolarState.Offline
            }
        }
    }
}

/** Соляр — премиум-техника: расчёт на сервере, без подписки предлагает оформить её на сайте. */
@Composable
fun SolarScreen(viewModel: SolarViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    when (val s = state) {
        SolarState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        SolarState.NeedPremium -> CenteredNote(stringResource(R.string.premium_required)) {
            Button(onClick = { openSite(context) }) { Text(stringResource(R.string.premium_buy)) }
        }
        SolarState.NeedAuth -> CenteredNote(stringResource(R.string.solar_need_auth)) {}
        SolarState.Offline -> CenteredNote(stringResource(R.string.net_error)) {
            Button(onClick = { viewModel.load(java.time.LocalDate.now().year) }) {
                Text(stringResource(R.string.retry))
            }
        }
        is SolarState.Ready -> LazyColumn(Modifier.fillMaxSize()) {
            item {
                Text(
                    stringResource(R.string.tools_solar) + (s.period?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp),
                )
            }
            item {
                ChartWheel(
                    chart = s.chart,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(8.dp),
                )
            }
            if (s.theme != null) {
                item {
                    Text(
                        s.theme,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            items(s.chart.points) { p ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(AstroLabels.pointGlyphs[p.name] ?: "", color = MaterialTheme.colorScheme.primary)
                    Text(AstroLabels.point(p.name), Modifier.weight(1f))
                    Text("${AstroLabels.signGlyphs[p.sign]} ${AstroLabels.sign(p.sign)}")
                    Text(AstroLabels.degMin(p.position))
                    Text(
                        stringResource(R.string.house_short, p.houseNum),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredNote(text: String, action: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        action()
    }
}

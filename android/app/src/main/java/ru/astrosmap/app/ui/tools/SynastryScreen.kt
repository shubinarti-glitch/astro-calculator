package ru.astrosmap.app.ui.tools

import androidx.compose.foundation.layout.Column
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
import ru.astrosmap.app.data.api.SynastryApiRequest
import ru.astrosmap.app.data.api.toNatalRequest
import ru.astrosmap.app.ui.theme.GoodColor
import javax.inject.Inject

/** Синастрия двух сохранённых карт — премиум-техника сайта. */
@HiltViewModel
class SynastryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dao: ChartDao,
    private val api: AstroApi,
) : ViewModel() {

    private val idA: Long = savedStateHandle.get<String>("a")?.toLongOrNull() ?: 0L
    private val idB: Long = savedStateHandle.get<String>("b")?.toLongOrNull() ?: 0L

    var title = ""
        private set

    private val _state = MutableStateFlow<ReportState>(ReportState.Loading)
    val state: StateFlow<ReportState> = _state

    init {
        load()
    }

    fun load() {
        _state.value = ReportState.Loading
        viewModelScope.launch {
            val a = dao.byId(idA) ?: return@launch
            val b = dao.byId(idB) ?: return@launch
            title = "${a.name} + ${b.name}"
            _state.value = loadReport {
                api.synastry(SynastryApiRequest(a.toNatalRequest(), b.toNatalRequest()))
            }
        }
    }
}

@Composable
fun SynastryScreen(viewModel: SynastryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    ReportScaffold(state, onRetry = viewModel::load) { data ->
        val couple = data.o("couple")
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Text(
                    stringResource(R.string.tools_synastry) + " · " + viewModel.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp),
                )
            }
            data.o("score")?.let { score ->
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            stringResource(
                                R.string.syn_score,
                                score.i("value") ?: 0,
                                score.s("description_ru").orEmpty(),
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            color = GoodColor,
                        )
                    }
                }
            }
            couple?.s("verdict")?.let {
                item {
                    Text(
                        it,
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item { ToolSection(stringResource(R.string.syn_spheres)) }
            items(couple?.a("spheres").orEmpty()) { s ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(
                        s.s("label") ?: s.s("name").orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    (s.s("text") ?: s.s("advice"))?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            item { ToolSection(stringResource(R.string.syn_strengths)) }
            items(couple?.a("strengths").orEmpty()) { RemoteAspectRow(it) }
            item { ToolSection(stringResource(R.string.syn_challenges)) }
            items(couple?.a("challenges").orEmpty()) { RemoteAspectRow(it) }
            item { ToolSection(stringResource(R.string.aspects)) }
            items(data.a("aspects")) { RemoteAspectRow(it) }
        }
    }
}

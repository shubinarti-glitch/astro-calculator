package ru.astrosmap.app.ui.tools

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import ru.astrosmap.app.ui.theme.AstroPanel
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import ru.astrosmap.app.data.TokenStore
import ru.astrosmap.app.R
import ru.astrosmap.app.data.ChartDao
import ru.astrosmap.app.data.JournalDao
import ru.astrosmap.app.data.ForecastLocationStore
import ru.astrosmap.app.data.api.AstroApi
import ru.astrosmap.app.data.api.DateDto
import ru.astrosmap.app.data.api.ForecastApiRequest
import ru.astrosmap.app.data.api.toNatalRequest
import ru.astrosmap.app.data.access.AccessState
import ru.astrosmap.app.data.access.Entitlement
import ru.astrosmap.app.ui.InterpretationMode
import ru.astrosmap.app.ui.InterpretationModeSelector
import ru.astrosmap.app.ui.saved.SaveMaterialButton
import ru.astrosmap.app.ui.theme.GoodColor
import java.time.LocalDate
import javax.inject.Inject

/** Прогноз на месяц вперёд: профекция, прогрессивная Луна, сферы, события. */
@HiltViewModel
class ForecastViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val dao: ChartDao,
    private val journalDao: JournalDao,
    private val api: AstroApi,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val chartId: Long = savedStateHandle.get<String>("id")?.toLongOrNull() ?: 0L
    private val _state = MutableStateFlow<ReportState>(ReportState.Loading)
    val state: StateFlow<ReportState> = _state
    private val _access = MutableStateFlow(AccessState())
    val access: StateFlow<AccessState> = _access
    private val _period = MutableStateFlow(ForecastPeriod.THIS_MONTH)
    val period: StateFlow<ForecastPeriod> = _period
    private val _history = MutableStateFlow<List<ForecastSnapshot>>(emptyList())
    val history: StateFlow<List<ForecastSnapshot>> = _history
    private val _comparison = MutableStateFlow<ForecastComparison?>(null)
    val comparison: StateFlow<ForecastComparison?> = _comparison
    private val _journalCount = MutableStateFlow(0)
    val journalCount: StateFlow<Int> = _journalCount

    private var loadJob: Job? = null
    private var currentScope = ""
    private val _cachedAt = MutableStateFlow<Long?>(null)
    val cachedAt: StateFlow<Long?> = _cachedAt

    init { load() }

    fun load() {
        loadJob?.cancel()
        val selectedPeriod = _period.value
        _state.value = ReportState.Loading
        _comparison.value = null
        _journalCount.value = 0
        _cachedAt.value = null
        _history.value = emptyList()
        loadJob = viewModelScope.launch {
            val token = tokenStore.get()
            _access.value = verifiedAccess(token)
            val entity = dao.byId(chartId)
            if (entity == null) {
                _state.value = ReportState.MissingChart
                return@launch
            }
            val location = ForecastLocationStore.get(context)
            currentScope = ForecastHistory.scope(token, _access.value.entitlements.map { it.wireName }.toSet()) +
                ":" + entity.toNatalRequest().toString() + ":" + location.toString()
            val range = selectedPeriod.range()
            val snapshots = ForecastHistory.list(context, chartId).filter { it.accessScope == currentScope }
            _history.value = snapshots
            val previous = ForecastHistory.matching(snapshots, selectedPeriod, range, currentScope)
            val loaded = loadReport {
                api.forecast(
                    ForecastApiRequest(
                        natal = entity.toNatalRequest(),
                        start = DateDto(range.first.year, range.first.monthValue, range.first.dayOfMonth),
                        end = DateDto(range.second.year, range.second.monthValue, range.second.dayOfMonth),
                        location = location,
                    ),
                )
            }
            ensureActive()
            // A login/logout during the request must never render another account's response.
            if (tokenStore.get() != token) {
                _access.value = AccessState()
                _state.value = ReportState.NeedAuth
                _history.value = emptyList()
                return@launch
            }
            if (forecastAccessDenied(loaded)) {
                ForecastHistory.revokeAccess(context, token)
                _access.value = AccessState()
                _history.value = emptyList()
                _state.value = loaded
                return@launch
            }
            val cached = if (loaded == ReportState.Offline) previous?.let(ForecastHistory::payload) else null
            _state.value = if (cached != null) ReportState.Ready(cached) else loaded
            if (cached != null) _cachedAt.value = previous?.createdAt
            if (loaded is ReportState.Ready) {
                _comparison.value = previous?.let(ForecastHistory::payload)?.let { ForecastHistory.compare(it, loaded.data) }
                ForecastHistory.save(context, chartId, selectedPeriod, range, loaded.data, currentScope)
                _history.value = ForecastHistory.list(context, chartId).filter { it.accessScope == currentScope }
                if (_access.value.hasEntitlement(Entitlement.JOURNAL_HISTORY)) {
                    val owner = context.getSharedPreferences("journal_owner", Context.MODE_PRIVATE)
                        .getString("last_owner", null) ?: "guest"
                    _journalCount.value = journalDao.since(owner, range.first.toEpochDay())
                        .count { it.epochDay <= range.second.toEpochDay() }
                }
            }
        }
    }

    private suspend fun verifiedAccess(token: String?): AccessState = try {
        val me = api.me()
        val access = me.accessState()
        if (tokenStore.get() != token) {
            _access.value = AccessState()
            _history.value = emptyList()
            _state.value = ReportState.NeedAuth
            throw CancellationException("Account changed")
        }
        ForecastHistory.rememberAccess(context, token, access, me.premiumUntil?.toDoubleOrNull()?.toLong()?.times(1000))
        access
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        if (error is retrofit2.HttpException && error.code() in listOf(401, 403)) {
            ForecastHistory.revokeAccess(context, token)
            AccessState()
        } else ForecastHistory.offlineAccess(context, token) ?: AccessState()
    }

    fun selectPeriod(value: ForecastPeriod) {
        _period.value = value
        load()
    }

    fun openHistory(snapshot: ForecastSnapshot) {
        loadJob?.cancel()
        _state.value = ReportState.Loading
        loadJob = viewModelScope.launch {
            val token = tokenStore.get()
            val verifiedAccess = verifiedAccess(token)
            val entity = dao.byId(chartId)
            if (entity == null) {
                _state.value = ReportState.MissingChart
                return@launch
            }
            val scope = ForecastHistory.scope(token, verifiedAccess.entitlements.map { it.wireName }.toSet()) +
                ":" + entity.toNatalRequest().toString() + ":" + ForecastLocationStore.get(context).toString()
            _access.value = verifiedAccess
            if (snapshot.accessScope != scope || tokenStore.get() != token) {
                _history.value = emptyList()
                _state.value = ReportState.NeedAuth
                return@launch
            }
            ForecastHistory.payload(snapshot)?.let {
                _period.value = runCatching { ForecastPeriod.valueOf(snapshot.period) }.getOrDefault(ForecastPeriod.THIS_MONTH)
                _state.value = ReportState.Ready(it)
                _cachedAt.value = snapshot.createdAt
                _comparison.value = null
                _journalCount.value = 0
            } ?: run { _state.value = ReportState.Offline }
        }
    }

}

@Composable
fun ForecastScreen(viewModel: ForecastViewModel = hiltViewModel(), onSelectChart: (() -> Unit)? = null) {
    val state by viewModel.state.collectAsState()
    val access by viewModel.access.collectAsState()
    val cachedAt by viewModel.cachedAt.collectAsState()
    val period by viewModel.period.collectAsState()
    val history by viewModel.history.collectAsState()
    val comparison by viewModel.comparison.collectAsState()
    val journalCount by viewModel.journalCount.collectAsState()
    var mode by remember { mutableStateOf(InterpretationMode.BRIEF) }

    Column(Modifier.fillMaxSize()) {
        ForecastPeriodSelector(period, viewModel::selectPeriod)
        Box(Modifier.weight(1f)) {
            ReportScaffold(state, onRetry = viewModel::load, onMissingChart = onSelectChart) { data ->
                LazyColumn(Modifier.fillMaxSize()) {
                    cachedAt?.let { timestamp ->
                        item {
                            Text(stringResource(R.string.forecast_cached_at,
                                java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                                    .format(java.util.Date(timestamp))), Modifier.padding(16.dp))
                            TextButton(onClick = viewModel::load) { Text(stringResource(R.string.retry)) }
                        }
                    }
                    item {
                        Text(
                            stringResource(R.string.tools_forecast) +
                                " · ${data.s("start").orEmpty()} — ${data.s("end").orEmpty()}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    item { ForecastPeriodOverview(data) }
                    item {
                        InterpretationModeSelector(
                            selected = mode,
                            onSelect = { mode = it },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    comparison?.let { value ->
                        item {
                            ToolSection(stringResource(R.string.forecast_compare_title))
                            ForecastComparisonCard(value)
                        }
                    }
                    if (access.hasEntitlement(Entitlement.JOURNAL_HISTORY) && journalCount > 0) {
                        item { ForecastAccessNote(R.string.forecast_journal_link, journalCount) }
                    }
                    data.s("summary")?.let {
                        item { Text(it, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium) }
                        item {
                            SaveMaterialButton(
                                sourceType = "forecast",
                                sourceId = "${data.s("start").orEmpty()}_${data.s("end").orEmpty()}",
                                title = stringResource(R.string.tools_forecast),
                                body = it,
                                premium = access.premium,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                    }
                    if (mode == InterpretationMode.DETAILED && !access.hasEntitlement(Entitlement.ADVANCED_FORECASTS)) {
                        item { ForecastAccessNote(R.string.interpretation_free_preview) }
                    }
                    if (mode == InterpretationMode.TECHNICAL && !access.hasEntitlement(Entitlement.PROFESSIONAL_TOOLS)) {
                        item { ForecastAccessNote(R.string.interpretation_professional_preview) }
                    }
                    if (mode == InterpretationMode.DETAILED) for (key in listOf("profection", "progressed_moon")) {
                        val text = data.o(key)?.s("text") ?: continue
                        item {
                            Text(
                                text,
                                Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    if (mode != InterpretationMode.TECHNICAL) item { ToolSection(stringResource(R.string.forecast_spheres)) }
                    val spheres = data.a("sphere_forecast").let {
                        if (mode == InterpretationMode.BRIEF || !access.hasEntitlement(Entitlement.ADVANCED_FORECASTS)) it.take(2) else it
                    }
                    if (mode != InterpretationMode.TECHNICAL) items(spheres) { s ->
                        AstroPanel(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "${s.s("icon").orEmpty()} ${s.s("name").orEmpty()}",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (s.s("tone") == "favorable") GoodColor else MaterialTheme.colorScheme.secondary,
                            )
                            val highlights = s.a("highlights").let {
                                if (mode == InterpretationMode.BRIEF) it.take(1) else it
                            }
                            if (highlights.isEmpty()) {
                                Text(stringResource(R.string.forecast_sphere_no_events), style = MaterialTheme.typography.bodyMedium)
                            } else {
                                Text(stringResource(R.string.forecast_sphere_basis),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                highlights.forEach { event ->
                                    Text(
                                        listOfNotNull(event.s("date"), event.s("p1_ru"),
                                            event.s("aspect_ru") ?: event.s("aspect_symbol"), event.s("p2_ru")).joinToString(" · "),
                                        modifier = Modifier.padding(top = 8.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                    ForecastMeaning(event)
                                }
                            }
                          }
                        }
                    }
                    item { ToolSection(stringResource(R.string.forecast_events)) }
                    val events = data.a("events").let {
                        if (mode == InterpretationMode.BRIEF) it.take(3) else it
                    }
                    items(events) { e ->
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
                            if (mode == InterpretationMode.DETAILED) {
                                ForecastMeaning(e)
                            }
                            if (mode == InterpretationMode.TECHNICAL) {
                                val orbit = forecastOrb(e)
                                orbit?.let { Text(stringResource(R.string.aspect_orb, it), style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                    item { ToolSection(stringResource(R.string.forecast_history_title)) }
                    val visibleHistory = if (access.hasEntitlement(Entitlement.ADVANCED_FORECASTS)) history else history.take(1)
                    if (visibleHistory.isEmpty()) {
                        item { ForecastAccessNote(R.string.forecast_history_empty) }
                    } else items(visibleHistory, key = { it.createdAt }) { snapshot ->
                        FilterChip(
                            selected = false,
                            onClick = { viewModel.openHistory(snapshot) },
                            label = { Text("${snapshot.start} — ${snapshot.end}") },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp),
                        )
                    }
                    if (!access.hasEntitlement(Entitlement.ADVANCED_FORECASTS)) {
                        item { ForecastAccessNote(R.string.forecast_history_premium) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ForecastPeriodSelector(period: ForecastPeriod, onSelect: (ForecastPeriod) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        ForecastPeriod.entries.forEach { item ->
            FilterChip(
                selected = period == item,
                onClick = { onSelect(item) },
                label = { Text(stringResource(periodLabel(item))) },
                modifier = Modifier.padding(horizontal = 3.dp),
            )
        }
    }
}

internal fun forecastAccessDenied(state: ReportState): Boolean =
    state == ReportState.NeedAuth || state == ReportState.NeedPremium

@Composable
private fun ForecastComparisonCard(value: ForecastComparison) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(stringResource(R.string.forecast_compare_added, value.added))
        Text(stringResource(R.string.forecast_compare_ended, value.ended))
        Text(stringResource(R.string.forecast_compare_continued, value.continued))
        val tone = when {
            value.favorableDelta > 0 -> R.string.forecast_compare_better
            value.favorableDelta < 0 -> R.string.forecast_compare_harder
            else -> R.string.forecast_compare_stable
        }
        Text(stringResource(tone), color = MaterialTheme.colorScheme.secondary)
    }
}

private fun periodLabel(period: ForecastPeriod): Int = when (period) {
    ForecastPeriod.TODAY -> R.string.forecast_period_today
    ForecastPeriod.TOMORROW -> R.string.forecast_period_tomorrow
    ForecastPeriod.THIS_WEEK -> R.string.forecast_period_this_week
    ForecastPeriod.NEXT_WEEK -> R.string.forecast_period_next_week
    ForecastPeriod.THIS_MONTH -> R.string.forecast_period_this_month
    ForecastPeriod.NEXT_MONTH -> R.string.forecast_period_next_month
}

@Composable
private fun ForecastAccessNote(textRes: Int, vararg args: Any) {
    Text(
        stringResource(textRes, *args),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}


internal fun forecastOrb(event: kotlinx.serialization.json.JsonObject): String? =
    event.s("orb") ?: event.d("orb")?.toString() ?: event.s("orbit") ?: event.d("orbit")?.toString()

@Composable
internal fun ForecastMeaning(event: kotlinx.serialization.json.JsonObject) {
    val interpretation = event.s("interpretation")?.takeIf { it.isNotBlank() }
    val advice = event.s("advice")?.takeIf { it.isNotBlank() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (interpretation != null) {
            Text(stringResource(R.string.forecast_interpretation_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary)
            Text(interpretation, style = MaterialTheme.typography.bodyLarge)
        } else if (advice == null) {
            Text(event.s("text")?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.forecast_sphere_text_unavailable),
                style = MaterialTheme.typography.bodyLarge)
        }
        advice?.let {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.forecast_advice_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                    Text(it, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
internal fun ForecastPeriodOverview(data: kotlinx.serialization.json.JsonObject) {
    val (favorable, challenging) = forecastOverviewEvents(data)
    if (favorable == null && challenging == null) return
    AstroPanel(Modifier.padding(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.forecast_period_main), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
            listOf(R.string.forecast_opportunity to favorable, R.string.forecast_attention to challenging)
                .forEach { (label, event) ->
                    event?.let {
                        Text(stringResource(label) + " · " + it.s("date").orEmpty(),
                            Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.secondary)
                        val meaning = it.s("interpretation")?.takeIf(String::isNotBlank)
                            ?: it.s("text")?.takeIf(String::isNotBlank)
                        meaning?.let { value -> Text(forecastPreview(value), style = MaterialTheme.typography.bodyLarge) }
                        it.s("advice")?.takeIf(String::isNotBlank)?.let { advice ->
                            Text(stringResource(R.string.forecast_advice_label), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary)
                            Text(forecastPreview(advice), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
        }
    }
}

internal fun forecastOverviewEvents(data: kotlinx.serialization.json.JsonObject): Pair<kotlinx.serialization.json.JsonObject?, kotlinx.serialization.json.JsonObject?> {
    val events = data.a("events")
    return events.firstOrNull { it.s("tone") == "positive" } to events.firstOrNull { it.s("tone") == "negative" }
}

internal fun forecastPreview(text: String): String =
    text.split(Regex("(?<=[.!?])\\s+"), limit = 2).first().trim()

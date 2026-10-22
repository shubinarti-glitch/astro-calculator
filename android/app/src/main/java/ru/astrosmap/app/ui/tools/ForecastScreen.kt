package ru.astrosmap.app.ui.tools

import ru.astrosmap.app.ui.assistant.AssistantPhase
import ru.astrosmap.app.ui.assistant.AssistantUiState
import ru.astrosmap.app.ui.assistant.PublishAssistantState

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
    private val analytics: ru.astrosmap.app.data.Analytics,
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

    private var reportToken: String? = null
    var reportOwner by mutableStateOf<String?>(null)
        private set
    var reportCity by mutableStateOf("")
        private set
    var reportZone by mutableStateOf("UTC")
        private set

    init {
        viewModelScope.launch {
            tokenStore.owners.collect { owner ->
                if (_state.value is ReportState.Ready && owner != reportOwner) {
                    loadJob?.cancel()
                    _state.value = ReportState.NeedAuth
                    _history.value = emptyList()
                    _journalCount.value = 0
                }
            }
        }
        load()
    }

    fun journalAdvice(date: String, advice: String, action: (String, String) -> Unit) {
        val expectedOwner = reportOwner ?: return
        val expectedToken = reportToken
        viewModelScope.launch {
            tokenStore.withCurrentSession(expectedOwner, expectedToken) { action(date, advice) }
        }
    }

    fun retry() {
        analytics.track("forecast_retry")
        load(isRetry = true)
    }

    fun load(isRetry: Boolean = false) {
        loadJob?.cancel()
        val selectedPeriod = _period.value
        _state.value = ReportState.Loading
        _comparison.value = null
        _journalCount.value = 0
        _cachedAt.value = null
        _history.value = emptyList()
        loadJob = viewModelScope.launch {
            val token = tokenStore.get()
            _access.value = verifiedAccess(token) ?: return@launch
            val entity = dao.byId(chartId)
            if (entity == null) {
                _state.value = ReportState.MissingChart
                return@launch
            }
            val location = ForecastLocationStore.get(context)
            val natal = entity.toNatalRequest().copy(lang = forecastLanguage())
            currentScope = ForecastHistory.scope(token, _access.value.entitlements.map { it.wireName }.toSet()) +
                ":" + natal.toString() + ":" + location.toString() + ":lang=" + natal.lang
            val range = selectedPeriod.range(forecastToday(location?.tzStr, entity.tz))
            val snapshots = ForecastHistory.list(context, chartId).filter { it.accessScope == currentScope }
            _history.value = snapshots
            val previous = ForecastHistory.matching(snapshots, selectedPeriod, range, currentScope)
            val baseline = ForecastHistory.comparisonBaseline(snapshots, selectedPeriod, range, currentScope)
            if (dao.owner() != entity.ownerKey || tokenStore.get() != token) {
                _state.value = ReportState.NeedAuth
                return@launch
            }
            val loaded = loadReport {
                ensureActive()
                api.forecast(
                    ForecastApiRequest(
                        natal = natal,
                        start = DateDto(range.first.year, range.first.monthValue, range.first.dayOfMonth),
                        end = DateDto(range.second.year, range.second.monthValue, range.second.dayOfMonth),
                        location = location,
                    ),
                    authorization = token?.let { "Bearer $it" } ?: "",
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
            val cached = if (reportAllowsCachedResult(loaded)) previous?.let(ForecastHistory::payload) else null
            analytics.track(if (isRetry) "forecast_retry_result" else "forecast_result", mapOf(
                "result" to when {
                    loaded is ReportState.Ready -> "loaded"
                    cached != null -> "cached"
                    else -> loaded.javaClass.simpleName
                }, "period" to selectedPeriod.name,
            ))
            if (loaded is ReportState.Ready) analytics.track("forecast_loaded", mapOf("period" to selectedPeriod.name))
            val responseOwner = tokenStore.owner()
            if (!tokenStore.withCurrentSession(responseOwner, token) {
                reportCity = location?.city?.takeIf { it.isNotBlank() } ?: entity.city
                reportZone = forecastZone(location?.tzStr, entity.tz).id
                reportOwner = responseOwner
                reportToken = token
                _state.value = if (cached != null) ReportState.Ready(cached) else loaded
            }) {
                _state.value = ReportState.NeedAuth
                _history.value = emptyList()
                return@launch
            }
            if (cached != null) _cachedAt.value = previous?.createdAt
            if (loaded is ReportState.Ready) {
                _comparison.value = baseline?.let { saved -> ForecastHistory.payload(saved)?.let {
                    ForecastHistory.compare(it, loaded.data).copy(baselineStart = saved.start, baselineEnd = saved.end)
                } }
                ForecastHistory.save(context, chartId, selectedPeriod, range, loaded.data, currentScope)
                _history.value = ForecastHistory.list(context, chartId).filter { it.accessScope == currentScope }
                if (_access.value.hasEntitlement(Entitlement.JOURNAL_HISTORY)) {
                    val owner = tokenStore.owner()
                    if (owner != null && tokenStore.get() == token) {
                        _journalCount.value = journalDao.since(owner, range.first.toEpochDay())
                            .count { it.epochDay <= range.second.toEpochDay() }
                    }
                }
            }
        }
    }

    private suspend fun verifiedAccess(token: String?): AccessState? = if (token == null) AccessState() else try {
        val me = api.me(authorization = "Bearer $token")
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
        if (reportIdentityDenied(reportFailure(error), token != null)) {
            ForecastHistory.revokeAccess(context, token)
            _access.value = AccessState()
            _history.value = emptyList()
            _state.value = reportFailure(error)
            null
        } else if (reportAllowsCachedResult(reportFailure(error))) ForecastHistory.offlineAccess(context, token) ?: AccessState()
        else AccessState()
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
            val verifiedAccess = verifiedAccess(token) ?: return@launch
            val entity = dao.byId(chartId)
            if (entity == null) {
                _state.value = ReportState.MissingChart
                return@launch
            }
            val natal = entity.toNatalRequest().copy(lang = forecastLanguage())
            val scope = ForecastHistory.scope(token, verifiedAccess.entitlements.map { it.wireName }.toSet()) +
                ":" + natal.toString() + ":" + ForecastLocationStore.get(context).toString() + ":lang=" + natal.lang
            _access.value = verifiedAccess
            if (snapshot.accessScope != scope || tokenStore.get() != token) {
                _history.value = emptyList()
                _state.value = ReportState.NeedAuth
                return@launch
            }
            ForecastHistory.payload(snapshot)?.let {
                _period.value = runCatching { ForecastPeriod.valueOf(snapshot.period) }.getOrDefault(ForecastPeriod.THIS_MONTH)
                val snapshotOwner = tokenStore.owner()
                if (!tokenStore.withCurrentSession(snapshotOwner, token) {
                    val location = ForecastLocationStore.get(context)
                    reportCity = location?.city?.takeIf { it.isNotBlank() } ?: entity.city
                    reportZone = forecastZone(location?.tzStr, entity.tz).id
                    reportOwner = snapshotOwner
                    reportToken = token
                    _state.value = ReportState.Ready(it)
                }) return@launch
                _cachedAt.value = snapshot.createdAt
                _comparison.value = null
                _journalCount.value = 0
            } ?: run { _state.value = ReportState.Offline }
        }
    }

}

@Composable
fun ForecastScreen(viewModel: ForecastViewModel = hiltViewModel(), onSelectChart: (() -> Unit)? = null, onJournalAdvice: ((String, String, ru.astrosmap.app.ui.journal.ForecastAdviceSource) -> Unit)? = null, onAssistantState: ((AssistantUiState?) -> Unit)? = null, onOpenJournal: (() -> Unit)? = null, onOpenAccount: (() -> Unit)? = null) {
    val state by viewModel.state.collectAsState()
    val access by viewModel.access.collectAsState()
    val cachedAt by viewModel.cachedAt.collectAsState()
    val period by viewModel.period.collectAsState()
    val history by viewModel.history.collectAsState()
    val comparison by viewModel.comparison.collectAsState()
    val journalCount by viewModel.journalCount.collectAsState()
    var mode by remember { mutableStateOf(InterpretationMode.BRIEF) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scrollScope = androidx.compose.runtime.rememberCoroutineScope()

    val assistantPhase = when (state) {
        ReportState.Loading -> AssistantPhase.LOADING
        ReportState.MissingChart -> AssistantPhase.EMPTY
        is ReportState.Ready -> AssistantPhase.READY
        else -> AssistantPhase.ERROR
    }
    val assistantHint = when (state) {
        ReportState.Loading -> R.string.assistant_forecast_loading_v177
        ReportState.MissingChart -> R.string.assistant_report_missing_v177
        ReportState.NeedAuth, ReportState.Forbidden -> R.string.assistant_report_auth_v177
        ReportState.NeedPremium -> R.string.assistant_report_access_v177
        ReportState.Offline -> R.string.assistant_report_offline_v177
        is ReportState.Ready -> if (cachedAt != null) R.string.assistant_forecast_cached_v177 else R.string.assistant_forecast_ready_v177
        else -> R.string.assistant_report_error_v177
    }
    val assistantAction: (() -> Unit)? = when (state) {
        ReportState.Loading -> null
        ReportState.MissingChart, ReportState.NeedPremium -> onSelectChart
        ReportState.NeedAuth, ReportState.Forbidden -> onOpenAccount
        is ReportState.Ready -> if (cachedAt != null) viewModel::retry else onOpenJournal
        else -> viewModel::retry
    }
    val assistantActionRes = when (state) {
        ReportState.Loading -> null
        ReportState.MissingChart, ReportState.NeedPremium -> R.string.assistant_open_charts_v176
        ReportState.NeedAuth, ReportState.Forbidden -> R.string.assistant_open_account_v177
        is ReportState.Ready -> if (cachedAt != null) R.string.retry else R.string.assistant_open_diary_v176
        else -> R.string.retry
    }
    PublishAssistantState(onAssistantState, assistantPhase, assistantHint,
        assistantActionRes.takeIf { assistantAction != null }, assistantAction, period.ordinal)

    Column(Modifier.fillMaxSize()) {
        ForecastPeriodSelector(period, viewModel::selectPeriod)
        Box(Modifier.weight(1f)) {
            ReportScaffold(state, onRetry = viewModel::retry, onMissingChart = onSelectChart) { data ->
                val visibleSpheres = data.a("sphere_forecast").let {
                    if (mode == InterpretationMode.BRIEF || !access.hasEntitlement(Entitlement.ADVANCED_FORECASTS)) it.take(2) else it
                }
                val visibleEvents = data.a("events").let { if (mode == InterpretationMode.BRIEF) it.take(3) else it }
                // Every optional item before the section headers is counted here; no pixel offsets.
                val beforeSections = 4 + (if (cachedAt != null) 1 else 0) +
                    (if (comparison != null) 1 else 0) +
                    (if (access.hasEntitlement(Entitlement.JOURNAL_HISTORY) && journalCount > 0) 1 else 0) +
                    (if (data.s("summary") != null) 2 else 0) +
                    (if (mode == InterpretationMode.DETAILED && !access.hasEntitlement(Entitlement.ADVANCED_FORECASTS)) 1 else 0) +
                    (if (mode == InterpretationMode.TECHNICAL && !access.hasEntitlement(Entitlement.PROFESSIONAL_TOOLS)) 1 else 0) +
                    (if (mode == InterpretationMode.DETAILED) listOf("profection", "progressed_moon").count { data.o(it)?.s("text") != null } else 0)
                val indices = forecastSectionIndices(beforeSections, visibleSpheres.size, mode != InterpretationMode.TECHNICAL, visibleEvents.size)
                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    cachedAt?.let { timestamp ->
                        item {
                            Text(stringResource(R.string.forecast_cached_at,
                                java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                                    .format(java.util.Date(timestamp))), Modifier.padding(16.dp))
                            TextButton(onClick = viewModel::retry) { Text(stringResource(R.string.retry)) }
                        }
                    }
                    item {
                        Text(
                            stringResource(R.string.tools_forecast) +
                                " · ${forecastDate(data.s("start"))} — ${forecastDate(data.s("end"))}",
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
                    item {
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                            val targets = buildList {
                                if (mode != InterpretationMode.TECHNICAL) add(R.string.forecast_spheres to indices.first)
                                add(R.string.forecast_events to indices.second)
                                add(R.string.forecast_history_title to indices.third)
                            }
                            targets.forEach { (label, target) ->
                                TextButton(onClick = { scrollScope.launch { listState.animateScrollToItem(target) } }) { Text(stringResource(label)) }
                            }
                        }
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
                    if (mode != InterpretationMode.TECHNICAL) items(visibleSpheres) { s ->
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
                                highlights.forEach { event ->
                                    Text(forecastDate(event.s("date")), style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.secondary)
                                    ForecastMeaning(event, onJournalAdvice?.let { action -> { date, advice -> viewModel.journalAdvice(date, advice) { d, a -> action(d, a, forecastAdviceSource(event, data)) } } })
                                    ForecastBasis(event)
                                }
                            }
                          }
                        }
                    }
                    item { ToolSection(stringResource(R.string.forecast_events)) }
                    items(visibleEvents) { e ->
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Text(forecastDate(e.s("date")), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary)
                            if (mode != InterpretationMode.TECHNICAL) {
                                ForecastMeaning(e, onJournalAdvice?.let { action -> { date, advice -> viewModel.journalAdvice(date, advice) { d, a -> action(d, a, forecastAdviceSource(e, data)) } } })
                                ForecastBasis(e)
                                EventReminderButton(e.s("date").orEmpty(), forecastEventTitle(e), viewModel.reportCity,
                                    viewModel.reportZone, viewModel.reportOwner)
                            } else {
                                Text(listOfNotNull(e.s("p1_ru"), e.s("aspect_ru") ?: e.s("aspect_symbol"), e.s("p2_ru")).joinToString(" · "),
                                    style = MaterialTheme.typography.labelLarge)
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
                            label = { Text("${forecastDate(snapshot.start)} — ${forecastDate(snapshot.end)}") },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp),
                        )
                    }
                    if (!access.hasEntitlement(Entitlement.ADVANCED_FORECASTS)) {
                        item { ForecastAccessNote(R.string.forecast_history_premium) }
                    }
                    item {
                        TextButton(onClick = { scrollScope.launch { listState.animateScrollToItem(0) } }, modifier = Modifier.padding(12.dp)) {
                            Text(stringResource(R.string.forecast_top_v176))
                        }
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
    state == ReportState.NeedAuth || state == ReportState.NeedPremium || state == ReportState.Forbidden

@Composable
internal fun ForecastComparisonCard(value: ForecastComparison) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        if (value.baselineStart != null && value.baselineEnd != null) {
            Text(stringResource(R.string.forecast_compare_baseline_v176, forecastDate(value.baselineStart), forecastDate(value.baselineEnd)),
                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        Text(stringResource(R.string.forecast_compare_added, value.added))
        Text(stringResource(R.string.forecast_compare_ended, value.ended))
        Text(stringResource(R.string.forecast_compare_continued, value.continued))
        val tone = when {
            value.favorableDelta > 0 -> R.string.forecast_compare_better
            value.favorableDelta < 0 -> R.string.forecast_compare_harder
            else -> R.string.forecast_compare_stable
        }
        Text(stringResource(tone), color = MaterialTheme.colorScheme.secondary)
        value.themes.forEach { theme ->
            AstroPanel(Modifier.padding(vertical = 6.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(theme.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    theme.previous?.let { Text(stringResource(R.string.forecast_before_v176), style = MaterialTheme.typography.labelLarge); Text(it) }
                    theme.current?.let { Text(stringResource(R.string.forecast_now_v176), style = MaterialTheme.typography.labelLarge); Text(it) }
                    theme.advice?.let { Text(stringResource(R.string.forecast_advice_label), color = MaterialTheme.colorScheme.primary); Text(it) }
                }
            }
        }
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
internal fun ForecastMeaning(event: kotlinx.serialization.json.JsonObject, onJournalAdvice: ((String, String) -> Unit)? = null) {
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
                    if (onJournalAdvice != null && event.s("date")?.let { date -> runCatching { LocalDate.parse(date) }.isSuccess } == true) {
                        TextButton(onClick = { onJournalAdvice(event.s("date")!!, it) }) {
                            Text(stringResource(R.string.forecast_journal_v176))
                        }
                    }
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
                        Text(stringResource(label) + " · " + forecastDate(it.s("date")),
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

@Composable
private fun forecastDate(raw: String?): String {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    return readableForecastDate(raw.orEmpty(), locale)
}

@Composable
internal fun ForecastBasis(event: kotlinx.serialization.json.JsonObject) {
    var expanded by androidx.compose.runtime.saveable.rememberSaveable(event.toString()) { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) {
        Text(stringResource(if (expanded) R.string.forecast_basis_hide_v176 else R.string.forecast_basis_show_v176))
    }
    if (expanded) {
        Text(listOfNotNull(event.s("p1_ru"), event.s("aspect_ru") ?: event.s("aspect_symbol"), event.s("p2_ru")).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        forecastOrb(event)?.let { Text(stringResource(R.string.aspect_orb, it), style = MaterialTheme.typography.bodySmall) }
    }
}

internal fun forecastSectionIndices(beforeSections: Int, sphereCount: Int, showSpheres: Boolean, eventCount: Int): Triple<Int, Int, Int> {
    val events = beforeSections + if (showSpheres) 1 + sphereCount else 0
    return Triple(beforeSections, events, events + 1 + eventCount)
}

internal fun forecastEventTitle(event: kotlinx.serialization.json.JsonObject): String =
    listOfNotNull(event.s("p1_ru") ?: event.s("p1"), event.s("aspect_ru") ?: event.s("aspect_symbol") ?: event.s("aspect"),
        event.s("p2_ru") ?: event.s("p2")).joinToString(" · ")

internal fun forecastAdviceSource(event: kotlinx.serialization.json.JsonObject, report: kotlinx.serialization.json.JsonObject) =
    ru.astrosmap.app.ui.journal.ForecastAdviceSource(forecastEventTitle(event), report.s("start").orEmpty(), report.s("end").orEmpty())

internal fun forecastLanguage(): String = if (ru.astrosmap.app.ui.AstroLabels.isRu()) "ru" else "en"

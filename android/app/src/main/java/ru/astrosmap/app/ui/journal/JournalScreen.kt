package ru.astrosmap.app.ui.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.imePadding
import ru.astrosmap.app.ui.keepEditorAboveIme
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import ru.astrosmap.app.R
import ru.astrosmap.app.data.JournalDao
import ru.astrosmap.app.data.JournalEntry
import ru.astrosmap.app.data.ChartDao
import ru.astrosmap.app.data.PrimaryChart
import ru.astrosmap.app.astro.AstroEngine
import ru.astrosmap.app.astro.BirthInput
import ru.astrosmap.app.data.access.Entitlement
import ru.astrosmap.app.data.access.hasEntitlement
import ru.astrosmap.app.data.api.AstroApi
import ru.astrosmap.app.ui.theme.AppHeader
import ru.astrosmap.app.ui.theme.AstroPanel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.astrosmap.app.ui.today.DayDomain
import ru.astrosmap.app.ui.today.DayInsightCalculator
import javax.inject.Inject

@HiltViewModel
class JournalViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val dao: JournalDao,
    private val chartDao: ChartDao,
    private val engine: AstroEngine,
    private val api: AstroApi,
    private val tokenStore: ru.astrosmap.app.data.TokenStore,
    private val analytics: ru.astrosmap.app.data.Analytics,
) : ViewModel() {
    var selectedDate by mutableStateOf(LocalDate.now())
        private set
    var mood by mutableIntStateOf(3)
    var energy by mutableIntStateOf(3)
    var relationships by mutableIntStateOf(3)
    var work by mutableIntStateOf(3)
    var wellbeing by mutableIntStateOf(3)
    var event by mutableStateOf("")
    var gratitude by mutableStateOf("")
    var note by mutableStateOf("")
    var tags by mutableStateOf("")
    var entries by mutableStateOf<List<JournalEntry>>(emptyList())
        private set
    var fullHistory by mutableStateOf(false)
        private set
    var analysis by mutableStateOf<JournalAnalytics?>(null)
        private set
    var transitMatch by mutableStateOf<TransitJournalMatch?>(null)
        private set
    var analysisLoading by mutableStateOf(false)
        private set
    var saved by mutableStateOf(false)
        private set
    private var ownerKey = "guest"
    var ready by mutableStateOf(false)
        private set
    var accessFailed by mutableStateOf(false)
        private set
    private val pendingAdvice = mutableMapOf<LocalDate, MutableSet<String>>()
    private val drafts = mutableMapOf<LocalDate, JournalEntry>()
    private var dateRequest = 0
    private var legacyOwner: String? = null
    var legacyCount by mutableIntStateOf(0)
        private set

    private fun snapshot() = JournalEntry(ownerKey, selectedDate.toEpochDay(), mood, energy,
        relationships, work, wellbeing, event.trim(), gratitude.trim(), note.trim(), tags.trim())

    private fun retainDraft() { if (ready) drafts[selectedDate] = snapshot() }
    fun accepts(draft: AdviceDraft) = ready && draft.owner == ownerKey

    init {
        analytics.track("journal_opened")
        viewModelScope.launch {
            tokenStore.owner()
            tokenStore.owners.collectLatest { owner ->
                ready = false
                drafts.clear()
                pendingAdvice.clear()
                dateRequest++
                legacyOwner = null
                legacyCount = 0
                accessFailed = false
                apply(null)
                entries = emptyList()
                analysis = null
                transitMatch = null
                val token = tokenStore.get()
                val me = if (token != null) try { api.me() }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { null } else null
                if (tokenStore.get() != token) return@collectLatest
                if (owner == null) {
                    if (token != null && me?.id != null) tokenStore.bindOwner(token, me.id)
                    else accessFailed = true
                    return@collectLatest
                }
                if (token != null && me?.id != null && owner != "user:${me.id}") return@collectLatest
                ownerKey = owner
                if (token != null && me != null) {
                    // A reused username does not prove ownership of old local entries.
                    val old = ownerHash(me.username)
                    val count = dao.all(old).size
                    if (tokenStore.owner() != owner) return@collectLatest
                    legacyOwner = old
                    legacyCount = count
                }
                fullHistory = me?.hasEntitlement(Entitlement.JOURNAL_HISTORY) ?: false
                refresh()
                val entry = dao.byDate(owner, selectedDate.toEpochDay())
                if (tokenStore.owner() == owner) { apply(entry); ready = true }
            }
        }
    }

    fun retryAccess() {
        viewModelScope.launch {
            accessFailed = false
            val token = tokenStore.get() ?: return@launch
            try {
                val me = api.me(authorization = "Bearer $token")
                if (me.id == null || !tokenStore.bindOwner(token, me.id)) accessFailed = true
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { accessFailed = true }
        }
    }

    fun importLegacy() {
        if (!ready) return
        val owner = ownerKey.takeIf { it.startsWith("user:") } ?: return
        val old = legacyOwner ?: return
        viewModelScope.launch {
            if (tokenStore.owner() != owner) return@launch
            dao.copyLegacyOwner(old, owner)
            if (tokenStore.owner() != owner) return@launch
            legacyCount = 0
            refresh()
            load(selectedDate)
        }
    }

    fun load(date: LocalDate) {
        if (!ready) return
        retainDraft()
        val request = ++dateRequest
        val owner = ownerKey
        viewModelScope.launch {
            val entry = drafts[date] ?: dao.byDate(owner, date.toEpochDay())
            if (tokenStore.owner() == owner && request == dateRequest) {
                retainDraft() // Include typing that happened while the Room read was pending.
                selectedDate = date
                apply(entry)
            }
        }
    }

    fun save() {
        if (!ready) return
        val owner = ownerKey
        val entry = snapshot()
        val date = selectedDate
        val imported = pendingAdvice[date].orEmpty().filter { entry.note.contains(it) }.toSet()
        viewModelScope.launch {
            if (tokenStore.owner() != owner) return@launch
            dao.upsert(entry)
            if (imported.isNotEmpty() && pendingAdvice[date]?.removeAll(imported) == true) {
                analytics.track("advice_saved")
            }
            if (tokenStore.owner() == owner) {
                if (selectedDate == date && snapshot().copy(updatedAt = 0) == entry.copy(updatedAt = 0)) saved = true
                if (drafts[date]?.copy(updatedAt = 0) == entry.copy(updatedAt = 0)) drafts.remove(date)
                refresh()
            }
        }
    }

    fun delete() {
        if (!ready) return
        val owner = ownerKey
        val date = selectedDate
        val before = snapshot()
        viewModelScope.launch {
            if (tokenStore.owner() != owner) return@launch
            dao.delete(owner, date.toEpochDay())
            if (tokenStore.owner() == owner) {
                drafts.remove(date)
                if (selectedDate == date && snapshot().copy(updatedAt = 0) == before.copy(updatedAt = 0)) apply(null)
                refresh()
            }
        }
    }

    fun addAdvice(draft: AdviceDraft, consumed: () -> Unit) {
        if (!ready || draft.owner != ownerKey) return
        retainDraft()
        val request = ++dateRequest
        val owner = ownerKey
        viewModelScope.launch {
            val existing = drafts[draft.date] ?: dao.byDate(owner, draft.date.toEpochDay())
            if (tokenStore.owner() != owner || request != dateRequest) return@launch
            // Preserve all open per-date drafts, not only the target day's saved row.
            if (selectedDate != draft.date) { retainDraft(); selectedDate = draft.date; apply(existing) }
            val imported = sourcedAdvice(draft)
            val updated = appendAdvice(note, imported)
            if (updated != note) pendingAdvice.getOrPut(draft.date) { mutableSetOf() }.add(imported)
            note = updated
            saved = false
            consumed()
        }
    }

    private suspend fun refresh() {
        val owner = ownerKey
        val loaded = if (fullHistory) {
            dao.all(owner)
        } else {
            dao.since(owner, LocalDate.now().minusDays(29).toEpochDay())
        }
        if (tokenStore.owner() != owner) return
        entries = loaded
        analysis = if (fullHistory) JournalAnalyticsCalculator.calculate(entries) else null
        if (fullHistory && analysis != null) calculateTransitMatch() else transitMatch = null
    }

    private suspend fun calculateTransitMatch() {
        val owner = ownerKey
        analysisLoading = true
        try {
            val charts = chartDao.allOnce().filter { !it.pendingDelete }
            val primary = PrimaryChart.resolve(context, charts) ?: run {
                transitMatch = null
                return
            }
            val natal = primary.toBirthInput()
            val sample = entries.sortedByDescending { it.epochDay }.take(30)
            val predicted = withContext(Dispatchers.Default) {
                sample.associate { entry ->
                    val date = LocalDate.ofEpochDay(entry.epochDay)
                    val input = BirthInput(date.year, date.monthValue, date.dayOfMonth, 12, 0, natal.lat, natal.lng, natal.tzId)
                    val scores = DayInsightCalculator.calculate(engine.transit(natal, input).aspects)
                        .indicators.associate { it.domain to it.score }
                    entry.epochDay to mapOf(
                        JournalMetric.MOOD to (scores[DayDomain.EMOTIONS] ?: 50),
                        JournalMetric.ENERGY to (scores[DayDomain.ENERGY] ?: 50),
                        JournalMetric.RELATIONSHIPS to (scores[DayDomain.RELATIONSHIPS] ?: 50),
                        JournalMetric.WORK to (scores[DayDomain.PRODUCTIVITY] ?: 50),
                        JournalMetric.WELLBEING to (((scores[DayDomain.ENERGY] ?: 50) + (scores[DayDomain.EMOTIONS] ?: 50)) / 2),
                    )
                }
            }
            if (tokenStore.owner() == owner) transitMatch = JournalAnalyticsCalculator.transitMatch(sample, predicted)
        } finally {
            analysisLoading = false
        }
    }

    private fun apply(entry: JournalEntry?) {
        mood = entry?.mood ?: 3
        energy = entry?.energy ?: 3
        relationships = entry?.relationships ?: 3
        work = entry?.work ?: 3
        wellbeing = entry?.wellbeing ?: 3
        event = entry?.event.orEmpty()
        gratitude = entry?.gratitude.orEmpty()
        note = entry?.note.orEmpty()
        tags = entry?.tags.orEmpty()
        saved = false
    }
}

@Composable
fun JournalScreen(viewModel: JournalViewModel = hiltViewModel(), adviceDraft: AdviceDraft? = null, onAdviceConsumed: () -> Unit = {}) {
    var showLegacyImport by remember { mutableStateOf(false) }
    if (showLegacyImport && viewModel.ready && viewModel.legacyCount > 0) {
        AlertDialog(onDismissRequest = { showLegacyImport = false },
            title = { Text(stringResource(R.string.journal_legacy_title_v176)) },
            text = { Text(stringResource(R.string.journal_legacy_explain_v176)) },
            confirmButton = { TextButton(onClick = { showLegacyImport = false; viewModel.importLegacy() }) {
                Text(stringResource(R.string.journal_legacy_confirm_v176))
            } },
            dismissButton = { TextButton(onClick = { showLegacyImport = false }) { Text(stringResource(R.string.cancel)) } })
    }
    if (!viewModel.ready) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (viewModel.accessFailed) R.string.net_error else R.string.journal_session_loading_v176))
            if (viewModel.accessFailed) Button(onClick = viewModel::retryAccess) { Text(stringResource(R.string.retry)) }
        }
        return
    }
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppHeader(stringResource(R.string.journal_title))
        if (viewModel.legacyCount > 0) {
            OutlinedButton(onClick = { showLegacyImport = true }) { Text(stringResource(R.string.journal_legacy_title_v176)) }
        }
        adviceDraft?.takeIf(viewModel::accepts)?.let { draft ->
            AstroPanel {
                Text(stringResource(R.string.journal_advice_title_v176), style = MaterialTheme.typography.titleMedium)
                Text(formatDate(draft.date))
                Text(draft.advice, style = MaterialTheme.typography.bodyLarge)
                Button(onClick = { viewModel.addAdvice(draft, onAdviceConsumed) }) {
                    Text(stringResource(R.string.journal_advice_add_v176))
                }
            }
        }
        if (!viewModel.fullHistory) {
            Text(
                stringResource(R.string.journal_free_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        AstroPanel {
            Text(formatDate(viewModel.selectedDate), style = MaterialTheme.typography.titleMedium)
            RatingRow(stringResource(R.string.journal_mood), viewModel.mood) { viewModel.mood = it }
            RatingRow(stringResource(R.string.journal_energy), viewModel.energy) { viewModel.energy = it }
            RatingRow(stringResource(R.string.journal_relationships), viewModel.relationships) {
                viewModel.relationships = it
            }
            RatingRow(stringResource(R.string.journal_work), viewModel.work) { viewModel.work = it }
            RatingRow(stringResource(R.string.journal_wellbeing), viewModel.wellbeing) { viewModel.wellbeing = it }
            JournalField(stringResource(R.string.journal_event), viewModel.event) { viewModel.event = it }
            JournalField(stringResource(R.string.journal_gratitude), viewModel.gratitude) { viewModel.gratitude = it }
            JournalField(stringResource(R.string.journal_note), viewModel.note, minLines = 3) { viewModel.note = it }
            JournalField(stringResource(R.string.journal_tags), viewModel.tags) { viewModel.tags = it }
            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (viewModel.saved) R.string.journal_saved else R.string.journal_save))
            }
            if (viewModel.entries.any { it.epochDay == viewModel.selectedDate.toEpochDay() }) {
                OutlinedButton(onClick = viewModel::delete, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.journal_delete))
                }
            }
        }
        if (viewModel.fullHistory) {
            JournalAnalyticsPanel(
                entries = viewModel.entries,
                analysis = viewModel.analysis,
                transitMatch = viewModel.transitMatch,
                loading = viewModel.analysisLoading,
            )
        }
        AstroPanel {
            Text(stringResource(R.string.journal_history), style = MaterialTheme.typography.titleMedium)
            if (viewModel.entries.isEmpty()) {
                Text(stringResource(R.string.journal_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            viewModel.entries.forEach { entry ->
                val date = LocalDate.ofEpochDay(entry.epochDay)
                Column(
                    Modifier.fillMaxWidth().clickable { viewModel.load(date) }.padding(vertical = 8.dp),
                ) {
                    Text(formatDate(date), color = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(R.string.journal_summary, entry.mood, entry.energy, entry.work),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (entry.event.isNotBlank()) Text(entry.event, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun JournalAnalyticsPanel(
    entries: List<JournalEntry>,
    analysis: JournalAnalytics?,
    transitMatch: TransitJournalMatch?,
    loading: Boolean,
) {
    AstroPanel {
        Text(stringResource(R.string.journal_analytics_title), style = MaterialTheme.typography.titleMedium)
        if (analysis == null) {
            Text(
                stringResource(R.string.journal_analytics_need, JournalAnalyticsCalculator.MIN_SAMPLE),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@AstroPanel
        }
        Text(
            stringResource(R.string.journal_analytics_sample, analysis.sampleSize),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        JournalTrendChart(entries.sortedBy { it.epochDay }.takeLast(30))
        analysis.averages.forEach { average ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(metricName(average.metric))
                Text("%.1f / 5".format(average.value), color = MaterialTheme.colorScheme.primary)
            }
        }
        Text(
            stringResource(
                R.string.journal_pattern_summary,
                metricName(analysis.strongest.metric),
                metricName(analysis.sensitive.metric),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        analysis.improving?.let {
            Text(stringResource(R.string.journal_pattern_improving, metricName(it)), color = MaterialTheme.colorScheme.secondary)
        }
        when {
            loading -> Text(stringResource(R.string.journal_transit_loading), style = MaterialTheme.typography.bodySmall)
            transitMatch == null -> Text(stringResource(R.string.journal_transit_no_chart), style = MaterialTheme.typography.bodySmall)
            transitMatch.compared < 5 -> Text(stringResource(R.string.journal_transit_need), style = MaterialTheme.typography.bodySmall)
            else -> Text(
                stringResource(R.string.journal_transit_match, transitMatch.percent, transitMatch.compared),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            stringResource(R.string.journal_transit_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun JournalTrendChart(entries: List<JournalEntry>) {
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
    )
    Canvas(Modifier.fillMaxWidth().height(150.dp).padding(vertical = 8.dp)) {
        if (entries.size < 2) return@Canvas
        listOf(JournalMetric.MOOD, JournalMetric.ENERGY, JournalMetric.WORK).forEachIndexed { index, metric ->
            val path = Path()
            entries.forEachIndexed { pointIndex, entry ->
                val x = pointIndex * size.width / (entries.size - 1)
                val y = size.height - ((entry.value(metric) - 1) / 4f * size.height)
                if (pointIndex == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, colors[index], style = Stroke(width = 4f, cap = StrokeCap.Round))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Text(stringResource(R.string.journal_chart_mood), style = MaterialTheme.typography.labelSmall, color = colors[0])
        Text(stringResource(R.string.journal_chart_energy), style = MaterialTheme.typography.labelSmall, color = colors[1])
        Text(stringResource(R.string.journal_chart_work), style = MaterialTheme.typography.labelSmall, color = colors[2])
    }
}

@Composable
private fun metricName(metric: JournalMetric): String = stringResource(
    when (metric) {
        JournalMetric.MOOD -> R.string.journal_mood
        JournalMetric.ENERGY -> R.string.journal_energy
        JournalMetric.RELATIONSHIPS -> R.string.journal_relationships
        JournalMetric.WORK -> R.string.journal_work
        JournalMetric.WELLBEING -> R.string.journal_wellbeing
    },
)

@Composable
private fun RatingRow(label: String, value: Int, onValue: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..5).forEach { score ->
                FilterChip(
                    selected = value == score,
                    onClick = { onValue(score) },
                    label = { Text(score.toString()) },
                    modifier = Modifier.widthIn(min = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun JournalField(label: String, value: String, minLines: Int = 1, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        minLines = minLines,
        modifier = Modifier.fillMaxWidth().keepEditorAboveIme(),
    )
}

private fun formatDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

private fun ownerHash(username: String): String = MessageDigest.getInstance("SHA-256")
    .digest(username.trim().lowercase().toByteArray())
    .joinToString("") { "%02x".format(it) }

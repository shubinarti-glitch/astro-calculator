package ru.astrosmap.app.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.astrosmap.app.R
import ru.astrosmap.app.astro.AstroEngine
import ru.astrosmap.app.astro.BirthInput
import ru.astrosmap.app.ui.AstroLabels
import ru.astrosmap.app.ui.theme.AppHeader
import ru.astrosmap.app.ui.theme.AstroPanel
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

/** День лунного календаря: фаза и знак Луны на полдень местного времени. */
data class LunarDay(val day: Int, val phaseKey: String, val sign: String)

@HiltViewModel
class LunarCalendarViewModel @Inject constructor(
    private val engine: AstroEngine,
    analytics: ru.astrosmap.app.data.Analytics,
) : ViewModel() {

    var year by mutableIntStateOf(LocalDate.now().year)
        private set
    var month by mutableIntStateOf(LocalDate.now().monthValue)
        private set
    var days by mutableStateOf<List<LunarDay>>(emptyList())
        private set
    var selected by mutableStateOf<LunarDay?>(null)

    init {
        analytics.track("lunar_calendar_opened")
        load()
    }

    fun shift(delta: Int) {
        val d = LocalDate.of(year, month, 1).plusMonths(delta.toLong())
        year = d.year
        month = d.monthValue
        load()
    }

    private fun load() {
        days = emptyList()
        selected = null
        val y = year
        val m = month
        viewModelScope.launch {
            val tz = ZoneId.systemDefault().id
            val computed = withContext(Dispatchers.Default) {
                (1..LocalDate.of(y, m, 1).lengthOfMonth()).map { day ->
                    // Фаза/знак не зависят от места — считаем на полдень, точка 0/0.
                    val chart = engine.natal(BirthInput(y, m, day, 12, 0, 0.0, 0.0, tz))
                    val moon = chart.points.first { it.name == "Moon" }
                    LunarDay(day, chart.lunarPhase.name, moon.sign)
                }
            }
            days = computed
            val today = LocalDate.now()
            if (today.year == y && today.monthValue == m) {
                selected = computed.getOrNull(today.dayOfMonth - 1)
            }
        }
    }
}

@Composable
fun LunarCalendarScreen(viewModel: LunarCalendarViewModel = hiltViewModel()) {
    val locale = if (AstroLabels.isRu()) Locale("ru") else Locale.ENGLISH
    val monthTitle = java.time.Month.of(viewModel.month)
        .getDisplayName(TextStyle.FULL_STANDALONE, locale)
        .replaceFirstChar { it.uppercase(locale) } + " " + viewModel.year

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppHeader(stringResource(R.string.tools_luncal))

        AstroPanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.shift(-1) }) { Text("‹", style = MaterialTheme.typography.headlineMedium) }
                Text(
                    monthTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { viewModel.shift(1) }) { Text("›", style = MaterialTheme.typography.headlineMedium) }
            }

            // Шапка дней недели (Пн–Вс).
            Row(Modifier.fillMaxWidth()) {
                java.time.DayOfWeek.entries.forEach { dow ->
                    Text(
                        dow.getDisplayName(TextStyle.SHORT_STANDALONE, locale),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            val offset = LocalDate.of(viewModel.year, viewModel.month, 1).dayOfWeek.value - 1
            val cells: List<LunarDay?> = List(offset) { null } + viewModel.days
            val today = LocalDate.now()
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { d -> DayCell(d, viewModel, today, Modifier.weight(1f)) }
                    repeat(7 - week.size) { Box(Modifier.weight(1f)) }
                }
            }

            Text(
                stringResource(R.string.luncal_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        viewModel.selected?.let { d ->
            AstroPanel {
                Text(
                    "%02d.%02d.%d".format(d.day, viewModel.month, viewModel.year),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "${LunarTexts.phaseEmoji[d.phaseKey] ?: ""} ${LunarTexts.phaseName(d.phaseKey)} · " +
                        "${AstroLabels.signGlyphs[d.sign] ?: ""} ${AstroLabels.sign(d.sign)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(LunarTexts.moonMood(d.sign), style = MaterialTheme.typography.bodyMedium)
                Text(
                    LunarTexts.phaseAdvice(d.phaseKey),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DayCell(d: LunarDay?, viewModel: LunarCalendarViewModel, today: LocalDate, modifier: Modifier) {
    if (d == null) {
        Box(modifier)
        return
    }
    val isToday = today.year == viewModel.year && today.monthValue == viewModel.month && today.dayOfMonth == d.day
    val isSelected = viewModel.selected == d
    Column(
        modifier
            .padding(1.dp)
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                    else -> androidx.compose.ui.graphics.Color.Transparent
                },
                RoundedCornerShape(8.dp),
            )
            .clickable { viewModel.selected = d }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            d.day.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(LunarTexts.phaseEmoji[d.phaseKey] ?: "", style = MaterialTheme.typography.labelSmall)
        Text(
            AstroLabels.signGlyphs[d.sign] ?: "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

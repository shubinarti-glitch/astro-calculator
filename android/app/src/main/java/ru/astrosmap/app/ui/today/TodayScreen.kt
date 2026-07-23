package ru.astrosmap.app.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import ru.astrosmap.app.R
import ru.astrosmap.app.astro.AspectHit
import ru.astrosmap.app.astro.AstroEngine
import ru.astrosmap.app.astro.BirthInput
import ru.astrosmap.app.data.Analytics
import ru.astrosmap.app.data.ChartDao
import ru.astrosmap.app.data.ChartEntity
import ru.astrosmap.app.data.ChartTexts
import ru.astrosmap.app.data.PrimaryChart
import ru.astrosmap.app.data.api.AstroApi
import ru.astrosmap.app.data.api.NatalRequest
import ru.astrosmap.app.data.api.TransitApiRequest
import ru.astrosmap.app.data.api.TransitDateDto
import ru.astrosmap.app.ui.AstroLabels
import ru.astrosmap.app.ui.ChartPicker
import ru.astrosmap.app.ui.theme.AppHeader
import ru.astrosmap.app.ui.theme.AstroPanel
import ru.astrosmap.app.ui.tools.LunarTexts
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

/** Одна строка «главного за день»: аспект + трактовка (если пришла с сервера). */
data class DayAspect(val hit: AspectHit, val interp: String?)

data class TodayState(
    val loading: Boolean = true,
    val chartName: String? = null,     // null — нет сохранённых карт
    val moonPhaseKey: String = "",
    val moonSign: String = "",
    val aspects: List<DayAspect> = emptyList(),
    val textsOffline: Boolean = false,
    /** Все карты — для выбора «кто я»; переключатель показываем только когда их больше одной. */
    val charts: List<ChartEntity> = emptyList(),
    val chartId: Long = 0,
)

/**
 * Экран «Сегодня» — стартовый. Отвечает на вопрос «что у меня сегодня», а не «какая у меня карта».
 *
 * Расчёт полностью офлайн (astrocore): фаза и знак Луны + главные транзиты к натальной карте.
 * Авторские трактовки подгружаются с сервера при сети — в APK их не зашиваем.
 */
@HiltViewModel
class TodayViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val dao: ChartDao,
    private val engine: AstroEngine,
    private val api: AstroApi,
    private val analytics: Analytics,
) : ViewModel() {

    private val _state = MutableStateFlow(TodayState())
    val state: StateFlow<TodayState> = _state

    init {
        analytics.track("today_viewed")
        load()
    }

    /** Сменить карту «это я» — выбор запоминается между запусками. */
    fun selectChart(id: Long) {
        PrimaryChart.set(context, id)
        load()
    }

    fun load() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val all = runCatching { dao.allOnce() }.getOrDefault(emptyList())
                .filter { !it.pendingDelete }
            // Карта «это я»: выбранная пользователем, иначе самая ранняя.
            val chart = PrimaryChart.resolve(context, all)
            if (chart == null) {
                // Без карты показываем только лунную часть — она считается без данных рождения.
                val moon = withContext(Dispatchers.Default) { moonOnly(today) }
                _state.value = TodayState(
                    loading = false,
                    chartName = null,
                    moonPhaseKey = moon.first,
                    moonSign = moon.second,
                )
                return@launch
            }
            val natal = chart.toBirthInput()
            val transitInput = BirthInput(
                year = today.year, month = today.monthValue, day = today.dayOfMonth,
                hour = 12, minute = 0, lat = natal.lat, lng = natal.lng, tzId = natal.tzId,
            )
            val tc = withContext(Dispatchers.Default) { engine.transit(natal, transitInput) }
            val moonSign = tc.transitPoints.firstOrNull { it.name == "Moon" }?.sign ?: ""
            val top = tc.aspects.sortedByDescending { strength(it) }.take(3).map { DayAspect(it, null) }
            _state.value = TodayState(
                loading = false,
                chartName = chart.name,
                moonPhaseKey = tc.lunarPhase.name,
                moonSign = moonSign,
                aspects = top,
                charts = all,
                chartId = chart.id,
            )
            loadTexts(chart.name, natal, chart.city, today)
        }
    }

    /** Фаза и знак Луны без данных рождения — точка 0/0, полдень. */
    private fun moonOnly(date: LocalDate): Pair<String, String> {
        val input = BirthInput(date.year, date.monthValue, date.dayOfMonth, 12, 0, 0.0, 0.0, java.time.ZoneId.systemDefault().id)
        val chart = engine.natal(input)
        val moon = chart.points.first { it.name == "Moon" }
        return chart.lunarPhase.name to moon.sign
    }

    private suspend fun loadTexts(name: String, natal: BirthInput, city: String, date: LocalDate) {
        try {
            val resp = api.transit(
                TransitApiRequest(
                    natal = NatalRequest(
                        name = name, year = natal.year, month = natal.month, day = natal.day,
                        hour = natal.hour, minute = natal.minute, lat = natal.lat, lng = natal.lng,
                        tzStr = natal.tzId, city = city,
                        lang = if (AstroLabels.isRu()) "ru" else "en",
                    ),
                    transitDate = TransitDateDto(date.year, date.monthValue, date.dayOfMonth),
                ),
            )
            val texts = resp["aspects"]?.jsonArray.orEmpty().mapNotNull { a ->
                val o = a.jsonObject
                fun str(k: String) = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
                val interp = str("interp") ?: return@mapNotNull null
                ChartTexts.aspectKey(
                    str("p1") ?: return@mapNotNull null,
                    str("aspect") ?: return@mapNotNull null,
                    str("p2") ?: return@mapNotNull null,
                ) to interp
            }.toMap()
            _state.value = _state.value.copy(
                aspects = _state.value.aspects.map {
                    it.copy(interp = texts[ChartTexts.aspectKey(it.hit.p1, it.hit.aspect, it.hit.p2)])
                },
                textsOffline = false,
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(textsOffline = true)
        }
    }

    private companion object {
        val PLANET_W = mapOf(
            "Sun" to 1.0, "Moon" to 0.6, "Mercury" to 0.8, "Venus" to 0.8, "Mars" to 0.9,
            "Jupiter" to 1.0, "Saturn" to 1.0, "Uranus" to 0.7, "Neptune" to 0.7, "Pluto" to 0.7,
        )
        val ASPECT_W = mapOf(
            "conjunction" to 1.0, "opposition" to 1.0, "square" to 0.95,
            "trine" to 0.9, "sextile" to 0.7, "quintile" to 0.4,
        )

        /** Сила аспекта дня: важность планеты × тип аспекта × точность × сходящийся/расходящийся. */
        fun strength(a: AspectHit): Double {
            val planet = PLANET_W[a.p2] ?: 0.5
            val aspect = ASPECT_W[a.aspect] ?: 0.5
            val tightness = (1.0 - (abs(a.orbit) / 10.0)).coerceIn(0.0, 1.0)
            val movement = if (a.movement == "Applying") 1.15 else 0.9
            return planet * aspect * tightness * movement
        }
    }
}

@Composable
fun TodayScreen(
    onCreateChart: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val locale = if (AstroLabels.isRu()) Locale("ru") else Locale.ENGLISH
    val today = LocalDate.now()
    val dateLine = "%d %s, %s".format(
        today.dayOfMonth,
        today.month.getDisplayName(TextStyle.FULL, locale),
        today.dayOfWeek.getDisplayName(TextStyle.FULL, locale),
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppHeader(stringResource(R.string.section_today))

        AstroPanel {
            Text(
                dateLine.replaceFirstChar { it.uppercase(locale) },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (state.moonPhaseKey.isNotBlank()) {
                Text(
                    "${LunarTexts.phaseEmoji[state.moonPhaseKey] ?: ""} ${LunarTexts.phaseName(state.moonPhaseKey)} · " +
                        "${AstroLabels.signGlyphs[state.moonSign] ?: ""} ${AstroLabels.sign(state.moonSign)}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(LunarTexts.moonMood(state.moonSign), style = MaterialTheme.typography.bodyMedium)
                Text(
                    LunarTexts.phaseAdvice(state.moonPhaseKey),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Карта дня — не зависит от натальной карты, показываем всегда.
        ru.astrosmap.app.ui.tarot.CardOfDaySection()

        if (state.chartName == null) {
            if (!state.loading) {
                AstroPanel {
                    Text(
                        stringResource(R.string.today_no_chart),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onCreateChart, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.today_make_chart))
                    }
                }
            }
            return@Column
        }

        AstroPanel {
            Text(
                stringResource(R.string.today_main),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            // Больше одной карты — даём явно выбрать, по кому считать день.
            if (state.charts.size > 1) {
                ChartPicker(
                    charts = state.charts,
                    selectedId = state.chartId,
                    onSelect = viewModel::selectChart,
                )
            } else {
                Text(
                    stringResource(R.string.today_for, state.chartName.orEmpty()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.aspects.isEmpty()) {
                Text(stringResource(R.string.today_quiet), style = MaterialTheme.typography.bodyMedium)
            }
            state.aspects.forEach { da ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(AstroLabels.pointGlyphs[da.hit.p2] ?: "", color = MaterialTheme.colorScheme.primary)
                        Text(AstroLabels.point(da.hit.p2), style = MaterialTheme.typography.titleSmall)
                        Text(AstroLabels.aspectGlyphs[da.hit.aspect] ?: "", color = MaterialTheme.colorScheme.secondary)
                        Text(AstroLabels.point(da.hit.p1), style = MaterialTheme.typography.titleSmall)
                    }
                    da.interp?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            if (state.textsOffline) {
                Text(
                    stringResource(R.string.today_texts_offline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

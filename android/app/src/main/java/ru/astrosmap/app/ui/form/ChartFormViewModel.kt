package ru.astrosmap.app.ui.form

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.astrosmap.app.R
import ru.astrosmap.app.data.ChartEntity
import ru.astrosmap.app.data.CityStore
import ru.astrosmap.app.ui.AstroLabels
import java.time.LocalDate
import javax.inject.Inject

/** Черновик, передаваемый с формы на экран просмотра (и обратно при редактировании). */
object ChartDraftHolder {
    var pending: ChartEntity? = null
    var editRequest: ChartEntity? = null
}

@HiltViewModel
class ChartFormViewModel @Inject constructor(
    private val cities: CityStore,
    private val tokens: ru.astrosmap.app.data.TokenStore,
    private val analytics: ru.astrosmap.app.data.Analytics,
) : ViewModel() {

    var name by mutableStateOf("")
    var day by mutableStateOf("")
    var month by mutableStateOf("")
    var year by mutableStateOf("")
    var hour by mutableStateOf("12")
    var minute by mutableStateOf("0")
    var cityQuery by mutableStateOf("")
    var suggestions by mutableStateOf<List<CityStore.City>>(emptyList())
    var selectedCity by mutableStateOf<CityStore.City?>(null)
    var errorRes by mutableStateOf<Int?>(null)

    private var citySearch: kotlinx.coroutines.Job? = null
    private var cityRequest = 0
    private fun cancelCitySearch() { citySearch?.cancel(); citySearch = null; cityRequest++ }

    private var editingId = 0L
    private var serverId: Long? = null
    private var editingOwner: String? = null
    private var clientId = java.util.UUID.randomUUID().toString()

    init {
        maybeConsumeEdit()
        viewModelScope.launch {
            var previous = tokens.owner()
            tokens.owners.collect { owner ->
                if (owner != previous) {
                    previous = owner
                    cancelCitySearch()
                    name = ""; day = ""; month = ""; year = ""; hour = "12"; minute = "0"
                    cityQuery = ""; selectedCity = null; suggestions = emptyList()
                    editingId = 0; serverId = null; editingOwner = null
                    clientId = java.util.UUID.randomUUID().toString()
                    ChartDraftHolder.pending = null; ChartDraftHolder.editRequest = null
                }
            }
        }
    }

    /** Подхватывает запрос «Редактировать». Зовётся и из init, и при каждом показе формы —
     *  ViewModel вкладки живёт в backstack, init не перезапускается. */
    fun maybeConsumeEdit() {
        ChartDraftHolder.editRequest?.let { e ->
            ChartDraftHolder.editRequest = null
            cancelCitySearch()
            suggestions = emptyList()
            if (tokens.owners.value != e.ownerKey) return
            editingId = e.id
            serverId = e.serverId
            editingOwner = e.ownerKey
            clientId = e.clientId
            name = e.name
            day = e.day.toString(); month = e.month.toString(); year = e.year.toString()
            hour = e.hour.toString(); minute = e.minute.toString()
            cityQuery = e.city
            selectedCity = CityStore.City(e.city, e.city, "", e.lat, e.lng, e.tz)
            errorRes = null
        }
    }

    fun onCityQuery(q: String) {
        cancelCitySearch()
        cityQuery = q
        selectedCity = null
        suggestions = emptyList()
        val owner = tokens.owners.value ?: return
        if (q.isBlank()) return
        val request = cityRequest
        citySearch = viewModelScope.launch {
            kotlinx.coroutines.delay(200)
            val result = withContext(Dispatchers.IO) { cities.search(q) }
            if (cityRequest == request && cityQuery == q && selectedCity == null && tokens.owners.value == owner) {
                suggestions = result
            }
        }
    }

    fun pickCity(city: CityStore.City) {
        cancelCitySearch()
        selectedCity = city
        cityQuery = city.label(AstroLabels.isRu())
        suggestions = emptyList()
    }

    /** Переход к оплате на сайте — начало платной воронки. */
    fun trackPremiumTap() = analytics.track("premium_tapped", mapOf("from" to "main"))

    /** Проверяет ввод и кладёт черновик; true — можно открывать экран карты. */
    fun calculate(): Boolean {
        errorRes = null
        val owner = tokens.owners.value ?: run { errorRes = R.string.net_error; return false }
        if (editingOwner != null && editingOwner != owner) { errorRes = R.string.net_error; return false }
        val city = selectedCity ?: run { errorRes = R.string.form_pick_city; return false }
        val d = day.toIntOrNull(); val mo = month.toIntOrNull(); val y = year.toIntOrNull()
        val h = hour.toIntOrNull(); val mi = minute.toIntOrNull()
        val valid = d != null && mo != null && y != null && h in 0..23 && mi in 0..59 &&
            y in 1..3000 && runCatching { LocalDate.of(y!!, mo!!, d!!) }.isSuccess
        if (!valid) { errorRes = R.string.form_bad_date; return false }

        ChartDraftHolder.pending = ChartEntity(
            id = editingId, ownerKey = owner, clientId = clientId,
            serverId = serverId,
            name = name.ifBlank { if (AstroLabels.isRu()) "Без имени" else "Untitled" },
            year = y!!, month = mo!!, day = d!!, hour = h!!, minute = mi!!,
            lat = city.lat, lng = city.lng, tz = city.tz,
            city = cityQuery,
        )
        analytics.track("chart_requested", mapOf("edit" to (editingId != 0L).toString()))
        return true
    }
}

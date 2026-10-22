package ru.astrosmap.app.ui.view

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import ru.astrosmap.app.astro.AstroEngine
import ru.astrosmap.app.astro.NatalChart
import ru.astrosmap.app.data.ChartDao
import ru.astrosmap.app.data.ChartEntity
import ru.astrosmap.app.data.ChartTexts
import ru.astrosmap.app.data.SyncManager
import ru.astrosmap.app.data.api.AstroApi
import ru.astrosmap.app.data.api.NatalRequest
import ru.astrosmap.app.data.access.AccessState
import ru.astrosmap.app.ui.AstroLabels
import ru.astrosmap.app.ui.form.ChartDraftHolder
import javax.inject.Inject

data class ChartViewState(
    val calculationFailed: Boolean = false,
    val entity: ChartEntity? = null,
    val chart: NatalChart? = null,
    val savedId: Long? = null,      // null — черновик ещё не сохранён
    val texts: ChartTexts? = null,
    val textsOffline: Boolean = false, // трактовки не загрузились (нет сети)
    val access: AccessState = AccessState(),
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
)

@HiltViewModel
class ChartViewViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val dao: ChartDao,
    private val tokens: ru.astrosmap.app.data.TokenStore,
    private val engine: AstroEngine,
    private val api: AstroApi,
    private val syncManager: SyncManager,
    private val analytics: ru.astrosmap.app.data.Analytics,
) : ViewModel() {

    private val _state = MutableStateFlow(ChartViewState())
    val state: StateFlow<ChartViewState> = _state

    /** Сырой JSON трактовок — сохраняется вместе с картой. */
    private var textsRaw: String? = null
    private val saveMutex = Mutex()
    private val lang = if (AstroLabels.isRu()) "ru" else "en"

    private var calculationJob: kotlinx.coroutines.Job? = null
    init { retryCalculation() }

    fun retryCalculation() {
        if (calculationJob?.isActive == true) return
        _state.value = ChartViewState()
        val id = savedStateHandle.get<String>("id")?.toLongOrNull() ?: 0L
        calculationJob = viewModelScope.launch {
            val owner = dao.owner() ?: run { _state.value = ChartViewState(calculationFailed = true); return@launch }
            val token = tokens.get()
            if (!tokens.withCurrentSession(owner, token) {}) return@launch
            val entity = if (id > 0) dao.byId(id) else ChartDraftHolder.pending?.takeIf { it.ownerKey == owner }
            if (entity == null) {
                tokens.withCurrentSession(owner, token) { _state.value = ChartViewState(calculationFailed = true) }
                return@launch
            }
            if (!tokens.withCurrentSession(owner, token) {}) return@launch
            val chart = try {
                withContext(Dispatchers.Default) { engine.natal(entity.toBirthInput()) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                tokens.withCurrentSession(owner, token) {
                    analytics.track("chart_failed", mapOf("edit" to (entity.id != 0L).toString()))
                    _state.value = ChartViewState(calculationFailed = true)
                }
                return@launch
            }
            if (!tokens.withCurrentSession(owner, token) {
                // An edited draft keeps its row ID, but its changes still need saving.
                _state.value = ChartViewState(entity = entity, chart = chart, savedId = entity.id.takeIf { id > 0 && it > 0 })
                if (id == 0L && savedStateHandle.get<Boolean>("calculation_recorded") != true) {
                    analytics.track("chart_created", mapOf("edit" to (entity.id != 0L).toString()))
                    savedStateHandle["calculation_recorded"] = true
                }
            }) return@launch
            val access = if (token == null) AccessState() else try {
                if (!tokens.withCurrentSession(owner, token) {}) return@launch
                api.me(authorization = "Bearer $token").accessState()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { AccessState() }
            if (!tokens.withCurrentSession(owner, token) { _state.value = _state.value.copy(access = access) }) return@launch
            loadTexts(entity, owner, token)
            if (!tokens.withCurrentSession(owner, token) {}) return@launch
            // Без трения: самую первую построенную карту сразу считаем «моей» — авто-сохраняем,
            // чтобы личные транзиты появились на «Сегодня» без отдельного нажатия. Дальнейшие
            // карты (друзей) сохраняются вручную. PrimaryChart.resolve сам возьмёт её как раннюю.
            if (id == 0L && _state.value.savedId == null && dao.allOnce().none { !it.pendingDelete }) {
                save()
            }
        }
    }

    private suspend fun loadTexts(entity: ChartEntity, owner: String, token: String?) {
        // 1) кэш на нужном языке
        val cachedRaw = entity.textsJson?.takeIf { entity.textsLang == lang }
        val cachedTexts = cachedRaw?.let(::parseSafe)
        if (!tokens.withCurrentSession(owner, token) {
            if (cachedTexts != null) {
                textsRaw = cachedRaw
                _state.value = _state.value.copy(texts = cachedTexts, textsOffline = false)
            }
        }) return
        // 2) сервер
        try {
            val body = NatalRequest(
                name = entity.name, year = entity.year, month = entity.month, day = entity.day,
                hour = entity.hour, minute = entity.minute, lat = entity.lat, lng = entity.lng,
                tzStr = entity.tz, city = entity.city, lang = lang,
            )
            if (!tokens.withCurrentSession(owner, token) {}) return
            // Guest requests explicitly suppress interceptor auth as well.
            val raw = api.natal(body, authorization = token?.let { "Bearer $it" } ?: "").toString()
            val freshTexts = parseSafe(raw)
                ?: throw IllegalStateException("Server returned invalid natal interpretation")
            if (!tokens.withCurrentSession(owner, token) {
                textsRaw = raw
                _state.value = _state.value.copy(texts = freshTexts, textsOffline = false)
            }) return
            if (!tokens.withCurrentSession(owner, token) {}) return
            _state.value.savedId?.let { dao.updateTexts(it, raw, lang) }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            tokens.withCurrentSession(owner, token) {
                _state.value = _state.value.copy(texts = cachedTexts, textsOffline = true)
            }
        }
    }

    private fun parseSafe(raw: String): ChartTexts? =
        runCatching { ChartTexts.parse(raw) }.getOrNull()

    fun save() = saveAndContinue(null)

    fun openForecast(onSaved: (Long) -> Unit) = saveAndContinue(onSaved)

    private fun saveAndContinue(onSaved: ((Long) -> Unit)?) {
        viewModelScope.launch {
            saveMutex.withLock {
                val sessionEntity = _state.value.entity ?: return@withLock
                if (dao.owner() != sessionEntity.ownerKey) { _state.value = ChartViewState(); return@withLock }
                val existingId = _state.value.savedId
                if (existingId != null && onSaved != null) {
                    onSaved(existingId)
                    return@withLock
                }
                val e = _state.value.entity ?: return@withLock
                _state.value = _state.value.copy(saving = true, saveFailed = false)
                try {
                    var toSave = if (textsRaw != null) e.copy(textsJson = textsRaw, textsLang = lang) else e
                    if (toSave.serverId != null) toSave = toSave.copy(dirty = true)
                    val newId = dao.upsert(toSave.copy(updatedAt = System.currentTimeMillis()))
                    val saved = toSave.copy(id = if (e.id > 0) e.id else newId)
                    _state.value = _state.value.copy(entity = saved, savedId = saved.id, saving = false)
                    analytics.track("chart_saved")
                    onSaved?.invoke(saved.id)
                    viewModelScope.launch { syncManager.sync() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.value = _state.value.copy(saving = false, saveFailed = true)
                }
            }
        }
    }

    fun delete(onDeleted: () -> Unit = {}) {
        val entity = _state.value.entity ?: return
        val id = _state.value.savedId ?: return
        viewModelScope.launch {
            saveMutex.withLock {
                val token = tokens.get()
                if (!tokens.withCurrentSession(entity.ownerKey, token) {}) return@withLock
                try {
                    // An upload may be in flight even while this screen still has serverId=null.
                    // Only device guests are safe to hard-delete without remote cancellation.
                    if (entity.ownerKey == ru.astrosmap.app.data.TokenStore.GUEST) dao.delete(id)
                    else dao.markPendingDelete(id)
                    syncManager.requestSync()
                    tokens.withCurrentSession(entity.ownerKey, token) { onDeleted() }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { _state.value = _state.value.copy(saveFailed = true) }
            }
        }
    }

    fun edit() {
        ChartDraftHolder.editRequest = _state.value.entity
    }
}

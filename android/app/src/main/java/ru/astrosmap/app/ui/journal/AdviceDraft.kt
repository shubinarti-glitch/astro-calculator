package ru.astrosmap.app.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.astrosmap.app.data.TokenStore
import java.time.LocalDate

data class ForecastAdviceSource(val eventTitle: String, val periodStart: String, val periodEnd: String) {
    fun heading(date: LocalDate, locale: java.util.Locale = java.util.Locale.getDefault()): String = listOf("AstroSMap",
        ru.astrosmap.app.ui.tools.readableForecastDate(date.toString(), locale), eventTitle,
        "${ru.astrosmap.app.ui.tools.readableForecastDate(periodStart, locale)} — ${ru.astrosmap.app.ui.tools.readableForecastDate(periodEnd, locale)}").filter { it.isNotBlank() }.joinToString(" · ")
}

data class AdviceDraft(val date: LocalDate, val advice: String, val owner: String, val source: ForecastAdviceSource? = null)

/** Runtime-only navigation payload; never put private report text in a URL or saved state. */
@HiltViewModel
class AdviceNavigationViewModel @Inject constructor(private val tokens: TokenStore) : ViewModel() {
    private val _draft = MutableStateFlow<AdviceDraft?>(null)
    val draft = _draft.asStateFlow()
    val owners = tokens.owners
    init {
        viewModelScope.launch {
            tokens.owner()
            tokens.owners.collect { owner ->
                if (_draft.value?.owner != owner) _draft.value = null
            }
        }
    }
    fun prepare(date: String, advice: String, expectedOwner: String?, source: ForecastAdviceSource? = null, navigate: () -> Unit) {
        if (expectedOwner == null) return
        val parsed = runCatching { LocalDate.parse(date) }.getOrNull() ?: return
        if (advice.isBlank()) return
        viewModelScope.launch {
            val token = tokens.get()
            tokens.withCurrentSession(expectedOwner, token) {
                _draft.value = AdviceDraft(parsed, advice, expectedOwner, source)
                navigate()
            }
        }
    }
    fun clear() { _draft.value = null }
}

internal fun appendAdvice(existing: String, advice: String): String {
    val clean = advice.trim()
    if (clean.isBlank() || existing.contains(clean)) return existing
    return listOf(existing.trimEnd(), clean).filter { it.isNotEmpty() }.joinToString("\n\n")
}

internal fun sourcedAdvice(draft: AdviceDraft): String =
    listOfNotNull(draft.source?.heading(draft.date), draft.advice.trim()).joinToString("\n")

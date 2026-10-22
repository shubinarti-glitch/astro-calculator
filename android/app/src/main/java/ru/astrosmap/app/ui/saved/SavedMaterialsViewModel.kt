package ru.astrosmap.app.ui.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.astrosmap.app.data.access.Entitlement
import ru.astrosmap.app.data.api.AstroApi

data class SavedMaterialsAccess(
    val loaded: Boolean = false,
    val pdfAllowed: Boolean = false,
)

@HiltViewModel
class SavedMaterialsViewModel @Inject constructor(
    private val api: AstroApi,
    val tokens: ru.astrosmap.app.data.TokenStore,
) : ViewModel() {
    private val _access = MutableStateFlow(SavedMaterialsAccess())
    val access: StateFlow<SavedMaterialsAccess> = _access

    init {
        viewModelScope.launch {
            tokens.owner()
            tokens.owners.collectLatest { owner ->
            _access.value = SavedMaterialsAccess()
            val token = tokens.get()
            val me = if (token != null && owner != null) runCatching { api.me("Bearer $token") }.getOrNull() else null
            if (tokens.get() != token || tokens.owner() != owner) return@collectLatest
            _access.value = SavedMaterialsAccess(
                loaded = true,
                pdfAllowed = me?.accessState()?.hasEntitlement(Entitlement.PDF_EXPORT) == true ||
                    (me?.reportCredits ?: 0) > 0,
            )
            }
        }
    }
}

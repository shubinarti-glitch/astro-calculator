package ru.astrosmap.app.ui.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import ru.astrosmap.app.R
import ru.astrosmap.app.data.SyncManager
import ru.astrosmap.app.data.TokenStore
import ru.astrosmap.app.data.api.ApiError
import ru.astrosmap.app.data.api.AstroApi
import ru.astrosmap.app.data.api.LoginRequest
import ru.astrosmap.app.data.api.MeResponse
import ru.astrosmap.app.data.api.RegisterRequest
import ru.astrosmap.app.ui.AstroLabels
import java.io.IOException
import javax.inject.Inject

sealed interface AccountState {
    data object Loading : AccountState
    data object LoggedOut : AccountState
    data object Offline : AccountState // вход выполнен, но сервер недоступен
    data class LoggedIn(val me: MeResponse) : AccountState
}

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val api: AstroApi,
    private val tokenStore: TokenStore,
    private val syncManager: SyncManager,
) : ViewModel() {

    var state by mutableStateOf<AccountState>(AccountState.Loading)
        private set
    var busy by mutableStateOf(false)
        private set

    /** Текст ошибки с сервера (detail) или null; ресурсные ошибки — errorRes. */
    var errorText by mutableStateOf<String?>(null)
    var errorRes by mutableStateOf<Int?>(null)

    init {
        viewModelScope.launch {
            if (tokenStore.get() == null) {
                state = AccountState.LoggedOut
            } else {
                refreshMe()
            }
        }
    }

    private suspend fun refreshMe() {
        state = try {
            AccountState.LoggedIn(api.me())
        } catch (e: HttpException) {
            if (e.code() == 401) tokenStore.clear() // токен отозван на сервере
            AccountState.LoggedOut
        } catch (e: IOException) {
            AccountState.Offline
        }
    }

    fun retry() {
        state = AccountState.Loading
        viewModelScope.launch { refreshMe() }
    }

    fun login(username: String, password: String) = submit {
        val resp = api.login(LoginRequest(username.trim(), password, lang()))
        tokenStore.save(resp.token)
        refreshMe()
        syncManager.sync()
    }

    fun register(username: String, email: String, password: String) = submit {
        val resp = api.register(RegisterRequest(username.trim(), password, email.trim(), lang()))
        tokenStore.save(resp.token)
        refreshMe()
        syncManager.sync()
    }

    fun logout() {
        viewModelScope.launch {
            runCatching { api.logout() } // сеть могла пропасть — локальный выход всё равно делаем
            tokenStore.clear()
            state = AccountState.LoggedOut
        }
    }

    private fun submit(block: suspend () -> Unit) {
        if (busy) return
        errorText = null
        errorRes = null
        viewModelScope.launch {
            busy = true
            try {
                block()
            } catch (e: HttpException) {
                errorText = parseDetail(e)
            } catch (e: IOException) {
                errorRes = R.string.net_error
            } finally {
                busy = false
            }
        }
    }

    private fun parseDetail(e: HttpException): String {
        val body = e.response()?.errorBody()?.string().orEmpty()
        return runCatching {
            Json { ignoreUnknownKeys = true }.decodeFromString<ApiError>(body).detail
        }.getOrNull().takeUnless { it.isNullOrBlank() } ?: "HTTP ${e.code()}"
    }

    private fun lang() = if (AstroLabels.isRu()) "ru" else "en"
}

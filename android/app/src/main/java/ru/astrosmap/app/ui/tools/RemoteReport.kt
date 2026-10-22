package ru.astrosmap.app.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import ru.astrosmap.app.R
import ru.astrosmap.app.ui.openSite

/** Состояние любого серверного отчёта (прогрессии, соляр, синастрия …). */
sealed interface ReportState {
    data object Loading : ReportState
    data object NeedPremium : ReportState
    data object NeedAuth : ReportState
    data object Offline : ReportState
    data object Forbidden : ReportState
    data object RateLimited : ReportState
    data object InvalidRequest : ReportState
    data object ServerError : ReportState
    data object UnexpectedError : ReportState
    data object MissingChart : ReportState
    data class Ready(val data: JsonObject) : ReportState
}

/** Distinguish transport, access and server failures without exposing server response bodies. */
internal fun reportFailure(error: Exception): ReportState = when (error) {
    is HttpException -> when (error.code()) {
        401 -> ReportState.NeedAuth
        402 -> ReportState.NeedPremium
        403 -> ReportState.Forbidden
        429 -> ReportState.RateLimited
        400, 404, 409, 422 -> ReportState.InvalidRequest
        in 500..599 -> ReportState.ServerError
        else -> ReportState.UnexpectedError
    }
    is java.io.IOException -> ReportState.Offline
    else -> ReportState.UnexpectedError
}

/** Identity denial is terminal even for a free snapshot with an empty entitlement set. */
internal fun reportIdentityDenied(state: ReportState, authenticated: Boolean): Boolean =
    authenticated && (state == ReportState.NeedAuth || state == ReportState.Forbidden)

internal fun reportAllowsCachedResult(state: ReportState): Boolean =
    state == ReportState.Offline || state == ReportState.ServerError

suspend fun loadReport(block: suspend () -> JsonObject): ReportState = try {
    ReportState.Ready(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    reportFailure(e)
}

/** Каркас экрана отчёта: гейты подписки/входа/сети, контент — только для Ready. */
@Composable
fun ReportScaffold(
    state: ReportState,
    onRetry: () -> Unit,
    onMissingChart: (() -> Unit)? = null,
    content: @Composable (JsonObject) -> Unit,
) {
    when (state) {
        ReportState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        ReportState.NeedPremium -> CenteredNote(stringResource(R.string.premium_required)) {
            // В googleplay-сборке без кнопки покупки — только пояснение (текст нейтральный, см. флейвор).
            if (ru.astrosmap.app.BuildConfig.SHOW_EXTERNAL_PURCHASE_LINKS) {
                val context = LocalContext.current
                Button(onClick = { openSite(context) }) { Text(stringResource(R.string.premium_buy)) }
            }
        }
        ReportState.MissingChart -> CenteredNote(stringResource(R.string.forecast_missing_chart)) {
            onMissingChart?.let { action ->
                Button(onClick = action) { Text(stringResource(R.string.forecast_choose_chart)) }
            }
        }
        ReportState.NeedAuth -> CenteredNote(stringResource(R.string.solar_need_auth)) {}
        ReportState.Offline -> CenteredNote(stringResource(R.string.net_error)) {
            Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
        ReportState.Forbidden -> CenteredNote(stringResource(R.string.report_forbidden_v176)) {}
        ReportState.RateLimited -> RetryNote(R.string.report_rate_limit_v176, onRetry)
        ReportState.InvalidRequest -> CenteredNote(stringResource(R.string.report_invalid_v176)) {}
        ReportState.ServerError -> RetryNote(R.string.report_server_v176, onRetry)
        ReportState.UnexpectedError -> RetryNote(R.string.report_unexpected_v176, onRetry)
        is ReportState.Ready -> content(state.data)
    }
}

@Composable
private fun RetryNote(message: Int, onRetry: () -> Unit) {
    CenteredNote(stringResource(message)) {
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
private fun CenteredNote(text: String, action: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        action()
    }
}

// ------------------------------------------------------------------ #
//  Мини-хелперы чтения JsonObject (ответы бэкенда, поля *_ru уже локализованы)
// ------------------------------------------------------------------ #

fun JsonObject.s(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

fun JsonObject.i(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

/** Булево поле: сервер шлёт true/false как JSON-boolean (не строку). */
fun JsonObject.b(key: String): Boolean = (this[key] as? JsonPrimitive)?.content == "true"

fun JsonObject.d(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

fun JsonObject.o(key: String): JsonObject? = this[key] as? JsonObject

fun JsonObject.a(key: String): List<JsonObject> =
    (this[key] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

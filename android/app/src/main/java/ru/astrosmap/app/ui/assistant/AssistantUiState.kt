package ru.astrosmap.app.ui.assistant

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/** UI instructions only: never contains chart input, report text or account data. */
enum class AssistantPhase { EMPTY, LOADING, READY, SAVING, SAVED, ERROR }

data class AssistantUiState(
    val phase: AssistantPhase,
    val hintRes: Int,
    val actionRes: Int? = null,
    val onAction: (() -> Unit)? = null,
    val eventKey: Int = 0,
)

@Composable
internal fun PublishAssistantState(
    onChange: ((AssistantUiState?) -> Unit)?,
    phase: AssistantPhase,
    hintRes: Int,
    actionRes: Int? = null,
    onAction: (() -> Unit)? = null,
    eventKey: Int = 0,
) {
    if (onChange == null) return
    val action by rememberUpdatedState(onAction)
    DisposableEffect(onChange, phase, hintRes, actionRes, eventKey, onAction != null) {
        onChange.invoke(AssistantUiState(phase, hintRes, actionRes,
            if (onAction != null) ({ action?.invoke(); Unit }) else null, eventKey))
        onDispose { onChange.invoke(null) }
    }
}

package ru.astrosmap.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize

/** Keep an editor and its first dependent result inside the scroll viewport above the real IME. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.keepEditorAboveIme(extraBottomPx: Int = 0, resultKey: Any? = null): Modifier {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    var editorSize by remember { mutableStateOf(IntSize.Zero) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, imeBottom, extraBottomPx, resultKey, editorSize) {
        if (focused && imeBottom > 0 && editorSize.height > 0) {
            // Insets and result rows must be placed before requesting their final rectangle.
            withFrameNanos { }
            requester.bringIntoView(Rect(0f, 0f, editorSize.width.toFloat(),
                (editorSize.height + extraBottomPx).toFloat()))
        }
    }
    return this.bringIntoViewRequester(requester).onSizeChanged { editorSize = it }
        .onFocusChanged { focused = it.isFocused }
}

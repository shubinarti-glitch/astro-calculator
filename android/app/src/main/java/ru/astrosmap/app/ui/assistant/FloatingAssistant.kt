package ru.astrosmap.app.ui.assistant

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import ru.astrosmap.app.R
import ru.astrosmap.app.data.DailyNotify
import ru.astrosmap.app.ui.tarot.TarotStorage

private enum class AssistantMotion { IDLE, SLEEPING, REACTING, WORKING, ATTENTIVE }

private val starFrames = intArrayOf(
    R.drawable.assistant_star_00, R.drawable.assistant_star_01,
    R.drawable.assistant_star_02, R.drawable.assistant_star_03,
    R.drawable.assistant_star_04, R.drawable.assistant_star_05,
    R.drawable.assistant_star_06, R.drawable.assistant_star_07,
    R.drawable.assistant_star_08, R.drawable.assistant_star_09,
    R.drawable.assistant_star_10, R.drawable.assistant_star_11,
)

private val sunFrames = intArrayOf(
    R.drawable.assistant_sun_00, R.drawable.assistant_sun_01,
    R.drawable.assistant_sun_02, R.drawable.assistant_sun_03,
    R.drawable.assistant_sun_04, R.drawable.assistant_sun_05,
    R.drawable.assistant_sun_06, R.drawable.assistant_sun_07,
    R.drawable.assistant_sun_08, R.drawable.assistant_sun_09,
    R.drawable.assistant_sun_10, R.drawable.assistant_sun_11,
)

private val moonFrames = intArrayOf(
    R.drawable.assistant_moon_00, R.drawable.assistant_moon_01,
    R.drawable.assistant_moon_02, R.drawable.assistant_moon_03,
    R.drawable.assistant_moon_04, R.drawable.assistant_moon_05,
    R.drawable.assistant_moon_06, R.drawable.assistant_moon_07,
    R.drawable.assistant_moon_08, R.drawable.assistant_moon_09,
    R.drawable.assistant_moon_10, R.drawable.assistant_moon_11,
)

private fun assistantFrames(character: AssistantCharacter) = when (character) {
    AssistantCharacter.STAR -> starFrames
    AssistantCharacter.SUN -> sunFrames
    AssistantCharacter.MOON -> moonFrames
}

internal fun cloudBubbleShape(tailOnRight: Boolean, tailAtTop: Boolean, edgeHeight: Float) = GenericShape { size, _ ->
    val w = size.width
    // Stretch the body, not the cloud edges: long hints must not be clipped by taller lobes.
    val edge = minOf(size.height, edgeHeight)
    fun y(fraction: Float): Float {
        val position = if (fraction <= 0.48f) fraction * edge else size.height - (1f - fraction) * edge
        return if (tailAtTop) size.height - position else position
    }
    moveTo(w * 0.10f, y(0.28f))
    cubicTo(w * 0.03f, y(0.28f), w * 0.02f, y(0.43f), w * 0.08f, y(0.48f))
    lineTo(w * 0.08f, y(0.52f))
    cubicTo(w * 0.02f, y(0.57f), w * 0.06f, y(0.74f), w * 0.16f, y(0.74f))
    if (tailOnRight) {
        cubicTo(w * 0.18f, y(0.84f), w * 0.30f, y(0.86f), w * 0.36f, y(0.79f))
        lineTo(w * 0.72f, y(0.79f))
        lineTo(w * 0.94f, y(0.97f))
        lineTo(w * 0.84f, y(0.73f))
    } else {
        lineTo(w * 0.06f, y(0.97f))
        lineTo(w * 0.28f, y(0.79f))
        lineTo(w * 0.84f, y(0.73f))
    }
    cubicTo(w * 0.95f, y(0.73f), w * 0.98f, y(0.57f), w * 0.92f, y(0.52f))
    lineTo(w * 0.92f, y(0.48f))
    cubicTo(w * 0.98f, y(0.39f), w * 0.95f, y(0.26f), w * 0.87f, y(0.25f))
    cubicTo(w * 0.85f, y(0.12f), w * 0.71f, y(0.08f), w * 0.63f, y(0.16f))
    cubicTo(w * 0.56f, y(0.03f), w * 0.39f, y(0.04f), w * 0.33f, y(0.16f))
    cubicTo(w * 0.25f, y(0.07f), w * 0.12f, y(0.13f), w * 0.10f, y(0.28f))
    close()
}

@Composable
fun FloatingAssistant(
    route: String?,
    modifier: Modifier = Modifier,
    onNavigate: ((String) -> Unit)? = null,
    uiState: AssistantUiState? = null,
) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    var revision by remember { mutableIntStateOf(0) }
    val settings = context.getSharedPreferences("astro_assistant", Context.MODE_PRIVATE)
    DisposableEffect(settings) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        settings.registerOnSharedPreferenceChangeListener(listener)
        onDispose { settings.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    revision
    if (!AssistantPrefs.enabled(context)) return
    val character = AssistantPrefs.character(context)
    val frames = assistantFrames(character)
    val assistantInteraction = remember { MutableInteractionSource() }
    val animationsEnabled = AssistantPrefs.animations(context)
    val hintsEnabled = AssistantPrefs.hints(context)
    var showHint by remember(route) { mutableStateOf(false) }
    var motionState by remember { mutableStateOf(AssistantMotion.IDLE) }
    var frameIndex by remember { mutableIntStateOf(0) }
    var activityToken by remember { mutableIntStateOf(0) }
    val motion = rememberInfiniteTransition(label = "assistant_idle")
    val floatY by motion.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1300), RepeatMode.Reverse),
        label = "assistant_float",
    )
    val scale by motion.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "assistant_breathe",
    )
    val hint = uiState?.let { stringResource(it.hintRes) } ?: assistantHint(context, route)
    val busy = uiState?.phase == AssistantPhase.LOADING || uiState?.phase == AssistantPhase.SAVING

    LaunchedEffect(route) {
        activityToken++
        motionState = AssistantMotion.IDLE
    }
    LaunchedEffect(uiState?.phase, uiState?.eventKey) {
        if (uiState == null) return@LaunchedEffect
        activityToken++
        motionState = when (uiState.phase) {
            AssistantPhase.LOADING, AssistantPhase.SAVING -> AssistantMotion.WORKING
            AssistantPhase.SAVED -> AssistantMotion.REACTING
            AssistantPhase.ERROR -> AssistantMotion.ATTENTIVE
            else -> if (motionState == AssistantMotion.WORKING) AssistantMotion.REACTING else AssistantMotion.IDLE
        }
    }

    LaunchedEffect(character, animationsEnabled, motionState, activityToken) {
        if (!animationsEnabled) {
            frameIndex = 0
            return@LaunchedEffect
        }
        when (motionState) {
            AssistantMotion.IDLE -> {
                val frames = intArrayOf(0, 1, 2, 0, 3, 4, 5, 0)
                val durations = longArrayOf(700, 220, 220, 850, 120, 140, 180, 900)
                while (true) {
                    frames.forEachIndexed { index, frame ->
                        frameIndex = frame
                        delay(durations[index])
                    }
                }
            }
            AssistantMotion.SLEEPING -> while (true) {
                frameIndex = 6
                delay(500)
                frameIndex = 7
                delay(1_100)
            }
            AssistantMotion.REACTING -> {
                intArrayOf(8, 9, 10, 11).forEach { frame ->
                    frameIndex = frame
                    delay(320)
                }
                motionState = if (busy) AssistantMotion.WORKING else AssistantMotion.IDLE
            }
            AssistantMotion.WORKING -> while (true) {
                intArrayOf(1, 2, 1, 0).forEach { frame ->
                    frameIndex = frame
                    delay(440)
                }
            }
            AssistantMotion.ATTENTIVE -> {
                intArrayOf(3, 4, 5, 0).forEach { frame ->
                    frameIndex = frame
                    delay(240)
                }
                motionState = AssistantMotion.IDLE
            }
        }
    }

    LaunchedEffect(activityToken, animationsEnabled, character, busy) {
        if (animationsEnabled && !busy) {
            delay(15_000)
            motionState = AssistantMotion.SLEEPING
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().imePadding()) {
        val density = LocalDensity.current
        val assistantSize = AssistantPrefs.size(context).dp.coerceAtMost(maxWidth).coerceAtMost(maxHeight)
        val insetX = ((maxWidth - assistantSize) / 2).coerceIn(0.dp, 8.dp)
        val insetY = ((maxHeight - assistantSize) / 2).coerceIn(0.dp, 8.dp)
        val maxX = with(density) { (maxWidth - assistantSize - insetX * 2).coerceAtLeast(0.dp).toPx() }
        val maxY = with(density) { (maxHeight - assistantSize - insetY * 2).coerceAtLeast(0.dp).toPx() }
        val bubbleWidth = (maxWidth - 16.dp).coerceIn(0.dp, 232.dp)
        var positionX by remember { mutableFloatStateOf(AssistantPrefs.positionX(context)) }
        var positionY by remember { mutableFloatStateOf(AssistantPrefs.positionY(context)) }
        // Relative anchors survive temporary keyboard resizing without rewriting the saved position.
        val offset = with(density) {
            IntOffset((insetX.toPx() + positionX * maxX).roundToInt(), (insetY.toPx() + positionY * maxY).roundToInt())
        }
        var bubbleHeight by remember { mutableIntStateOf(0) }
        val placement = with(density) {
            placeAssistantBubble(offset.x.toFloat(), offset.y.toFloat(), assistantSize.toPx(),
                bubbleWidth.toPx(), if (bubbleHeight > 0) bubbleHeight.toFloat() else 160.dp.toPx(),
                maxWidth.toPx(), maxHeight.toPx(), 8.dp.toPx(), 8.dp.toPx())
        }
        val bubbleMaxHeight = with(density) { placement.maxHeight.toDp() }
        val bubbleInset = ((bubbleMaxHeight - 48.dp) / 2).coerceIn(0.dp, 42.dp)
        val petState = stringResource(when {
            motionState == AssistantMotion.SLEEPING -> R.string.assistant_pet_sleep_v177
            busy -> R.string.assistant_pet_busy_v177
            uiState?.phase == AssistantPhase.ERROR -> R.string.assistant_pet_attention_v177
            else -> R.string.assistant_pet_awake_v177
        })
        val tapLabel = stringResource(when {
            !hintsEnabled -> R.string.assistant_pet_interact_v177
            showHint -> R.string.assistant_hint_close_v177
            else -> R.string.assistant_hint_open_v177
        })

        AnimatedVisibility(
            visible = showHint && hintsEnabled,
            enter = fadeIn(tween(180)), exit = fadeOut(tween(120)),
        ) {
            Surface(
                modifier = Modifier.width(bubbleWidth)
                    .heightIn(max = bubbleMaxHeight)
                    .onSizeChanged { bubbleHeight = it.height }.graphicsLayer {
                    translationX = placement.x
                    translationY = placement.y
                }.semantics { liveRegion = LiveRegionMode.Polite },
                shape = cloudBubbleShape(placement.tailOnRight, placement.tailAtTop, with(density) { (120.dp * (bubbleInset / 42.dp)).toPx() }),
                color = if (darkTheme) Color(0xFFF5F0FF) else Color(0xFFFFFBEE),
                contentColor = Color(0xFF29223D),
                border = BorderStroke(
                    1.5.dp,
                    if (darkTheme) Color(0xFFB9A5FF) else Color(0xFFD4AA55),
                ),
                tonalElevation = 2.dp,
                shadowElevation = 12.dp,
            ) {
                Column(Modifier.padding(start = 32.dp, end = 32.dp, top = bubbleInset, bottom = bubbleInset)
                    .verticalScroll(rememberScrollState())) {
                    Text(hint, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF29223D))
                    if (uiState != null) {
                        val label = uiState.actionRes
                        val action = uiState.onAction
                        if (label != null && action != null) {
                            TextButton(onClick = { showHint = false; activityToken++; action() }) {
                                Text(stringResource(label), color = Color(0xFF5A49B0))
                            }
                        }
                    } else {
                        val action = assistantAction(route)
                        if (onNavigate != null && action != null) {
                            TextButton(onClick = { showHint = false; onNavigate(action.first) }) {
                                Text(stringResource(action.second), color = Color(0xFF5A49B0))
                            }
                        }
                    }
                    TextButton(onClick = { showHint = false; activityToken++ }) {
                        Text(stringResource(R.string.assistant_hint_close_v177), color = Color(0xFF5A49B0))
                    }
                }
            }
        }

        Image(
            painter = painterResource(
                if (animationsEnabled) {
                    frames[frameIndex.coerceIn(frames.indices)]
                } else {
                    character.imageRes
                },
            ),
            contentDescription = stringResource(character.nameRes),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(assistantSize)
                .semantics { stateDescription = petState }
                .graphicsLayer {
                    translationX = offset.x.toFloat()
                    translationY = offset.y.toFloat() + if (animationsEnabled) floatY else 0f
                    // The generated sprite cells are visually wider than the intended mascot.
                    // Keep the assistant compact and slightly vertical without changing its hit area.
                    scaleX = (if (animationsEnabled) scale else 1f) * 0.88f
                    scaleY = (if (animationsEnabled) scale else 1f) * 1.02f
                }
                .clickable(
                    interactionSource = assistantInteraction,
                    indication = null,
                    onClickLabel = tapLabel,
                ) {
                    activityToken++
                    motionState = AssistantMotion.REACTING
                    if (hintsEnabled) showHint = !showHint
                }
                .pointerInput(maxX, maxY, busy) {
                    detectDragGestures(
                        onDragStart = {
                            activityToken++
                            motionState = if (busy) AssistantMotion.WORKING else AssistantMotion.IDLE
                            showHint = false
                        },
                        onDragCancel = {
                            positionX = AssistantPrefs.positionX(context)
                            positionY = AssistantPrefs.positionY(context)
                        },
                        onDragEnd = {
                            AssistantPrefs.setPosition(
                                context,
                                positionX,
                                positionY,
                            )
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        if (maxX > 0f) positionX = (positionX + dragAmount.x / maxX).coerceIn(0f, 1f)
                        if (maxY > 0f) positionY = (positionY + dragAmount.y / maxY).coerceIn(0f, 1f)
                    }
                },
        )
    }
}

@Composable
private fun assistantHint(context: Context, route: String?): String {
    val textRes = when {
        route == "today" && TarotStorage.todayCard(context) == null -> R.string.assistant_hint_day_card
        route == "today" && !DailyNotify.isEnabled(context) -> R.string.assistant_hint_notifications
        route == "today" -> R.string.assistant_hint_today
        route == "tarot" -> R.string.assistant_hint_tarot
        route == "chart" -> R.string.assistant_hint_chart
        route == "saved" || route == "materials" -> R.string.assistant_hint_saved
        route == "account" -> R.string.assistant_hint_account
        route == "journal" -> R.string.assistant_hint_journal_v176
        route == "luncal" -> R.string.assistant_hint_calendar_v176
        route?.startsWith("return/") == true -> R.string.assistant_hint_return_v176
        route?.startsWith("synastry/") == true -> R.string.assistant_hint_synastry_v176
        route?.startsWith("progression/") == true -> R.string.assistant_hint_progression_v176
        route?.startsWith("forecast") == true || route?.startsWith("transit") == true -> R.string.assistant_hint_forecast
        route == "tools" -> R.string.assistant_hint_tools
        else -> R.string.assistant_hint_default
    }
    return stringResource(textRes)
}

internal fun assistantAction(route: String?): Pair<String, Int>? = when {
    route?.startsWith("forecast/") == true -> "journal" to R.string.assistant_open_diary_v176
    route == "tools" -> "saved" to R.string.assistant_open_charts_v176
    route?.startsWith("view/") == true -> "tools" to R.string.assistant_open_tools_v176
    else -> null
}

package ru.astrosmap.app.ui.assistant

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal data class AssistantPersonality(
    val floatMillis: Int,
    val floatDistance: Float,
    val swayDegrees: Float,
    val reactionMillis: Long,
    val idleFrames: IntArray,
    val idleDurations: LongArray,
    val accent: Color,
)

internal fun assistantPersonality(character: AssistantCharacter) = when (character) {
    AssistantCharacter.STAR -> AssistantPersonality(1_300, 3f, 1.8f, 260,
        intArrayOf(0, 1, 2, 0, 3, 4, 5, 0),
        longArrayOf(700, 220, 220, 850, 120, 140, 180, 900), Color(0xFFE6C6FF))
    AssistantCharacter.SUN -> AssistantPersonality(1_050, 3.8f, 1.2f, 220,
        intArrayOf(0, 3, 4, 5, 0, 1, 2, 0),
        longArrayOf(900, 100, 120, 140, 650, 180, 180, 800), Color(0xFFFFD171))
    AssistantCharacter.MOON -> AssistantPersonality(2_100, 2f, .6f, 380,
        intArrayOf(0, 1, 0, 2, 0, 3, 4, 5, 0),
        longArrayOf(1_200, 300, 700, 300, 1_100, 180, 200, 240, 1_400), Color(0xFFD6D5FF))
}

@Composable
internal fun AssistantAccent(character: AssistantCharacter, progress: State<Float>, modifier: Modifier) {
    val color = remember(character) { assistantPersonality(character).accent }
    Canvas(modifier) {
        val t = progress.value
        if (t <= 0f || t >= 1f) return@Canvas
        val opacity = sin(t * PI).toFloat().coerceIn(0f, 1f)
        val radius = size.minDimension * (.32f + .16f * t)
        when (character) {
            AssistantCharacter.STAR -> repeat(4) { index ->
                val angle = PI * (index / 2.0 + .25)
                val point = center + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * radius
                val arm = size.minDimension * .045f * opacity
                drawLine(color.copy(alpha = opacity), point - Offset(arm, 0f), point + Offset(arm, 0f), 2.dp.toPx(), StrokeCap.Round)
                drawLine(color.copy(alpha = opacity), point - Offset(0f, arm), point + Offset(0f, arm), 2.dp.toPx(), StrokeCap.Round)
            }
            AssistantCharacter.SUN -> {
                drawCircle(color.copy(alpha = opacity * .16f), radius)
                drawCircle(color.copy(alpha = opacity * .65f), radius, style = Stroke(1.5.dp.toPx()))
            }
            AssistantCharacter.MOON -> {
                drawCircle(color.copy(alpha = opacity * .10f), radius)
                drawArc(color.copy(alpha = opacity * .65f), 210f, 110f, false,
                    topLeft = center - Offset(radius, radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                    style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}

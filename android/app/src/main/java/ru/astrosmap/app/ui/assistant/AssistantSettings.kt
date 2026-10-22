package ru.astrosmap.app.ui.assistant

import android.animation.ValueAnimator
import android.content.Context
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.delay
import ru.astrosmap.app.R

@Composable
fun AssistantSettings(context: Context) {
    var selected by remember { mutableStateOf(AssistantPrefs.character(context)) }
    var enabled by remember { mutableStateOf(AssistantPrefs.enabled(context)) }
    var hints by remember { mutableStateOf(AssistantPrefs.hints(context)) }
    var size by remember { mutableStateOf(AssistantPrefs.size(context)) }

    Text(stringResource(R.string.assistant_title), style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary)
    Text(stringResource(R.string.assistant_choose), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        AssistantCharacter.entries.forEach { character ->
            val active = selected == character
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                AssistantChoice(character, active) {
                    selected = character
                    AssistantPrefs.setCharacter(context, character)
                }
                Text(stringResource(character.nameRes), style = MaterialTheme.typography.labelSmall,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center)
            }
        }
    }
    Text(stringResource(R.string.assistant_size))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(64, 88, 104).forEach { value ->
            androidx.compose.material3.FilterChip(
                modifier = Modifier.weight(1f),
                selected = size == value,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = .16f),
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface),
                onClick = { size = value; AssistantPrefs.setSize(context, value) },
                label = { Text(stringResource(when (value) {
                    64 -> R.string.assistant_size_small
                    88 -> R.string.assistant_size_medium
                    else -> R.string.assistant_size_large
                }), textAlign = TextAlign.Center) },
            )
        }
    }
    AssistantSwitch(stringResource(R.string.assistant_show), enabled) {
        enabled = it; AssistantPrefs.setEnabled(context, it)
    }
    AssistantSwitch(stringResource(R.string.assistant_hints), hints, enabled) {
        hints = it; AssistantPrefs.setHints(context, it)
    }
}

@Composable
private fun AssistantChoice(character: AssistantCharacter, selected: Boolean, onClick: () -> Unit) {
    val personality = remember(character) { assistantPersonality(character) }
    val frames = assistantFrames(character)
    val animations = ValueAnimator.areAnimatorsEnabled()
    var frame by remember(character) { mutableIntStateOf(0) }
    LaunchedEffect(character, animations) {
        if (!animations) { frame = 0; return@LaunchedEffect }
        while (true) {
            personality.idleFrames.forEachIndexed { index, value ->
                frame = value
                delay(personality.idleDurations[index])
            }
        }
    }
    val motion = rememberInfiniteTransition(label = "assistant_choice")
    val drift by motion.animateFloat(-1f, 1f,
        infiniteRepeatable(tween(personality.floatMillis), RepeatMode.Reverse), label = "assistant_choice_drift")
    Box(Modifier.size(82.dp).clip(CircleShape)
        .border(BorderStroke(if (selected) 3.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline), CircleShape)
        .selectable(selected, role = Role.RadioButton, onClick = onClick)) {
        Image(painterResource(frames[frame]), contentDescription = stringResource(character.nameRes),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(2.dp).graphicsLayer {
                translationY = -8.dp.toPx() + if (animations) drift * personality.floatDistance else 0f
                rotationZ = if (animations) drift * personality.swayDegrees else 0f
            })
    }
}

@Composable
private fun AssistantSwitch(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked && enabled, enabled = enabled, onCheckedChange = onChange)
    }
}

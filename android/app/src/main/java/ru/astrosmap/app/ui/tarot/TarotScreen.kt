package ru.astrosmap.app.ui.tarot

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import ru.astrosmap.app.R
import ru.astrosmap.app.data.api.AstroApi
import ru.astrosmap.app.ui.openSite
import ru.astrosmap.app.ui.theme.AppHeader
import ru.astrosmap.app.ui.theme.AstroPanel
import javax.inject.Inject

/** Живой таролог — контакт для консультации (тот же, что у астролога на сайте). */
private const val TAROLOGIST_TG = "https://t.me/Astrosmap"

/** Два расклада-триплета: позиции задают, о чём каждая из трёх карт. */
private enum class Spread(val titleRes: Int, val positions: List<Int>) {
    SITUATION(R.string.tarot_spread_situation, listOf(
        R.string.tarot_pos_essence, R.string.tarot_pos_obstacle, R.string.tarot_pos_advice)),
    MFA(R.string.tarot_spread_mfa, listOf(
        R.string.tarot_pos_thoughts, R.string.tarot_pos_feelings, R.string.tarot_pos_actions)),
}

@HiltViewModel
class TarotViewModel @Inject constructor(private val api: AstroApi) : ViewModel() {
    var premium by mutableStateOf(false)
        private set

    init {
        // Премиум определяет частоту раскладов. Офлайн/без входа — считаем бесплатным.
        viewModelScope.launch { premium = runCatching { api.me().premium }.getOrDefault(false) }
    }
}

@Composable
fun TarotScreen(viewModel: TarotViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var spread by remember { mutableStateOf<Spread?>(null) }
    var cards by remember { mutableStateOf<List<TarotCard>>(emptyList()) }
    var revealed by remember { mutableStateOf(setOf<Int>()) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppHeader(stringResource(R.string.section_tarot))

        // Предупреждение по ФЗ + честная ссылка на живого таролога.
        AstroPanel {
            Text(
                stringResource(R.string.tarot_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { openSite(context, TAROLOGIST_TG) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.tarot_live_reader)) }
        }

        if (spread == null) {
            AstroPanel {
                Text(
                    stringResource(R.string.tarot_choose_spread),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                // У каждого расклада свой недельный лимит — считаем по отдельности.
                Spread.entries.forEach { s ->
                    val cooldown = TarotStorage.spreadCooldownDays(context, s.name, viewModel.premium)
                    Button(
                        onClick = {
                            spread = s
                            cards = TarotDeck.draw(3)
                            revealed = emptySet()
                            TarotStorage.markSpreadDone(context, s.name)
                        },
                        enabled = cooldown == 0,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(s.titleRes)) }
                    if (cooldown > 0) {
                        Text(
                            stringResource(R.string.tarot_cooldown, cooldown),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val anyLocked = Spread.entries.any {
                    TarotStorage.spreadCooldownDays(context, it.name, viewModel.premium) > 0
                }
                if (anyLocked && !viewModel.premium) {
                    Button(onClick = { openSite(context, "https://astrosmap.ru/#premium") },
                        modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.premium_buy))
                    }
                }
            }
        } else {
            AstroPanel {
                Text(
                    stringResource(spread!!.titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.tarot_tap_to_open),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                cards.forEachIndexed { i, card ->
                    SpreadRow(
                        position = stringResource(spread!!.positions[i]),
                        card = card,
                        open = i in revealed,
                        onOpen = { revealed = revealed + i },
                    )
                }
                OutlinedButton(
                    onClick = { spread = null },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.tarot_done)) }
            }
        }
    }
}

@Composable
private fun SpreadRow(position: String, card: TarotCard, open: Boolean, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val face = rememberTarotFace(card.id)
        if (open && face != null) {
            Image(
                bitmap = face,
                contentDescription = card.name,
                modifier = Modifier.width(74.dp).aspectRatio(0.58f).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit,
            )
        } else {
            CardBack(
                Modifier
                    .width(74.dp)
                    .aspectRatio(0.58f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpen,
                    ),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                position,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            if (open) {
                Text(card.name, style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary)
                Text(card.meaning, style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    stringResource(R.string.tarot_face_down),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

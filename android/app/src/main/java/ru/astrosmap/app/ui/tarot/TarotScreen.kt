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

/**
 * Расклады. `positions` задают и смысл позиций, и число карт.
 *
 * YES_NO — по карте на «да» и на «нет» из полной колоды: выигрывает позиция, где карта
 * старше по градации колоды. Исход определён самими картами, а не случайной меткой.
 */
private enum class Spread(val titleRes: Int, val positions: List<Int>) {
    SITUATION(R.string.tarot_spread_situation, listOf(
        R.string.tarot_pos_essence, R.string.tarot_pos_obstacle, R.string.tarot_pos_advice)),
    MFA(R.string.tarot_spread_mfa, listOf(
        R.string.tarot_pos_thoughts, R.string.tarot_pos_feelings, R.string.tarot_pos_actions)),
    YES_NO(R.string.tarot_spread_yesno, listOf(
        R.string.tarot_pos_yes, R.string.tarot_pos_no)),
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
                            cards = TarotDeck.draw(s.positions.size)
                            revealed = emptySet()
                            TarotStorage.markSpreadDone(context, s.name)
                        },
                        enabled = cooldown == 0,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(s.titleRes)) }
                    if (cooldown > 0) {
                        Text(
                            if (viewModel.premium) stringResource(R.string.tarot_cooldown_daily)
                            else stringResource(R.string.tarot_cooldown, cooldown),
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
                if (spread == Spread.YES_NO) {
                    Text(
                        stringResource(R.string.tarot_yesno_rule),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                cards.forEachIndexed { i, card ->
                    SpreadRow(
                        position = stringResource(spread!!.positions[i]),
                        card = card,
                        open = i in revealed,
                        onOpen = { revealed = revealed + i },
                    )
                }
                // Вердикт «да / нет» — только когда обе карты открыты.
                if (spread == Spread.YES_NO && revealed.size == cards.size && cards.size == 2) {
                    YesNoVerdict(yes = cards[0], no = cards[1])
                }
                OutlinedButton(
                    onClick = { spread = null },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.tarot_done)) }
            }
        }
    }
}

/**
 * Итог расклада «да / нет»: сравниваем старшинство карт на двух позициях
 * по градации всей колоды. Побеждает старшая — она и даёт ответ.
 * Ничьей не бывает: карты в раскладе всегда разные, а ранги уникальны.
 */
@Composable
private fun YesNoVerdict(yes: TarotCard, no: TarotCard) {
    val isYes = TarotDeck.rank(yes.id) > TarotDeck.rank(no.id)
    val winner = if (isYes) yes else no

    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(if (isYes) R.string.tarot_answer_yes else R.string.tarot_answer_no),
            style = MaterialTheme.typography.headlineMedium,
            color = if (isYes) ru.astrosmap.app.ui.theme.GoodColor else MaterialTheme.colorScheme.error,
        )
        Text(
            stringResource(
                R.string.tarot_yesno_why,
                winner.name,
                stringResource(if (isYes) R.string.tarot_pos_yes else R.string.tarot_pos_no),
            ),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            winner.advice,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
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

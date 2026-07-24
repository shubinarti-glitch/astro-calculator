package ru.astrosmap.app.ui.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Засечное семейство бренда.
 *
 * ponytail: системный serif вместо вшитого Cormorant. Инстанцированные из вариативного
 * шрифта TTF Android отказывался рендерить — текст не отрисовывался, кадровый цикл
 * вставал и приложение зависало на заставке. Системный serif выглядит близко и не рискует.
 */
val Cormorant = FontFamily.Serif

private val WordmarkGold = Color(0xFFE9DBBF)
private val WordmarkPurple = Color(0xFF8B7BD8)

/** Логотип «AstroSMap»: начало золотистое, «Map» — фиолетовый, как в референсе. */
@Composable
fun AstroWordmark(fontSize: TextUnit = 34.sp, modifier: Modifier = Modifier) {
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = WordmarkGold)) { append("AstroS") }
        withStyle(SpanStyle(color = WordmarkPurple)) { append("Map") }
    }
    Text(
        text = text,
        modifier = modifier,
        style = LocalTextStyle.current.copy(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
        ),
    )
}

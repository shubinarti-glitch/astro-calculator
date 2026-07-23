package ru.astrosmap.app.ui.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import ru.astrosmap.app.R

/** Засечный шрифт бренда (как на сайте), два начертания. */
val Cormorant = FontFamily(
    Font(R.font.cormorant_semibold, FontWeight.SemiBold),
    Font(R.font.cormorant_bold, FontWeight.Bold),
)

private val WordmarkGold = Color(0xFFE9DBBF)
private val WordmarkPurple = Color(0xFF8B7BD8)

/**
 * Логотип «AstroSMap» засечным шрифтом. Акцент как в референсе: начало золотистое,
 * «Map» — фиолетовый. Раскраска по буквам (annotated string), без brush —
 * надёжнее рендерится с кастомным шрифтом.
 */
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
            fontFamily = Cormorant,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
        ),
    )
}

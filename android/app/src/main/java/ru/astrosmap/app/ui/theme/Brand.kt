package ru.astrosmap.app.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
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

/**
 * Логотип «AstroSMap»: начало золотистое, «Map» — фиолетовый, как в референсе.
 * По буквам периодически пробегает световой блик — логотип «сверкает». Блик обрезан
 * по силуэту текста (BlendMode.SrcAtop), цвета букв сохраняются.
 */
@Composable
fun AstroWordmark(fontSize: TextUnit = 34.sp, modifier: Modifier = Modifier) {
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = WordmarkGold)) { append("AstroS") }
        withStyle(SpanStyle(color = WordmarkPurple)) { append("Map") }
    }
    // Блик пробегает за ~0,9 с, затем пауза ~2,3 с — сверкание, а не мельтешение.
    val shine = rememberInfiniteTransition(label = "wordmark-shine")
    val pos by shine.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 3200
                -0.4f at 0
                1.4f at 900 using LinearEasing
                1.4f at 3200
            },
            RepeatMode.Restart,
        ),
        label = "shine-pos",
    )
    Text(
        text = text,
        modifier = modifier.drawWithContent {
            drawContent()
            val band = size.width * 0.32f
            val cx = pos * size.width
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.6f), Color.Transparent),
                    start = Offset(cx - band, 0f),
                    end = Offset(cx + band, size.height),
                ),
                blendMode = BlendMode.SrcAtop,
            )
        },
        style = LocalTextStyle.current.copy(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
        ),
    )
}

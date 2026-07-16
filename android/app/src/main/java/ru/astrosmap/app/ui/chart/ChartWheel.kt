package ru.astrosmap.app.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import ru.astrosmap.app.astro.AspectHit
import ru.astrosmap.app.astro.ChartPoint
import ru.astrosmap.app.astro.HouseCusp
import ru.astrosmap.app.astro.NatalChart
import ru.astrosmap.app.ui.AstroLabels
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// Палитра колеса — тёмная тема сайта (frontend/css/style.css).
private val FrameColor = Color(0xFF0C0C1E)      // --chart-frame
private val LineDim = Color(0x338B9BD8)         // тонкие линии
private val LineBright = Color(0x668B9BD8)
private val GoldColor = Color(0xFFC9A86A)       // --accent
private val TextColor = Color(0xFFECE9F5)       // --text
private val HarmoniousColor = Color(0xFF5FC98A) // --good
private val TenseColor = Color(0xFFE0716F)      // --bad
private val CreativeColor = Color(0xFF8B7BD8)   // --accent-2

// Цвета стихий: огонь/земля/воздух/вода (по индексу знака % 4).
private val ElementColors = listOf(
    Color(0xFFE0716F), Color(0xFF5FC98A), Color(0xFFC9A86A), Color(0xFF8B9BD8),
)

private val SignGlyphs = ru.astrosmap.app.astro.SIGNS.map { AstroLabels.signGlyphs.getValue(it) }

private val Angles = setOf("Ascendant", "Medium_Coeli", "Descendant", "Imum_Coeli")
private val PointGlyphs = AstroLabels.pointGlyphs.filterKeys { it !in Angles }

private fun aspectColor(aspect: String): Color = when (aspect) {
    "trine", "sextile" -> HarmoniousColor
    "square", "opposition" -> TenseColor
    "quintile" -> CreativeColor
    else -> GoldColor // conjunction
}

/** Колесо натальной карты: зодиак, дома, планеты, линии аспектов. */
@Composable
fun ChartWheel(chart: NatalChart, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier = modifier) {
        drawWheel(chart, measurer)
    }
}

internal fun DrawScope.drawWheel(chart: NatalChart, measurer: TextMeasurer) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val radius = min(cx, cy) * 0.92f // запас под подписи As/Mc снаружи
    val asc = chart.houses.first { it.num == 1 }.absPos

    // Экранный угол долготы: ASC слева, зодиак растёт против часовой стрелки.
    fun angleOf(lon: Double): Double = PI - Math.toRadians(lon - asc)
    fun pos(r: Float, lon: Double): Offset {
        val a = angleOf(lon)
        return Offset(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
    }

    val rZodiacOuter = radius
    val rZodiacInner = radius * 0.84f
    val rPlanet = radius * 0.70f
    val rHouseNum = radius * 0.48f
    val rAspect = radius * 0.42f

    // Фон и окружности.
    drawCircle(FrameColor, rZodiacOuter, Offset(cx, cy))
    for (r in listOf(rZodiacOuter, rZodiacInner, rAspect)) {
        drawCircle(LineBright, r, Offset(cx, cy), style = Stroke(radius * 0.004f))
    }

    // Зодиак: границы знаков и символы.
    for (i in 0 until 12) {
        val lon = i * 30.0
        drawLine(LineBright, pos(rZodiacInner, lon), pos(rZodiacOuter, lon), radius * 0.004f)
        drawGlyph(
            measurer, SignGlyphs[i], pos(radius * 0.92f, lon + 15.0),
            ElementColors[i % 4], radius * 0.075f,
        )
    }
    // Мелкие деления по 5°.
    for (d in 0 until 72) {
        val lon = d * 5.0
        if (d % 6 != 0) {
            drawLine(LineDim, pos(rZodiacInner, lon), pos(rZodiacInner * 1.025f, lon), radius * 0.003f)
        }
    }

    // Дома: куспиды и номера. Оси (1/4/7/10) — золотом и толще.
    for (h in chart.houses) {
        val isAxis = h.num in listOf(1, 4, 7, 10)
        drawLine(
            if (isAxis) GoldColor else LineBright,
            pos(rAspect, h.absPos), pos(rZodiacInner, h.absPos),
            radius * if (isAxis) 0.007f else 0.003f,
            cap = StrokeCap.Round,
        )
        val next = chart.houses.first { it.num == h.num % 12 + 1 }
        val mid = h.absPos + (((next.absPos - h.absPos) % 360.0 + 360.0) % 360.0) / 2.0
        drawGlyph(measurer, h.num.toString(), pos(rHouseNum, mid), LineBright.copy(alpha = 0.9f), radius * 0.045f)
    }

    // Подписи углов снаружи колеса.
    val angleLabels = listOf("Ascendant" to "As", "Medium_Coeli" to "Mc", "Descendant" to "Ds", "Imum_Coeli" to "Ic")
    for ((name, label) in angleLabels) {
        val a = chart.angles.firstOrNull { it.name == name } ?: continue
        drawGlyph(measurer, label, pos(radius * 1.055f, a.absPos), GoldColor, radius * 0.05f)
    }

    // Линии аспектов (по истинным долготам): прозрачность растёт с точностью орбиса.
    val lonByName = (chart.points + chart.angles).associate { it.name to it.absPos }
    for (asp in chart.aspects) {
        val l1 = lonByName[asp.p1] ?: continue
        val l2 = lonByName[asp.p2] ?: continue
        val alpha = (1.0f - (asp.orbit / 12.0f).toFloat()).coerceIn(0.25f, 0.9f)
        drawLine(
            aspectColor(asp.aspect).copy(alpha = alpha),
            pos(rAspect, l1), pos(rAspect, l2), radius * 0.005f,
        )
    }

    // Планеты: чёрточка на истинном градусе + символ с раздвижкой от наложений.
    val shown = chart.points.filter { it.name in PointGlyphs }
    val spreadLons = spreadAngles(shown.map { it.absPos }, minSep = 7.0)
    for ((i, p) in shown.withIndex()) {
        drawLine(TextColor, pos(rZodiacInner, p.absPos), pos(rZodiacInner * 0.965f, p.absPos), radius * 0.005f)
        val glyphPos = pos(rPlanet, spreadLons[i])
        drawGlyph(measurer, PointGlyphs.getValue(p.name), glyphPos, TextColor, radius * 0.08f)
        if (p.retrograde) {
            val rPos = pos(rPlanet * 0.88f, spreadLons[i])
            drawGlyph(measurer, "R", rPos, TenseColor, radius * 0.038f)
        }
    }
}

/** Раздвигает близкие долготы, чтобы символы планет не накладывались. */
internal fun spreadAngles(lons: List<Double>, minSep: Double): List<Double> {
    if (lons.size < 2) return lons
    val indexed = lons.withIndex().sortedBy { it.value }
    val adjusted = indexed.map { it.value }.toMutableList()
    repeat(30) {
        for (i in adjusted.indices) {
            val j = (i + 1) % adjusted.size
            val gap = if (j == 0) (adjusted[j] + 360.0) - adjusted[i] else adjusted[j] - adjusted[i]
            if (gap < minSep) {
                val push = (minSep - gap) / 2.0
                adjusted[i] -= push
                adjusted[j] += push
            }
        }
    }
    val result = DoubleArray(lons.size)
    for ((k, entry) in indexed.withIndex()) result[entry.index] = adjusted[k]
    return result.toList()
}

private fun DrawScope.drawGlyph(
    measurer: TextMeasurer,
    text: String,
    center: Offset,
    color: Color,
    sizePx: Float,
) {
    val layout = measurer.measure(
        text,
        TextStyle(color = color, fontSize = (sizePx / density).sp, fontWeight = FontWeight.Medium),
    )
    drawText(
        layout,
        topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f),
    )
}

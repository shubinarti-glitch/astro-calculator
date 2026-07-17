package ru.astrosmap.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/** Звёздное небо — как фон сайта. Рисуется один раз, позиции фиксированы сидом. */
@Composable
fun StarryBackground(modifier: Modifier = Modifier) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0A0A1A)
    if (!isDark) return
    Canvas(modifier.fillMaxSize()) {
        val rnd = Random(42)
        repeat(90) {
            val x = rnd.nextFloat() * size.width
            val y = rnd.nextFloat() * size.height
            val r = 0.6f + rnd.nextFloat() * 1.6f
            val a = 0.12f + rnd.nextFloat() * 0.5f
            drawCircle(Color(0xFFECE9F5).copy(alpha = a), radius = r, center = Offset(x, y))
        }
    }
}

/** Панель-карточка в стиле сайта: скругление, полупрозрачная заливка, тонкая рамка. */
@Composable
fun AstroPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = Color(0xB8161630),                       // --panel
        border = BorderStroke(1.dp, Color(0x2E7878C8)),  // --panel-border
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** Шапка приложения: звезда-логотип + название, как на сайте. */
@Composable
fun AppHeader(subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(40.dp)) {
            fun star(cx: Float, cy: Float, rl: Float, rs: Float, color: Color) {
                val p = Path()
                for (i in 0 until 8) {
                    val r = if (i % 2 == 0) rl else rs
                    val a = Math.toRadians(i * 45.0 - 90).toFloat()
                    val x = cx + r * kotlin.math.cos(a)
                    val y = cy + r * kotlin.math.sin(a)
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close()
                drawPath(p, color)
            }
            star(size.width * 0.45f, size.height * 0.55f, size.width * 0.42f, size.width * 0.10f, Color(0xFFC9A86A))
            star(size.width * 0.78f, size.height * 0.22f, size.width * 0.13f, size.width * 0.035f, Color(0xFF8B7BD8))
        }
        Column(Modifier.padding(start = 10.dp)) {
            Text(
                "AstroSMap",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

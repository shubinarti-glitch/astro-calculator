package ru.astrosmap.app.ui.chart

import org.junit.Assert.*
import org.junit.Test
import ru.astrosmap.app.astro.*
import kotlin.math.*

class ChartWheelHitTest {
    private fun chart(longitudes: List<Double>, asc: Double = 0.0) = NatalChart(
        0.0, longitudes.mapIndexed { i, lon -> ChartPoint(listOf("Sun", "Moon", "Mercury")[i], lon, 1.0, false, 1) },
        emptyList(), (1..12).map { HouseCusp(it, (asc + (it - 1) * 30) % 360) },
        emptyList(), LunarPhase(1, "New Moon", 0.0),
    )

    @Test fun ascendantRotationAndRectangularCanvasMatchRenderedGlyphs() {
        val chart = chart(listOf(90.0), asc = 90.0)
        // Radius = min(1000, 800) / 2 * .92; ASC is on the left.
        assertEquals("Sun", chartPointHit(chart, 1000f, 800f, 500f - 368f * .70f, 400f, 24f))
        assertNull(chartPointHit(chart, 1000f, 800f, 500f, 400f, 24f))
    }

    @Test fun clusterHitFollowsDisplacedGlyphNotTrueLongitude() {
        val chart = chart(listOf(1.0, 1.1, 1.2))
        val spread = spreadAngles(chart.points.map { it.absPos }, 7.0)
        chart.points.forEachIndexed { i, point ->
            val a = PI - Math.toRadians(spread[i])
            val x = 500f + (460 * .70 * cos(a)).toFloat()
            val y = 500f + (460 * .70 * sin(a)).toFloat()
            assertEquals(point.name, chartPointHit(chart, 1000f, 1000f, x, y, 24f))
        }
    }

    @Test fun wrappingClusterAndMissesRemainUnambiguous() {
        val chart = chart(listOf(359.0, 0.1))
        val spread = spreadAngles(chart.points.map { it.absPos }, 7.0)
        spread.forEachIndexed { i, lon ->
            val a = PI - Math.toRadians(lon)
            assertEquals(chart.points[i].name, chartPointHit(chart, 600f, 600f,
                300f + (276 * .70 * cos(a)).toFloat(), 300f + (276 * .70 * sin(a)).toFloat(), 24f))
        }
        assertNull(chartPointHit(chart, 600f, 600f, 0f, 0f, 24f))
        assertNull(chartPointHit(chart, 0f, 600f, 0f, 0f, 24f))
        assertNull(chartPointHit(chart.copy(houses = emptyList()), 600f, 600f, 100f, 100f, 24f))
    }
}

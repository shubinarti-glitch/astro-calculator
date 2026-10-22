package ru.astrosmap.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import ru.astrosmap.app.R
import ru.astrosmap.app.astro.ChartPoint
import ru.astrosmap.app.data.ChartTexts
import ru.astrosmap.app.data.Titled
import ru.astrosmap.app.ui.theme.AstroTheme
import ru.astrosmap.app.ui.view.SelectedChartPoint

class ChartSelectionUiTest {
    @get:Rule val compose = createComposeRule()
    private val point = ChartPoint("Sun", 10.0, 1.0, false, 1)
    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun runtimeInterpretationIsReadableAtLargeFontAndResetWorks() {
        val texts = ChartTexts(emptyList(), emptyList(), emptyList(), emptyList(),
            mapOf("Sun" to listOf(Titled("Synthetic heading", "Synthetic runtime meaning"))), emptyMap())
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                AstroTheme {
                    var selected by remember { mutableStateOf(true) }
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        if (selected) SelectedChartPoint(point, emptyList(), texts) { selected = false }
                    }
                }
            }
        }
        compose.onNodeWithText("Synthetic runtime meaning").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(label(R.string.chart_reset_selection)).performScrollTo().performClick()
        compose.onNodeWithText("Synthetic runtime meaning").assertDoesNotExist()
    }

    @Test fun missingRuntimeTextIsHonestAndDoesNotInventMeaning() {
        compose.setContent { AstroTheme { SelectedChartPoint(point, emptyList(), null) {} } }
        compose.onNodeWithText(label(R.string.chart_interpretation_unavailable)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.chart_no_aspects)).assertIsDisplayed()
    }
}

package ru.astrosmap.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import ru.astrosmap.app.R
import ru.astrosmap.app.ui.assistant.AssistantPrefs
import ru.astrosmap.app.ui.assistant.FloatingAssistant
import ru.astrosmap.app.ui.theme.AstroTheme
import ru.astrosmap.app.ui.tools.ForecastBasis
import ru.astrosmap.app.ui.tools.readableForecastDate
import ru.astrosmap.app.ui.tools.ForecastMeaning
import ru.astrosmap.app.ui.tools.ForecastPeriodOverview
import ru.astrosmap.app.ui.tools.ReportScaffold
import ru.astrosmap.app.ui.tools.ReportState
import kotlinx.serialization.json.jsonObject

class ForecastContentUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun label(id: Int) = context.getString(id)

    @Test fun meaningShowsInterpretationAndAdviceAtLargeFontWithoutLegacyDuplicate() {
        val event = Json.parseToJsonElement("""{"interpretation":"Synthetic interpretation", "advice":"Synthetic practical action", "text":"Legacy combined text"}""").jsonObject
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                AstroTheme { Column(Modifier.verticalScroll(rememberScrollState())) { ForecastMeaning(event) } }
            }
        }
        compose.onNodeWithText(label(R.string.forecast_interpretation_label)).assertIsDisplayed()
        compose.onNodeWithText("Synthetic interpretation").assertIsDisplayed()
        compose.onNodeWithText("Synthetic practical action").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Legacy combined text").assertDoesNotExist()
    }

    @Test fun legacyMeaningRemainsReadable() {
        val event = Json.parseToJsonElement("""{"text":"Legacy report response"}""").jsonObject
        compose.setContent { AstroTheme { Column { ForecastMeaning(event) } } }
        compose.onNodeWithText("Legacy report response").assertIsDisplayed()
    }

    @Test fun overviewShowsDistinctOpportunityAndAttentionWithDates() {
        val payload = Json.parseToJsonElement("""{"events":[{"tone":"positive","date":"2026-10-20","interpretation":"Opportunity sentence. Further detail.","advice":"Useful action."},{"tone":"negative","date":"2026-10-21","interpretation":"Attention sentence.","advice":"Careful action."}]}""").jsonObject
        compose.setContent { AstroTheme { Column(Modifier.verticalScroll(rememberScrollState())) { ForecastPeriodOverview(payload) } } }
        compose.onNodeWithText(label(R.string.forecast_period_main)).assertIsDisplayed()
        compose.onNodeWithText("Opportunity sentence.").assertIsDisplayed()
        compose.onNodeWithText(label(R.string.forecast_opportunity) + " · " + readableForecastDate("2026-10-20", context.resources.configuration.locales[0])).assertIsDisplayed()
        compose.onNodeWithText("Careful action.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Further detail.").assertDoesNotExist()
    }

    @Test fun adviceLinksToSelectedDayAndTechnicalBasisIsOptional() {
        val event = Json.parseToJsonElement("""{"date":"2026-10-21","interpretation":"Synthetic meaning","advice":"Synthetic action","p1_ru":"Test Sun","p2_ru":"Test Moon","aspect_ru":"Test aspect"}""").jsonObject
        var captured: Pair<String, String>? = null
        compose.setContent {
            AstroTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ForecastMeaning(event) { date, advice -> captured = date to advice }
                    ForecastBasis(event)
                }
            }
        }
        compose.onNodeWithText("Synthetic meaning").assertIsDisplayed()
        compose.onNodeWithText("Test Sun · Test aspect · Test Moon").assertDoesNotExist()
        compose.onNodeWithText(label(R.string.forecast_journal_v176)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals("2026-10-21" to "Synthetic action", captured) }
        compose.onNodeWithText(label(R.string.forecast_basis_show_v176)).performScrollTo().performClick()
        compose.onNodeWithText("Test Sun · Test aspect · Test Moon").performScrollTo().assertIsDisplayed()
    }

    @Test fun comparisonNamesBaselinePeriodAndShowsActualChangedThemeAndAdvice() {
        val result = ru.astrosmap.app.ui.tools.ForecastComparison(1, 1, 0, 0, true,
            listOf(ru.astrosmap.app.ui.tools.ForecastThemeChange("Synthetic topic", "Earlier meaning", "New meaning", "Current action")),
            "2026-10-01", "2026-10-31")
        compose.setContent {
            AstroTheme { Column(Modifier.verticalScroll(rememberScrollState())) { ru.astrosmap.app.ui.tools.ForecastComparisonCard(result) } }
        }
        val locale = context.resources.configuration.locales[0]
        val heading = context.getString(R.string.forecast_compare_baseline_v176,
            readableForecastDate("2026-10-01", locale), readableForecastDate("2026-10-31", locale))
        compose.onNodeWithText(heading).assertIsDisplayed()
        compose.onNodeWithText("Synthetic topic").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Earlier meaning").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("New meaning").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Current action").performScrollTo().assertIsDisplayed()
    }

    @Test fun missingChartHasWorkingRecoveryAction() {
        var selected = 0
        compose.setContent { AstroTheme { ReportScaffold(ReportState.MissingChart, {}, { selected++ }) {} } }
        compose.onNodeWithText(label(R.string.forecast_missing_chart)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.forecast_choose_chart)).performClick()
        compose.runOnIdle { assertEquals(1, selected) }
    }

    @Test fun animatedPetCanBeDraggedAndExplainsForecastOnTap() {
        val wasEnabled = AssistantPrefs.enabled(context)
        val hadHints = AssistantPrefs.hints(context)
        val hadAnimations = AssistantPrefs.animations(context)
        val oldX = AssistantPrefs.positionX(context)
        val oldY = AssistantPrefs.positionY(context)
        try {
            AssistantPrefs.setEnabled(context, true)
            AssistantPrefs.setHints(context, true)
            AssistantPrefs.setAnimations(context, true)
            AssistantPrefs.setPosition(context, 0.6f, 0.5f)
            compose.setContent { AstroTheme { FloatingAssistant("forecast/1") } }
            val pet = compose.onNodeWithContentDescription(label(AssistantPrefs.character(context).nameRes))
            pet.assertIsDisplayed()
            val before = pet.fetchSemanticsNode().boundsInRoot.center
            pet.performTouchInput { swipe(center, center + Offset(-120f, -120f), 500) }
            compose.waitForIdle()
            val after = pet.fetchSemanticsNode().boundsInRoot.center
            assertTrue("Pet must remain draggable on report screens", (after - before).getDistance() > 40f)
            pet.performClick()
            compose.onNodeWithText(label(R.string.assistant_hint_forecast)).assertIsDisplayed()
        } finally {
            AssistantPrefs.setEnabled(context, wasEnabled)
            AssistantPrefs.setHints(context, hadHints)
            AssistantPrefs.setAnimations(context, hadAnimations)
            AssistantPrefs.setPosition(context, oldX, oldY)
        }
    }
}

package ru.astrosmap.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import ru.astrosmap.app.BuildConfig
import ru.astrosmap.app.R
import ru.astrosmap.app.ui.theme.AstroTheme
import ru.astrosmap.app.ui.tools.ReportScaffold
import ru.astrosmap.app.ui.tools.ReportState

/** Synthetic fixtures exercise client behavior without copying private editorial content. */
class ReportUiSmokeTest {
    @get:Rule val compose = createComposeRule()
    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun offlineReportOffersWorkingRetry() {
        var retries = 0
        compose.setContent { AstroTheme { ReportScaffold(ReportState.Offline, { retries++ }) {} } }
        compose.onNodeWithText(label(R.string.net_error)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.retry)).performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun readyReportRendersReturnedContent() {
        val payload = buildJsonObject { put("summary", "UI test report") }
        compose.setContent {
            AstroTheme { ReportScaffold(ReportState.Ready(payload), {}) { Text(it["summary"].toString()) } }
        }
        compose.onNodeWithText("\"UI test report\"").assertIsDisplayed()
    }

    @Test fun reportGateMatchesStorePurchasePolicy() {
        compose.setContent { AstroTheme { ReportScaffold(ReportState.NeedPremium, {}) {} } }
        compose.onNodeWithText(label(R.string.premium_required)).assertIsDisplayed()
        val purchase = compose.onNodeWithText(label(R.string.premium_buy))
        if (BuildConfig.SHOW_EXTERNAL_PURCHASE_LINKS) purchase.assertIsDisplayed() else purchase.assertDoesNotExist()
    }

    @Test fun interpretationModesRemainReachableAtDoubleFontSize() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                AstroTheme {
                    var mode by remember { mutableStateOf(InterpretationMode.BRIEF) }
                    InterpretationModeSelector(mode, { mode = it })
                }
            }
        }
        compose.onNodeWithText(label(R.string.interpretation_technical)).performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText(label(R.string.interpretation_brief)).performScrollTo().performClick().assertIsSelected()
    }
}

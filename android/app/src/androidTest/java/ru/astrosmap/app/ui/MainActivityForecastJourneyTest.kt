package ru.astrosmap.app.ui

import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import ru.astrosmap.app.BuildConfig
import ru.astrosmap.app.MainActivity
import ru.astrosmap.app.R
import ru.astrosmap.app.data.CityStore

/** Opt-in real-API acceptance test. Uses the real form, city asset, Room and API.
 * Run with networkE2e=true while the local server is listening on host port8000.
 * Only the uniquely named synthetic chart created here is removed afterwards.
 */
class MainActivityForecastJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun label(id: Int) = compose.activity.getString(id)
    private fun awaitText(text: String) {
        compose.waitUntil(90_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun capture(name: String) {
        val directory = File(compose.activity.filesDir, "qa-evidence").apply { mkdirs() }
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun createSaveAndReadRealForecastWithLatestPeriodWinning() {
        assumeTrue("Requires explicitly enabled local API test", InstrumentationRegistry.getArguments().getString("networkE2e") == "true")
        assumeTrue("Must never run against production", BuildConfig.BASE_URL == "http://10.0.2.2:8000/")
        val context = compose.activity
        val name = "QA-forecast-" + UUID.randomUUID().toString()
        println("QA_CHART_NAME=$name")
        try {
            awaitText(label(R.string.section_chart))
            compose.onNodeWithText(label(R.string.section_chart)).performClick()
            compose.onNodeWithText(label(R.string.form_name)).performScrollTo().performTextReplacement(name)
            // Exercise the actual date dialog. Its documented empty-form default is Jan1 1990.
            compose.onNodeWithText(label(R.string.form_date)).performTouchInput { click() }
            compose.onNodeWithText(label(R.string.ok)).performClick()
            compose.onNodeWithText(label(R.string.form_city)).performScrollTo().performTextReplacement("Moscow")
            val city = CityStore(context).search("Moscow").first { it.nameEn == "Moscow" }
            val cityLabel = city.label(AstroLabels.isRu())
            awaitText(cityLabel)
            compose.onNodeWithText(cityLabel).performScrollTo().performClick()
            compose.onNodeWithText(label(R.string.form_calculate)).performScrollTo().performClick()
            awaitText(label(R.string.forecast_chart_open))
            // Saving may already have occurred for an installation with no charts.
            if (compose.onAllNodesWithContentDescription(label(R.string.save)).fetchSemanticsNodes().isNotEmpty()) {
                compose.onNodeWithContentDescription(label(R.string.save)).performClick()
                awaitText(label(R.string.forecast_chart_open))
            }
            compose.onNodeWithText(label(R.string.forecast_chart_open)).performScrollTo().performClick()
            compose.waitUntil(90_000) { compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty() }



            // Three actual chip callbacks queued in the same UI frame reproduce fast input
            // before recomposition removes the ready report. Last requested period must win.
            val periodLabels = listOf(R.string.forecast_period_next_week, R.string.forecast_period_next_month, R.string.forecast_period_this_month)
            val actions = periodLabels.map {
                compose.onNodeWithText(label(it)).fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
            }
            compose.runOnUiThread { actions.forEach { it() } }
            val today = LocalDate.now()
            val start = today.withDayOfMonth(1)
            val end = start.plusMonths(1).minusDays(1)
            val title = label(R.string.tools_forecast) + " · $start — $end"
            compose.waitUntil(90_000) { compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(title))
            compose.onNodeWithText(title).performScrollTo().assertIsDisplayed()
            capture("forecast-overview.png")
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label(R.string.forecast_interpretation_label)))
            compose.onAllNodesWithText(label(R.string.forecast_interpretation_label))[0].assertExists()
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label(R.string.forecast_advice_label)))
            compose.onAllNodesWithText(label(R.string.forecast_advice_label))[0].assertExists()
            capture("forecast-advice.png")
            // Confirm persistence and that auto/manual save did not insert duplicates.
            SQLiteDatabase.openDatabase(context.getDatabasePath("charts.db").absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT COUNT(*) FROM charts WHERE name = ?", arrayOf(name)).use { cursor ->
                    cursor.moveToFirst()
                    assertEquals(1, cursor.getInt(0))
                }
            }
        } finally {
            val file = context.getDatabasePath("charts.db")
            if (InstrumentationRegistry.getArguments().getString("retainQa") != "true" && file.exists()) SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.delete("charts", "name = ?", arrayOf(name))
            }
        }
    }
}

/** Exact-name cleanup for the intentionally retained synthetic acceptance chart. */
class RetainedQaCleanupTest {
    @Test fun removeOnlyExplicitSyntheticChart() {
        val name = InstrumentationRegistry.getArguments().getString("cleanupQa")
        assumeTrue("Requires exact synthetic chart name", name?.matches(Regex("QA-forecast-[0-9a-f-]{36}")) == true)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SQLiteDatabase.openDatabase(context.getDatabasePath("charts.db").absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            val deleted = db.delete("charts", "name = ?", arrayOf(name!!))
            assertEquals(1, deleted)
        }
    }
}

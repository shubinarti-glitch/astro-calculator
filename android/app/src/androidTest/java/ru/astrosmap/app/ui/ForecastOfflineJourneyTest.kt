package ru.astrosmap.app.ui

import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.provider.Settings
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import ru.astrosmap.app.BuildConfig
import ru.astrosmap.app.MainActivity
import ru.astrosmap.app.R

/** Operates only on the exact synthetic card named by the acceptance runner. */
class ForecastOfflineJourneyTest {
    @get:Rule val compose = AndroidComposeTestRule(
        ActivityScenarioRule<MainActivity>(launchIntent()),
        activityProvider = { rule ->
            var activity: MainActivity? = null
            rule.scenario.onActivity { activity = it }
            checkNotNull(activity)
        },
    )

    companion object {
        private fun launchIntent(): Intent = Intent(
            InstrumentationRegistry.getInstrumentation().targetContext,
            MainActivity::class.java,
        )
    }
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun label(id: Int) = compose.activity.getString(id)
    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .use { it.bufferedReader().readText() }
    private fun capture(name: String) {
        val directory = File(instrumentation.targetContext.filesDir, "qa-evidence").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    private fun waitForText(text: String, substring: Boolean = false) {
        compose.waitUntil(90_000) {
            compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }
    }
    /** Follow the same route as a person; notification destinations remain production-restricted. */
    private fun openExactForecast(name: String) {
        check(name.matches(Regex("QA-forecast-[0-9a-f-]{36}")))
        val context = instrumentation.targetContext
        SQLiteDatabase.openDatabase(context.getDatabasePath("charts.db").absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT id FROM charts WHERE name = ?", arrayOf(name)).use { cursor ->
                check(cursor.moveToFirst()) { "Synthetic acceptance chart missing" }
                check(!cursor.moveToNext()) { "Synthetic acceptance chart duplicated" }
            }
        }
        waitForText(label(R.string.nav_cards_short))
        compose.onNodeWithText(label(R.string.nav_cards_short)).performClick()
        waitForText(label(R.string.search))
        compose.onNode(androidx.compose.ui.test.hasSetTextAction()).performClick()
        compose.waitUntil(15_000) {
            androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
        }
        compose.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextReplacement(name)
        val card = hasText(name) and !androidx.compose.ui.test.hasSetTextAction()
        compose.waitUntil(90_000) { compose.onAllNodes(card).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(card).performScrollTo().performClick()
        waitForText(label(R.string.forecast_chart_open))
        compose.waitUntil(5_000) {
            androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) != true
        }
        compose.onNodeWithText(label(R.string.forecast_chart_open)).performScrollTo().performClick()
        compose.waitUntil(90_000) {
            compose.onAllNodesWithText(label(R.string.forecast_period_this_month)).fetchSemanticsNodes().isNotEmpty()
        }
        waitForText(label(R.string.tools_forecast) + " · ", substring = true)
    }

    private fun selectPeriod(labelRes: Int) {
        compose.onNodeWithText(label(labelRes)).performScrollTo().performClick()
    }

    @Test fun cachedForecastRecoversAfterUncachedPeriod() {
        val name = InstrumentationRegistry.getArguments().getString("qaName")
        assumeTrue("Explicit synthetic card is required", name?.matches(Regex("QA-forecast-[0-9a-f-]{36}")) == true)
        assumeTrue("Local API only", BuildConfig.BASE_URL == "http://10.0.2.2:8000/")
        val context = instrumentation.targetContext
        val resolver = context.contentResolver
        val previousFont = Settings.System.getFloat(resolver, Settings.System.FONT_SCALE, 1f)
        val wifi = Settings.Global.getInt(resolver, "wifi_on", 1) != 0
        val mobile = Settings.Global.getInt(resolver, "mobile_data", 1) != 0
        val previousSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1)
        try {
            openExactForecast(name!!)
            // The actual saved-chart and forecast controls opened the synthetic report.
            compose.waitUntil(90_000) { compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label(R.string.forecast_interpretation_label)))
            capture("forecast-live.png")
            // The cache banner is the first lazy item, outside the interpretation viewport.
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
            shell("svc wifi disable")
            shell("svc data disable")
            selectPeriod(R.string.forecast_period_this_month)
            val cachedPrefix = label(R.string.forecast_cached_at).substringBefore('%').trim()
            waitForText(cachedPrefix, true)
            capture("forecast-offline.png")
            selectPeriod(R.string.forecast_period_tomorrow)
            waitForText(label(R.string.net_error))
            // The period selector must survive the error and allow a cached-period recovery.
            selectPeriod(R.string.forecast_period_this_month)
            waitForText(cachedPrefix, true)

        } finally {
            shell("settings put system font_scale $previousFont")
            shell("wm size ${previousSize ?: "reset"}")
            shell("svc wifi ${if (wifi) "enable" else "disable"}")
            shell("svc data ${if (mobile) "enable" else "disable"}")
        }
    }

    /** Window/theme are configured before launching the activity, avoiding live-resize test races. */
    @Test fun cachedForecastInConfiguredWindow() {
        val arguments = InstrumentationRegistry.getArguments()
        val name = arguments.getString("qaName")
        val captureName = arguments.getString("captureName")
        assumeTrue(name?.matches(Regex("QA-forecast-[0-9a-f-]{36}")) == true)
        assumeTrue(captureName in setOf("forecast-small-screen.png", "forecast-light-theme.png"))
        assumeTrue(BuildConfig.BASE_URL == "http://10.0.2.2:8000/")
        openExactForecast(name!!)
        compose.waitUntil(90_000) { compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty() }
        val cachedPrefix = label(R.string.forecast_cached_at).substringBefore('%').trim()
        waitForText(cachedPrefix, true)
        if (captureName == "forecast-small-screen.png") {
            org.junit.Assert.assertEquals(840f, compose.onRoot().fetchSemanticsNode().boundsInRoot.width, 1f)
            org.junit.Assert.assertTrue("Configured large-text test requires 200% font",
                compose.activity.resources.configuration.fontScale >= 1.9f)
        } else {
            org.junit.Assert.assertEquals(android.content.res.Configuration.UI_MODE_NIGHT_NO,
                compose.activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK)
            org.junit.Assert.assertTrue("Light UI needs dark status bar icons",
                androidx.core.view.WindowCompat.getInsetsController(compose.activity.window,
                    compose.activity.window.decorView).isAppearanceLightStatusBars)
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label(R.string.forecast_advice_label)))
        capture(captureName!!)
    }

}

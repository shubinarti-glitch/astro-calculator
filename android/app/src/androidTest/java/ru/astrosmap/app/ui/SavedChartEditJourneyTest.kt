package ru.astrosmap.app.ui

import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.platform.app.InstrumentationRegistry
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import ru.astrosmap.app.BuildConfig
import ru.astrosmap.app.MainActivity
import ru.astrosmap.app.R
import ru.astrosmap.app.data.CityStore
import ru.astrosmap.app.data.TokenStore

/** Opt-in local-API regression. Only this test's unique guest chart is read or removed.
 * Run with networkE2e=true, localhost API on host:8000, and a logged-out QA emulator.
 * Uses the real Activity, form, city database, engine, Room and explicit Save/Forecast actions.
 */
class SavedChartEditJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun label(id: Int) = compose.activity.getString(id)
    private fun awaitText(text: String) {
        compose.waitUntil(90_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun awaitIcon(id: Int) {
        compose.waitUntil(90_000) {
            compose.onAllNodesWithContentDescription(label(id)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun savedChartEditRequiresSaveAndBothCommitActionsKeepOriginalIdentity() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("networkE2e") == "true")
        assumeTrue("Local API only", BuildConfig.BASE_URL == "http://10.0.2.2:8000/")
        assumeTrue("Guest fixture only; do not modify QA account A/B", runBlocking { TokenStore(compose.activity).get() == null })
        val context = compose.activity
        val originalName = "QA-edit-" + UUID.randomUUID()
        val savedName = originalName + "-saved"
        val forecastName = originalName + "-forecast"
        val names = arrayOf(originalName, savedName, forecastName)
        var fixtureId: String? = null
        var fixtureClientId: String? = null

        // Hash cached prose in memory: assertion failures must never print private report text.
        fun rows(): List<Map<String, String?>> {
            val file = context.getDatabasePath("charts.db")
            if (!file.exists()) return emptyList()
            return SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT * FROM charts WHERE ownerKey = 'guest' AND name IN (?, ?, ?)", names).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) add(cursor.columnNames.associateWith { column ->
                            val index = cursor.getColumnIndexOrThrow(column)
                            if (cursor.isNull(index)) null else {
                                val value = cursor.getString(index)
                                if (column == "textsJson") MessageDigest.getInstance("SHA-256")
                                    .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
                                else value
                            }
                        })
                    }
                }
            }
        }
        fun openSaved(name: String) {
            compose.onNodeWithText(label(R.string.nav_cards_short)).performClick()
            awaitText(name)
            compose.onNodeWithText(name).performScrollTo().assertIsDisplayed().performClick()
            awaitText(label(R.string.forecast_chart_open))
        }
        fun editAndCalculate(newName: String): Map<String, String?> {
            compose.onNodeWithContentDescription(label(R.string.edit)).assertIsDisplayed().performClick()
            compose.onNodeWithText(label(R.string.form_name)).performScrollTo().performTextReplacement(newName)
            val before = rows().single()
            compose.onNodeWithText(label(R.string.form_calculate)).performScrollTo().assertIsDisplayed().performClick()
            awaitIcon(R.string.save)
            compose.onNodeWithContentDescription(label(R.string.save)).assertIsDisplayed()
            compose.onNodeWithContentDescription(label(R.string.delete)).assertDoesNotExist()
            // A newly rendered interpretation proves the edited natal request completed.
            compose.waitUntil(90_000) {
                runCatching {
                    compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label(R.string.material_save)))
                    compose.onNodeWithText(label(R.string.material_save)).assertIsDisplayed()
                }.isSuccess
            }
            compose.waitForIdle()
            assertTrue("Original birth/name/cache row changed before explicit Save", rows().single() == before)
            return before
        }

        try {
            awaitText(label(R.string.section_chart))
            compose.onNodeWithText(label(R.string.section_chart)).performClick()
            compose.onNodeWithText(label(R.string.form_name)).performScrollTo().performTextReplacement(originalName)
            compose.onNodeWithText(label(R.string.form_date)).performTouchInput { click() }
            compose.onNodeWithText(label(R.string.ok)).performClick()
            compose.onNodeWithText(label(R.string.form_city)).performScrollTo().performTextReplacement("Moscow")
            val city = CityStore(context).search("Moscow").first { it.nameEn == "Moscow" }
            awaitText(city.label(AstroLabels.isRu()))
            compose.onNodeWithText(city.label(AstroLabels.isRu())).performScrollTo().performClick()
            compose.onNodeWithText(label(R.string.form_calculate)).performScrollTo().performClick()
            awaitText(label(R.string.forecast_chart_open))
            if (compose.onAllNodesWithContentDescription(label(R.string.save)).fetchSemanticsNodes().isNotEmpty()) {
                compose.onNodeWithContentDescription(label(R.string.save)).performClick()
            }
            awaitIcon(R.string.delete)
            compose.waitUntil(90_000) { rows().size == 1 }
            val initial = rows().single()
            fixtureId = initial["id"]
            fixtureClientId = initial["clientId"]
            assertNotNull(fixtureId); assertNotNull(fixtureClientId)

            openSaved(originalName)
            val beforeSave = editAndCalculate(savedName)
            assertEquals(originalName, beforeSave["name"])
            compose.onNodeWithContentDescription(label(R.string.save)).performClick()
            awaitIcon(R.string.delete)
            compose.waitUntil(90_000) { rows().singleOrNull()?.get("name") == savedName }
            val firstSave = rows().single()
            assertEquals(fixtureId, firstSave["id"])
            assertEquals(fixtureClientId, firstSave["clientId"])
            assertEquals(1, rows().size)

            openSaved(savedName)
            val beforeForecast = editAndCalculate(forecastName)
            assertEquals(savedName, beforeForecast["name"])
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label(R.string.forecast_chart_open)))
            compose.onNodeWithText(label(R.string.forecast_chart_open)).performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(90_000) { rows().singleOrNull()?.get("name") == forecastName }
            awaitText(label(R.string.forecast_period_this_month))
            val secondSave = rows().single()
            assertEquals(fixtureId, secondSave["id"])
            assertEquals(fixtureClientId, secondSave["clientId"])
            assertEquals(1, rows().size)
        } finally {
            val file = context.getDatabasePath("charts.db")
            if (file.exists()) SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                if (fixtureId != null && fixtureClientId != null) {
                    db.delete("charts", "id = ? AND clientId = ? AND ownerKey = 'guest' AND name IN (?, ?, ?)",
                        arrayOf(fixtureId!!, fixtureClientId!!, *names))
                } else {
                    db.delete("charts", "ownerKey = 'guest' AND name IN (?, ?, ?)", names)
                }
            }
        }
    }
}

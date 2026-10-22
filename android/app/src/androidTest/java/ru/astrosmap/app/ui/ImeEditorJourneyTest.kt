package ru.astrosmap.app.ui

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import ru.astrosmap.app.MainActivity
import ru.astrosmap.app.R
import ru.astrosmap.app.data.CityStore
import ru.astrosmap.app.data.TokenStore

/** Opt-in on the dedicated guest QA device. Keyboard remains real and visible during assertions. */
class ImeEditorJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun label(id: Int) = compose.activity.getString(id)
    private fun awaitText(text: String) = compose.waitUntil(45_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    private fun safeDevice() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("imeE2e") == "true")
        assumeTrue(compose.activity.packageName.endsWith(".debug"))
        assumeTrue(runBlocking { TokenStore(compose.activity).get() == null })
    }
    private fun imeVisible(): Boolean = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
        ?.isVisible(WindowInsetsCompat.Type.ime()) == true

    private fun assertAboveKeyboard(node: SemanticsNodeInteraction) {
        compose.waitUntil(15_000) { imeVisible() }
        compose.waitUntil(15_000) {
            val bounds = node.fetchSemanticsNode().boundsInWindow
            val view = compose.activity.window.decorView
            val bottom = ViewCompat.getRootWindowInsets(view)!!.getInsets(WindowInsetsCompat.Type.ime()).bottom
            bounds.top >= 0f && bounds.bottom <= view.height - bottom + 1f
        }
        assertTrue("Real soft keyboard must be visible, never disabled for this test", imeVisible())
        node.assertIsDisplayed()
    }
    private fun physicalTap(node: SemanticsNodeInteraction) {
        val bounds = node.fetchSemanticsNode().boundsInWindow
        val location = IntArray(2)
        compose.activity.window.decorView.getLocationOnScreen(location)
        val x = location[0] + bounds.center.x
        val y = location[1] + bounds.center.y
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(downTime, downTime + 60, MotionEvent.ACTION_UP, x, y, 0)
        try {
            assertTrue(automation.injectInputEvent(down, true))
            assertTrue(automation.injectInputEvent(up, true))
        } finally { down.recycle(); up.recycle() }
    }

    @Test fun cityEditorAndFirstSuggestionRemainAboveRealImeAndPhysicalTapSelects() {
        safeDevice()
        awaitText(label(R.string.section_chart))
        compose.onNodeWithText(label(R.string.section_chart)).performClick()
        val editor = compose.onNodeWithTag("chart-city-editor")
        editor.performScrollTo().performTouchInput { click() }
        compose.waitUntil(15_000) { imeVisible() }
        editor.performTextReplacement("Mos")
        editor.performTextReplacement("Mosc")
        editor.performTextReplacement("Moscow")
        val expected = CityStore(compose.activity).search("Moscow").first().label(AstroLabels.isRu())
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("chart-city-first-suggestion").fetchSemanticsNodes().isNotEmpty() }
        val first = compose.onNodeWithTag("chart-city-first-suggestion")
        assertAboveKeyboard(editor)
        assertAboveKeyboard(first)
        // A semantics click would bypass the keyboard obstruction this regression is about.
        physicalTap(first)
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("chart-city-first-suggestion").fetchSemanticsNodes().isEmpty() }
        editor.assertTextContains(expected)
        // Beyond the actual 200ms search debounce: a stale result must not reopen the list.
        SystemClock.sleep(600)
        compose.waitForIdle()
        compose.onNodeWithTag("chart-city-first-suggestion").assertDoesNotExist()
        editor.assertTextContains(expected)
        compose.waitUntil(15_000) { !imeVisible() }
        compose.onNodeWithText(label(R.string.form_calculate)).performScrollTo().assertIsDisplayed()
    }

    @Test fun forecastLocationEditorAndFirstResultRemainAboveRealIme() {
        safeDevice()
        awaitText(label(R.string.section_tools))
        compose.onNodeWithText(label(R.string.section_tools)).performClick()
        val editor = compose.onNodeWithTag("tools-city-editor")
        editor.performScrollTo().performTouchInput { click() }
        compose.waitUntil(15_000) { imeVisible() }
        editor.performTextReplacement("Moscow")
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("tools-city-first-suggestion").fetchSemanticsNodes().isNotEmpty() }
        assertAboveKeyboard(editor)
        assertAboveKeyboard(compose.onNodeWithTag("tools-city-first-suggestion"))
        // Do not select: this check must not change the persisted forecast location.
    }

    @Test fun journalNoteCanBeEditedAboveRealImeWithoutSavingSyntheticText() {
        safeDevice()
        awaitText(label(R.string.section_tools))
        compose.onNodeWithText(label(R.string.section_tools)).performClick()
        val journal = "✎ " + label(R.string.journal_title)
        awaitText(journal)
        compose.onNodeWithText(journal).performScrollTo().performClick()
        awaitText(label(R.string.journal_note))
        val editor = compose.onNodeWithText(label(R.string.journal_note))
        editor.performScrollTo().performTouchInput { click() }
        compose.waitUntil(15_000) { imeVisible() }
        editor.performTextReplacement("Synthetic unsaved keyboard check")
        assertAboveKeyboard(editor)
        editor.assertTextContains("Synthetic unsaved keyboard check")
    }
}

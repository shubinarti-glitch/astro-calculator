package ru.astrosmap.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import ru.astrosmap.app.R
import ru.astrosmap.app.ui.assistant.AssistantCharacter
import ru.astrosmap.app.ui.assistant.AssistantPhase
import ru.astrosmap.app.ui.assistant.AssistantPrefs
import ru.astrosmap.app.ui.assistant.AssistantUiState
import ru.astrosmap.app.ui.assistant.FloatingAssistant
import ru.astrosmap.app.ui.assistant.PublishAssistantState
import ru.astrosmap.app.ui.theme.AstroTheme

class AssistantPetUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("astro_assistant", Context.MODE_PRIVATE)
    private var originalPrefs: Map<String, *> = emptyMap<String, Any>()
    private val hint = R.string.assistant_hint_forecast
    private val action = R.string.assistant_open_diary_v176
    private fun label(id: Int) = context.getString(id)
    private fun pet() = compose.onNodeWithContentDescription(label(AssistantCharacter.STAR.nameRes))
    private fun close() = compose.onNodeWithText(label(R.string.assistant_hint_close_v177))

    @Before fun saveSettings() {
        originalPrefs = prefs.all.toMap()
        prefs.edit().clear().putBoolean("enabled", true).putBoolean("hints", true)
            .putBoolean("animations", false).putString("character", "star")
            .putInt("size_dp", 88).putFloat("position_x", .7f)
            .putFloat("position_y", .8f).commit()
    }

    @After fun restoreSettings() {
        val editor = prefs.edit().clear()
        originalPrefs.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        editor.commit()
    }

    private fun state(phase: AssistantPhase = AssistantPhase.READY, onAction: () -> Unit = {}) =
        AssistantUiState(phase, hint, action, onAction)

    private fun show(uiState: AssistantUiState) {
        compose.setContent { AstroTheme { FloatingAssistant("forecast/1", uiState = uiState) } }
    }

    @Test fun replacedPublisherReceiverGetsCurrentStateWithoutPhaseChange() {
        val receiverKey = mutableStateOf(0)
        val events = mutableListOf<Pair<Int, AssistantPhase?>>()
        compose.setContent {
            val receiver = remember(receiverKey.value) {
                val key = receiverKey.value
                { state: AssistantUiState? ->
                    events.add(key to state?.phase)
                    Unit
                }
            }
            AstroTheme { PublishAssistantState(receiver, AssistantPhase.READY, hint) }
        }
        compose.runOnIdle {
            assertTrue(events.contains(0 to AssistantPhase.READY))
            receiverKey.value = 1
        }
        compose.runOnIdle {
            assertTrue("A new receiver needs the current state even when the phase is unchanged",
                events.contains(1 to AssistantPhase.READY))
        }
    }

    @Test fun explicitActionRunsOnceAndClosesHint() {
        var actions = 0
        show(state { actions++ })
        compose.onNodeWithText(label(hint)).assertDoesNotExist()
        pet().performClick()
        compose.onNodeWithText(label(action)).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(1, actions)
        compose.onNodeWithText(label(hint)).assertDoesNotExist()
        close().assertDoesNotExist()
    }

    @Test fun hintCloudUsesItsOwnMeasuredSizeAndStaysInsideViewport() {
        show(state())
        pet().performClick()
        val viewport = compose.onRoot().getUnclippedBoundsInRoot()
        val cloud = compose.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite),
            useUnmergedTree = true,
        ).getUnclippedBoundsInRoot()
        val tolerance = 1f
        assertTrue("Cloud must have its own width, not inherit the viewport minimum",
            (cloud.right - cloud.left).value > 0f && (cloud.right - cloud.left).value <= 232f + tolerance)
        assertTrue("Cloud must have a positive height bounded by the viewport",
            (cloud.bottom - cloud.top).value > 0f && (cloud.bottom - cloud.top).value <= (viewport.bottom - viewport.top).value + tolerance)
        assertTrue("Cloud's actual left edge must stay inside the viewport",
            cloud.left.value >= viewport.left.value - tolerance)
        assertTrue("Cloud's actual top edge must stay inside the viewport",
            cloud.top.value >= viewport.top.value - tolerance)
        assertTrue("Cloud's actual right edge must stay inside the viewport",
            cloud.right.value <= viewport.right.value + tolerance)
        assertTrue("Cloud's actual bottom edge must stay inside the viewport",
            cloud.bottom.value <= viewport.bottom.value + tolerance)
    }

    @Test fun explicitCloseDismissesWithoutRunningAction() {
        var actions = 0
        show(state { actions++ })
        pet().performClick()
        close().performScrollTo().assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals(0, actions)
        compose.onNodeWithText(label(hint)).assertDoesNotExist()
    }

    @Test fun loadingSavingAndErrorNeverOpenHintAutomatically() {
        val current = mutableStateOf(state(AssistantPhase.LOADING))
        compose.setContent { AstroTheme { FloatingAssistant("forecast/1", uiState = current.value) } }
        listOf(AssistantPhase.LOADING, AssistantPhase.SAVING, AssistantPhase.ERROR).forEach { phase ->
            compose.runOnIdle { current.value = state(phase) }
            pet().assertIsDisplayed()
            compose.onNodeWithText(label(hint)).assertDoesNotExist()
            close().assertDoesNotExist()
        }
        pet().performClick()
        compose.onNodeWithText(label(hint)).assertIsDisplayed()
    }

    @Test fun draggingClosesHintAndNeverRunsAction() {
        var actions = 0
        show(state { actions++ })
        pet().performClick()
        val before = pet().fetchSemanticsNode().boundsInRoot.center
        pet().performTouchInput { swipe(center, center + Offset(-90f, -90f), 500) }
        compose.waitForIdle()
        val after = pet().fetchSemanticsNode().boundsInRoot.center
        assertTrue("The pet should follow a drag", (after - before).getDistance() > 30f)
        assertEquals(0, actions)
        compose.onNodeWithText(label(hint)).assertDoesNotExist()
        pet().performClick()
        compose.onNodeWithText(label(hint)).assertIsDisplayed()
        assertEquals(0, actions)
    }

    @Test fun temporaryViewportShrinkPreservesSavedAnchorAndRestoresPosition() {
        val shrunk = mutableStateOf(false)
        compose.setContent {
            AstroTheme {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().height(maxHeight * if (shrunk.value) .55f else 1f)) {
                        FloatingAssistant("forecast/1", uiState = state())
                    }
                }
            }
        }
        val original = pet().fetchSemanticsNode().boundsInRoot.center
        val savedX = prefs.getFloat("position_x", -1f)
        val savedY = prefs.getFloat("position_y", -1f)
        compose.runOnIdle { shrunk.value = true }
        val compact = pet().fetchSemanticsNode().boundsInRoot.center
        assertTrue("The pet must move into the shorter viewport", compact.y < original.y)
        assertEquals(savedX, prefs.getFloat("position_x", -1f), 0f)
        assertEquals(savedY, prefs.getFloat("position_y", -1f), 0f)
        compose.runOnIdle { shrunk.value = false }
        val restored = pet().fetchSemanticsNode().boundsInRoot.center
        assertEquals(original.x, restored.x, 1f)
        assertEquals(original.y, restored.y, 1f)
        assertEquals(savedY, prefs.getFloat("position_y", -1f), 0f)
    }

    @Test fun doubleFontKeepsActionAndCloseReachableByScrolling() {
        var actions = 0
        AssistantPrefs.setPosition(context, .7f, 1f)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                AstroTheme { FloatingAssistant("forecast/1", uiState = state { actions++ }) }
            }
        }
        pet().performClick()
        close().performScrollTo().assertIsDisplayed().performClick()
        assertEquals(0, actions)
        pet().performClick()
        compose.onNodeWithText(label(action)).performScrollTo().assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals(1, actions)
        close().assertDoesNotExist()
    }

    @Test fun shortNarrowWindowKeepsCloseReachableAtDoubleFont() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                AstroTheme {
                    Box(Modifier.size(width = 274.dp, height = 180.dp)) {
                        FloatingAssistant("forecast/1", uiState = state())
                    }
                }
            }
        }
        pet().performClick()
        close().performScrollTo().assertIsDisplayed().performClick()
        compose.waitForIdle()
        close().assertDoesNotExist()
    }

    @Test fun disabledHintsStayClosedAndDisabledAnimationsKeepPetStill() {
        AssistantPrefs.setHints(context, false)
        var actions = 0
        show(state { actions++ })
        pet().performClick()
        compose.onNodeWithText(label(hint)).assertDoesNotExist()
        assertEquals(0, actions)
        val before = pet().fetchSemanticsNode().boundsInRoot
        compose.mainClock.advanceTimeBy(2_000)
        val after = pet().fetchSemanticsNode().boundsInRoot
        assertEquals(before, after)
        compose.runOnIdle { AssistantPrefs.setHints(context, true) }
        pet().performClick()
        compose.onNodeWithText(label(hint)).assertIsDisplayed()
    }

    @Test fun nonFiniteStoredPositionUsesSafeDefaults() {
        prefs.edit().putFloat("position_x", Float.NaN)
            .putFloat("position_y", Float.POSITIVE_INFINITY).commit()
        assertEquals(.92f, AssistantPrefs.positionX(context), 0f)
        assertEquals(.72f, AssistantPrefs.positionY(context), 0f)
        show(state())
        pet().assertIsDisplayed()
        val center = pet().fetchSemanticsNode().boundsInRoot.center
        assertTrue(center.x.isFinite() && center.y.isFinite())
        pet().performClick()
        close().performScrollTo().assertIsDisplayed()
    }

    @Test fun allCharactersAndSizesKeepHintsAndCloseUsable() {
        show(state())
        AssistantCharacter.entries.forEach { character ->
            listOf(64, 88, 104).forEach { size ->
                compose.runOnIdle {
                    AssistantPrefs.setCharacter(context, character)
                    AssistantPrefs.setSize(context, size)
                }
                compose.onNodeWithContentDescription(label(character.nameRes))
                    .assertIsDisplayed().performClick()
                compose.onNodeWithText(label(hint)).assertIsDisplayed()
                close().performScrollTo().assertIsDisplayed().performClick()
                compose.waitForIdle()
                compose.onNodeWithText(label(hint)).assertDoesNotExist()
            }
        }
    }
}

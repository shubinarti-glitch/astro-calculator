package ru.astrosmap.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
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
import ru.astrosmap.app.ui.assistant.AssistantSettings
import ru.astrosmap.app.ui.assistant.AssistantAccent
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

    @Test fun disabledHintsStayClosedAndCanBeEnabledAgain() {
        AssistantPrefs.setHints(context, false)
        var actions = 0
        show(state { actions++ })
        pet().performClick()
        compose.onNodeWithText(label(hint)).assertDoesNotExist()
        assertEquals(0, actions)
        compose.runOnIdle { AssistantPrefs.setHints(context, true) }
        pet().performClick()
        compose.onNodeWithText(label(hint)).assertIsDisplayed()
    }

    @Test fun legacyDisabledAnimationPreferenceDoesNotStopMotion() {
        assertTrue(!prefs.getBoolean("animations", true))
        // Compose cancels infinite animations if automatic time advancement starts them.
        compose.mainClock.autoAdvance = false
        show(state())
        compose.mainClock.advanceTimeByFrame()
        val before = pet().fetchSemanticsNode().boundsInRoot.center
        compose.mainClock.advanceTimeBy(650)
        val after = pet().fetchSemanticsNode().boundsInRoot.center
        assertTrue("Legacy static-image setting must not stop the pet animation",
            (after - before).getDistance() > .1f)
    }

    @Test fun settingsKeepCharacterSizeAndHintsWithoutAnimationSwitch() {
        compose.setContent { AstroTheme { Column { AssistantSettings(context) } } }
        compose.onNodeWithText(label(R.string.assistant_animations)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.assistant_show)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.assistant_hints)).assertIsDisplayed()
        AssistantCharacter.entries.forEach { character ->
            compose.onNodeWithContentDescription(label(character.nameRes)).assertIsDisplayed()
        }
    }

    @Test fun livePreviewsSelectEachCharacterAndKeepDifferentMotion() {
        compose.mainClock.autoAdvance = false
        compose.setContent { AstroTheme { Column { AssistantSettings(context) } } }
        compose.mainClock.advanceTimeByFrame()
        val before = AssistantCharacter.entries.map { character ->
            compose.onNodeWithContentDescription(label(character.nameRes), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        }
        compose.mainClock.advanceTimeBy(650)
        val distances = AssistantCharacter.entries.mapIndexed { index, character ->
            val node = compose.onNodeWithContentDescription(label(character.nameRes), useUnmergedTree = true)
            val after = node.fetchSemanticsNode().boundsInRoot
            (after.topLeft - before[index].topLeft).getDistance()
        }
        assertTrue("Each preview should move", distances.all { it > .1f })
        assertTrue("Character movement should have different timing", distances.max() - distances.min() > .1f)
        AssistantCharacter.entries.forEach { character ->
            val node = compose.onNodeWithContentDescription(label(character.nameRes))
            node.performClick()
            compose.mainClock.advanceTimeByFrame()
            node.assertIsSelected()
            assertEquals(character, AssistantPrefs.character(context))
        }
    }

    @Test fun eachAccentAppearsBrieflyAndFullyDisappears() {
        val character = mutableStateOf(AssistantCharacter.STAR)
        val progress = mutableStateOf(.5f)
        compose.setContent {
            AssistantAccent(character.value, progress,
                Modifier.size(88.dp).background(Color.Black).testTag("accent"))
        }
        fun litPixels(): Int {
            val image = compose.onNodeWithTag("accent").captureToImage().toPixelMap()
            var count = 0
            for (y in 0 until image.height) for (x in 0 until image.width) {
                val pixel = image[x, y]
                if (pixel.red + pixel.green + pixel.blue > .02f) count++
            }
            return count
        }
        AssistantCharacter.entries.forEach { value ->
            compose.runOnIdle { character.value = value; progress.value = .5f }
            assertTrue("Accent should be visible for $value", litPixels() > 0)
            compose.runOnIdle { progress.value = 1f }
            assertEquals("Accent must disappear completely", 0, litPixels())
            compose.runOnIdle { progress.value = 0f }
            assertEquals(0, litPixels())
        }
    }

    @Test fun doubleFontSettingsKeepCharactersAndSizesInsideNarrowWindow() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                AstroTheme {
                    Box(Modifier.size(274.dp, 400.dp).testTag("settings-window")) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            AssistantSettings(context)
                        }
                    }
                }
            }
        }
        val viewport = compose.onNodeWithTag("settings-window").getUnclippedBoundsInRoot()
        val labels = AssistantCharacter.entries.map { it.nameRes } + listOf(
            R.string.assistant_size_small, R.string.assistant_size_medium, R.string.assistant_size_large)
        labels.forEach { id ->
            val node = compose.onNodeWithText(label(id))
            node.performScrollTo().assertIsDisplayed()
            val bounds = node.getUnclippedBoundsInRoot()
            assertTrue("Setting label must fit the narrow window",
                bounds.left.value >= viewport.left.value - 1f && bounds.right.value <= viewport.right.value + 1f)
        }
    }

    @Test fun dragTiltsPetThenSettlesWithoutChangingTheChosenPosition() {
        compose.mainClock.autoAdvance = false
        show(state())
        compose.mainClock.advanceTimeByFrame()
        val before = pet().fetchSemanticsNode().boundsInRoot
        pet().performTouchInput { down(center); moveBy(Offset(80f, 0f), 100) }
        compose.mainClock.advanceTimeBy(350)
        val held = pet().fetchSemanticsNode().boundsInRoot
        assertTrue("Pet should lean while being dragged", held.width > before.width * 1.04f)
        pet().performTouchInput { up() }
        compose.mainClock.advanceTimeByFrame()
        val chosenX = prefs.getFloat("position_x", -1f)
        val chosenY = prefs.getFloat("position_y", -1f)
        compose.mainClock.advanceTimeBy(2_000)
        val settled = pet().fetchSemanticsNode().boundsInRoot
        assertEquals(held.center.x, settled.center.x, 5f)
        assertEquals(chosenX, prefs.getFloat("position_x", -1f), 0f)
        assertEquals(chosenY, prefs.getFloat("position_y", -1f), 0f)
        close().assertDoesNotExist()
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

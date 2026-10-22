package ru.astrosmap.app.ui.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantLayoutTest {
    private fun place(x: Float, y: Float, bubbleH: Float = 200f) = placeAssistantBubble(
        x, y, 88f, 232f, bubbleH, 360f, 640f, 8f, 8f,
    )

    @Test fun leftAndRightEdgesUseFreeHorizontalSpace() {
        val fromLeft = place(8f, 300f)
        val fromRight = place(264f, 300f)
        assertEquals(104f, fromLeft.x, 0f)
        assertFalse(fromLeft.tailOnRight)
        assertEquals(24f, fromRight.x, 0f)
        assertTrue(fromRight.tailOnRight)
        assertEquals(624f, fromLeft.maxHeight, 0f)
        assertTrue(fromLeft.x >= 8f + 88f + 8f)
        assertTrue(fromRight.x + 232f <= 264f - 8f)
    }

    @Test fun centeredPetUsesMoreSpaceBelowOrAbove() {
        val nearTop = place(136f, 8f, 900f)
        val nearBottom = place(136f, 544f, 900f)
        assertEquals(104f, nearTop.y, 0f)
        assertEquals(528f, nearTop.maxHeight, 0f)
        assertTrue(nearTop.tailAtTop)
        assertEquals(8f, nearBottom.y, 0f)
        assertEquals(528f, nearBottom.maxHeight, 0f)
        assertFalse(nearBottom.tailAtTop)
        assertTrue(nearTop.y >= 8f + 88f + 8f)
        assertTrue(nearBottom.y + nearBottom.maxHeight <= 544f - 8f)
    }

    @Test fun cornersAndOversizedTextRemainInsideViewport() {
        for (x in listOf(8f, 136f, 264f)) {
            for (y in listOf(8f, 276f, 544f)) {
                val result = place(x, y, 2_000f)
                val actualHeight = minOf(2_000f, result.maxHeight)
                assertTrue(result.x >= 8f)
                assertTrue(result.x + 232f <= 352f)
                assertTrue(result.y >= 8f)
                assertTrue(result.y + actualHeight <= 632f)
                val separated = result.x + 232f <= x - 8f || result.x >= x + 96f ||
                    result.y + actualHeight <= y - 8f || result.y >= y + 96f
                assertTrue("Bubble must not overlap the pet when free space exists", separated)
            }
        }
    }

    @Test fun measurementDoesNotChangeTheSelectedSideOrHeightLimit() {
        for (x in listOf(8f, 136f, 264f)) {
            for (y in listOf(8f, 276f, 544f)) {
                val initial = place(x, y, 0f)
                for (height in listOf(40f, 200f, 2_000f)) {
                    val measured = place(x, y, height)
                    assertEquals(initial.x, measured.x, 0f)
                    assertEquals(initial.maxHeight, measured.maxHeight, 0f)
                    assertEquals(initial.tailOnRight, measured.tailOnRight)
                }
            }
        }
    }

    @Test fun shortLandscapeWindowConstrainsScrollableBubble() {
        val result = placeAssistantBubble(276f, 8f, 88f, 232f, 800f, 640f, 112f, 8f, 8f)
        assertEquals(96f, result.maxHeight, 0f)
        assertEquals(8f, result.y, 0f)
        assertTrue(result.x + 232f <= 276f - 8f)
    }

    @Test fun tinyViewportStillProducesBoundedCoordinates() {
        val result = placeAssistantBubble(0f, 0f, 24f, 24f, 200f, 24f, 24f, 8f, 8f)
        assertEquals(8f, result.x, 0f)
        assertEquals(8f, result.y, 0f)
        assertEquals(8f, result.maxHeight, 0f)
        assertFalse(result.x.isNaN())
        assertFalse(result.y.isNaN())
    }

    @Test fun previousPositionAfterViewportShrinkCannotPutBubbleOutside() {
        val result = placeAssistantBubble(500f, 600f, 88f, 184f, 800f, 200f, 160f, 8f, 8f)
        assertTrue(result.x >= 8f)
        assertTrue(result.x + 184f <= 192f)
        assertTrue(result.y >= 8f)
        assertTrue(result.y + result.maxHeight <= 152f)
    }
}

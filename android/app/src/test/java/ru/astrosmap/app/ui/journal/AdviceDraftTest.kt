package ru.astrosmap.app.ui.journal

import org.junit.Assert.assertEquals
import org.junit.Test

class AdviceDraftTest {
    @Test fun sourceIsPreservedBesideAdviceWithoutNavigationTextEncoding() {
        val source = ForecastAdviceSource("Synthetic event", "2026-10-01", "2026-10-31")
        val draft = AdviceDraft(java.time.LocalDate.of(2026, 10, 21), "Synthetic advice", "guest", source)
        val block = sourcedAdvice(draft)
        org.junit.Assert.assertTrue(block.contains("Synthetic event"))
        org.junit.Assert.assertTrue(block.endsWith("Synthetic advice"))
        org.junit.Assert.assertTrue(source.heading(draft.date, java.util.Locale.US).contains("Oct 21, 2026"))
        assertEquals(block, appendAdvice(block, block))
        assertEquals("Synthetic advice", sourcedAdvice(draft.copy(source = null)))
    }
    @Test fun appendsWithoutDestroyingExistingNote() {
        assertEquals("My note\n\nSynthetic advice", appendAdvice("My note", " Synthetic advice "))
    }
    @Test fun repeatingTheSameActionDoesNotDuplicateText() {
        assertEquals("My note\n\nSynthetic advice", appendAdvice("My note\n\nSynthetic advice", "Synthetic advice"))
    }
    @Test fun blankAdvicePreservesOriginalIncludingWhitespace() {
        assertEquals("My note\n", appendAdvice("My note\n", "  "))
    }
    @Test fun aLongExistingNoteAndAdviceAreBothPreserved() {
        val note = "A".repeat(2000)
        val advice = "B".repeat(1000)
        assertEquals(note + "\n\n" + advice, appendAdvice(note, advice))
    }
}

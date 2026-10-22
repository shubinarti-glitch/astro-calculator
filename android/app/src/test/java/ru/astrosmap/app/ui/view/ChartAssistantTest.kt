package ru.astrosmap.app.ui.view

import org.junit.Assert.*
import org.junit.Test
import ru.astrosmap.app.R
import ru.astrosmap.app.ui.assistant.AssistantPhase

class ChartAssistantTest {
    @Test fun failedOperationOnSavedChartNeverOffersSave() {
        var invoked = false
        val result = chartAssistantUiState(ChartViewState(savedId = 7L, saveFailed = true),
            { invoked = true }, { invoked = true }, { invoked = true })
        assertEquals(AssistantPhase.ERROR, result.phase)
        assertEquals(R.string.assistant_chart_operation_error_v177, result.hintRes)
        assertNull(result.actionRes)
        assertNull(result.onAction)
        assertFalse(invoked)
    }

    @Test fun failedUnsavedWriteRetriesSaving() {
        var saved = false
        val result = chartAssistantUiState(ChartViewState(saveFailed = true),
            { fail("Must not calculate") }, { saved = true }, { fail("Must not navigate") })
        assertEquals(AssistantPhase.ERROR, result.phase)
        assertEquals(R.string.assistant_save_error_v177, result.hintRes)
        assertEquals(R.string.retry, result.actionRes)
        requireNotNull(result.onAction).invoke()
        assertTrue(saved)
    }

    @Test fun failedCalculationRetriesCalculation() {
        var retried = false
        val result = chartAssistantUiState(ChartViewState(calculationFailed = true),
            { retried = true }, { fail("Must not save") }, { fail("Must not navigate") })
        assertEquals(AssistantPhase.ERROR, result.phase)
        assertEquals(R.string.assistant_chart_error_v177, result.hintRes)
        assertEquals(R.string.retry, result.actionRes)
        requireNotNull(result.onAction).invoke()
        assertTrue(retried)
    }
}

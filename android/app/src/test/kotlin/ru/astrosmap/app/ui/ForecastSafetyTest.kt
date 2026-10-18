package ru.astrosmap.app.ui.tools
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
class ForecastSafetyTest {
    @Test fun explicitDenialRevokesGrantsButNetworkFailureDoesNot() {
        assertTrue(forecastAccessDenied(ReportState.NeedAuth))
        assertTrue(forecastAccessDenied(ReportState.NeedPremium))
        assertFalse(forecastAccessDenied(ReportState.Offline))
        assertFalse(forecastAccessDenied(ReportState.Ready(Json.parseToJsonElement("{}") as JsonObject)))
    }
    @Test fun comparisonUsesWireAspectAndStableCalendarDay() {
        val old = Json.parseToJsonElement("""{"events":[{"p1":"Sun","aspect_symbol":"square","p2":"Moon","date":"2026-10-18","exact_datetime":"2026-10-18T12:30:00"}]}""") as JsonObject
        val changed = Json.parseToJsonElement("""{"events":[{"p1":"Sun","aspect_symbol":"trine","p2":"Moon","date":"2026-10-18"}]}""") as JsonObject
        assertEquals(1, ForecastHistory.compare(old, changed).added)
        assertEquals(1, ForecastHistory.compare(old, changed).ended)
        val refined = Json.parseToJsonElement("""{"events":[{"p1":"Sun","aspect_symbol":"square","p2":"Moon","exact_datetime":"2026-10-18T12:30:02"}]}""") as JsonObject
        assertEquals(1, ForecastHistory.compare(old, refined).continued)
        assertEquals(0, ForecastHistory.compare(old, refined).added)
    }

    @Test fun cachedGrantsCannotOutliveSubscriptionOrOneDay() {
        assertEquals(1000L, accessCacheExpiry(1000, true, null))
        assertEquals(1500L, accessCacheExpiry(1000, true, 1500))
        assertEquals(86_401_000L, accessCacheExpiry(1000, true, 999_999_999))
        assertEquals(86_401_000L, accessCacheExpiry(1000, false, null))
    }

    @Test fun overviewUsesOnlyActualPositiveAndNegativeEvents() {
        val data = Json.parseToJsonElement("""{"events":[{"tone":"neutral","date":"1"},{"tone":"negative","date":"2"},{"tone":"positive","date":"3"}]}""") as JsonObject
        val (positive, negative) = forecastOverviewEvents(data)
        assertEquals("3", positive?.s("date"))
        assertEquals("2", negative?.s("date"))
        assertEquals(null to null, forecastOverviewEvents(Json.parseToJsonElement("{}") as JsonObject))
        assertEquals("First sentence.", forecastPreview("First sentence. Second sentence."))
    }

    @Test fun numericOrbUsesServerFieldAndSupportsOldPayloads() {
        assertEquals("1.25", forecastOrb(Json.parseToJsonElement("""{"orb":1.25,"orbit":2.5}""") as JsonObject))
        assertEquals("0.3", forecastOrb(Json.parseToJsonElement("""{"orbit":"0.3"}""") as JsonObject))
        assertNull(forecastOrb(Json.parseToJsonElement("{}") as JsonObject))
    }
    @Test fun cancellationNeverBecomesOffline() = runBlocking {
        try {
            loadReport { throw CancellationException("period changed") }
            fail("Cancellation must reach calling job")
        } catch (_: CancellationException) { }
    }
    @Test fun cacheRequiresSamePeriodRangeAndAccessScope() {
        val range = LocalDate.of(2026, 10, 1) to LocalDate.of(2026, 10, 31)
        val item = ForecastSnapshot(1, "THIS_MONTH", "2026-10-01", "2026-10-31", 1, "{}", "premium-owner")
        assertEquals(item, ForecastHistory.matching(listOf(item), ForecastPeriod.THIS_MONTH, range, "premium-owner"))
        assertNull(ForecastHistory.matching(listOf(item), ForecastPeriod.NEXT_MONTH, range, "premium-owner"))
        assertNull(ForecastHistory.matching(listOf(item), ForecastPeriod.THIS_MONTH, range, "guest"))
        assertNull(ForecastHistory.matching(listOf(item), ForecastPeriod.THIS_MONTH, range.first.plusDays(1) to range.second, "premium-owner"))
        assertNull(ForecastHistory.matching(listOf(item.copy(accessScope = "")), ForecastPeriod.THIS_MONTH, range, "guest"))
    }
    @Test fun logoutAndDowngradeChangeCacheScope() {
        val premium = ForecastHistory.scope("session", setOf("advanced_forecasts"))
        assertNotEquals(premium, ForecastHistory.scope(null, emptySet()))
        assertNotEquals(premium, ForecastHistory.scope("session", emptySet()))
        assertNotEquals(premium, ForecastHistory.scope("other", setOf("advanced_forecasts")))
        assertFalse(premium.contains("session"))
        assertEquals(ForecastHistory.scope("a", setOf("x", "y")), ForecastHistory.scope("a", setOf("y", "x")))
    }
}

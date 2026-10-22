package ru.astrosmap.app.ui.tools

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ForecastReliabilityTest {
    private fun clock(value: String) = Clock.fixed(Instant.parse(value), ZoneOffset.UTC)

    @Test fun cityDateWinsOverDeviceAndBirthAcrossDateLine() {
        val instant = clock("2026-10-31T23:30:00Z")
        assertEquals(LocalDate.of(2026, 11, 1), forecastToday("Pacific/Kiritimati", "America/Los_Angeles", instant))
        assertEquals(LocalDate.of(2026, 10, 31), forecastToday("America/Los_Angeles", "Pacific/Kiritimati", instant))
        assertEquals(LocalDate.of(2026, 11, 1) to LocalDate.of(2026, 11, 30), ForecastPeriod.THIS_MONTH.range(forecastToday("Pacific/Kiritimati", null, instant)))
    }

    @Test fun tomorrowIsCalendarDayAcrossDstRatherThanTwentyFourHours() {
        for (instant in listOf("2026-03-08T06:30:00Z", "2026-11-01T05:30:00Z")) {
            val today = forecastToday("America/New_York", null, clock(instant))
            assertEquals(today.plusDays(1) to today.plusDays(1), ForecastPeriod.TOMORROW.range(today))
        }
        assertEquals("Europe/Moscow", forecastZone("invalid zone", "Europe/Moscow").id)
    }

    @Test fun accessValidationAndThrottlingNeverUseCachedForecasts() {
        val states = listOf(401 to ReportState.NeedAuth, 402 to ReportState.NeedPremium,
            403 to ReportState.Forbidden, 429 to ReportState.RateLimited,
            400 to ReportState.InvalidRequest, 422 to ReportState.InvalidRequest)
        for ((code, state) in states) {
            val response = Response.error<Any>(code, okhttp3.ResponseBody.create(null, ""))
            assertEquals(state, reportFailure(HttpException(response)))
            assertFalse(reportAllowsCachedResult(state))
        }
        assertTrue(forecastAccessDenied(ReportState.Forbidden))
        assertEquals(ReportState.ServerError, reportFailure(HttpException(Response.error<Any>(503, okhttp3.ResponseBody.create(null, "")))))
        assertTrue(reportAllowsCachedResult(ReportState.ServerError))
        assertEquals(ReportState.Offline, reportFailure(java.io.IOException()))
        assertEquals(ReportState.UnexpectedError, reportFailure(IllegalArgumentException()))
        assertFalse(reportAllowsCachedResult(ReportState.UnexpectedError))
    }

    @Test fun comparisonPrioritizesLatestDifferentCompatibleWindowWithoutChangingCacheMatch() {
        val range = LocalDate.of(2026, 11, 1) to LocalDate.of(2026, 11, 30)
        val same = ForecastSnapshot(1, "NEXT_MONTH", "2026-11-01", "2026-11-30", 100, "{}", "scope:en")
        val october = same.copy(period = "THIS_MONTH", start = "2026-10-01", end = "2026-10-31", createdAt = 20)
        val september = october.copy(start = "2026-09-01", end = "2026-09-30", createdAt = 10)
        val foreign = october.copy(accessScope = "other-account-or-lang-or-location", createdAt = 200)
        val items = listOf(same, september, foreign, october)
        assertEquals(october, ForecastHistory.comparisonBaseline(items, ForecastPeriod.NEXT_MONTH, range, "scope:en"))
        assertEquals(same, ForecastHistory.matching(items, ForecastPeriod.NEXT_MONTH, range, "scope:en"))
        assertEquals(same, ForecastHistory.comparisonBaseline(listOf(same, foreign), ForecastPeriod.NEXT_MONTH, range, "scope:en"))
    }

    @Test fun comparisonKeepsDayWeekAndMonthFamiliesSeparate() {
        val today = LocalDate.of(2026, 10, 21)
        val day = ForecastSnapshot(1, "TODAY", "2026-10-21", "2026-10-21", 10, "{}", "scope")
        val week = day.copy(period = "THIS_WEEK", start = "2026-10-19", end = "2026-10-25", createdAt = 20)
        val month = day.copy(period = "THIS_MONTH", start = "2026-10-01", end = "2026-10-31", createdAt = 30)
        val days = listOf(day, week, month)
        assertEquals(day, ForecastHistory.comparisonBaseline(days, ForecastPeriod.TOMORROW, today.plusDays(1) to today.plusDays(1), "scope"))
        assertEquals(week, ForecastHistory.comparisonBaseline(days, ForecastPeriod.NEXT_WEEK, LocalDate.of(2026,10,26) to LocalDate.of(2026,11,1), "scope"))
        assertNull(ForecastHistory.comparisonBaseline(listOf(day, week), ForecastPeriod.NEXT_MONTH, LocalDate.of(2026,11,1) to LocalDate.of(2026,11,30), "scope"))
    }

    @Test fun comparisonRejectsCorruptPeriodDatesWindowAndPayload() {
        val valid = ForecastSnapshot(1, "THIS_MONTH", "2026-10-01", "2026-10-31", 10, "{}", "scope")
        val corrupt = listOf(valid.copy(period = "invalid"), valid.copy(start = "bad"),
            valid.copy(end = "2026-09-01"), valid.copy(start = "2026-10-02"),
            valid.copy(payload = "[]"), valid.copy(payload = "broken"),
            valid.copy(payload = """{"start":"2026-09-01"}"""),
            valid.copy(payload = """{"start":123}"""), valid.copy(createdAt = 0))
        val range = LocalDate.of(2026, 11, 1) to LocalDate.of(2026, 11, 30)
        assertNull(ForecastHistory.comparisonBaseline(corrupt, ForecastPeriod.NEXT_MONTH, range, "scope"))
        assertEquals(valid, ForecastHistory.comparisonBaseline(corrupt + valid, ForecastPeriod.NEXT_MONTH, range, "scope"))
    }

    @Test fun comparisonUsesActualChangedThemeAndActionWithoutFallbackProse() {
        fun payload(text: String) = kotlinx.serialization.json.Json.parseToJsonElement(text) as kotlinx.serialization.json.JsonObject
        val before = payload("""{"sphere_forecast":[{"name":"Synthetic topic","highlights":[{"interpretation":"Before"}]}]}""")
        val after = payload("""{"sphere_forecast":[{"name":"Synthetic topic","highlights":[{"interpretation":"After","advice":"Action"}]}]}""")
        val result = compareForecastThemes(before, after).single()
        assertEquals("Before", result.previous)
        assertEquals("After", result.current)
        assertEquals("Action", result.advice)
        assertTrue(compareForecastThemes(after, after).isEmpty())
        assertTrue(compareForecastThemes(payload("{}"), payload("{}")).isEmpty())
    }

    @Test fun explicitIdentityDenialIsTerminalEvenForAnEmptyGrantScope() {
        val scopeBefore = ForecastHistory.scope("expired-session", emptySet())
        val scopeAfter = ForecastHistory.scope("expired-session", emptySet())
        assertEquals(scopeBefore, scopeAfter)
        // Scope equality alone is insufficient: /me denial stops history and network loads.
        assertTrue(reportIdentityDenied(ReportState.NeedAuth, authenticated = true))
        assertTrue(reportIdentityDenied(ReportState.Forbidden, authenticated = true))
        assertFalse(reportIdentityDenied(ReportState.NeedAuth, authenticated = false))
        assertFalse(reportIdentityDenied(ReportState.Forbidden, authenticated = false))
        assertFalse(reportIdentityDenied(ReportState.Offline, authenticated = true))
        assertFalse(reportIdentityDenied(ReportState.ServerError, authenticated = true))
    }

    @Test fun sectionTargetsAccountForHiddenSpheresAndVariableEventLists() {
        assertEquals(Triple(4, 7, 11), forecastSectionIndices(4, 2, true, 3))
        assertEquals(Triple(10, 10, 31), forecastSectionIndices(10, 5, false, 20))
        assertEquals(Triple(4, 5, 6), forecastSectionIndices(4, 0, true, 0))
    }

    @Test fun localizedDatesKeepMalformedServerValuesSafe() {
        assertEquals("Oct 21, 2026", readableForecastDate("2026-10-21", Locale.US))
        assertEquals("bad-date", readableForecastDate("bad-date", Locale.US))
    }
}

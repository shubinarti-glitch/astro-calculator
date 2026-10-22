package ru.astrosmap.app.data

import org.junit.Assert.*
import org.junit.Test

class AnalyticsOutboxTest {
    private class Store : AnalyticsQueueStorage {
        var value: String? = null
        override fun read() = value
        override fun write(value: String) { this.value = value }
    }
    @Test fun restartsRetainPendingAndAckDoesNotDeleteConcurrentNewEvents() {
        val store = Store()
        val initial = AnalyticsOutbox(store)
        initial.enqueue("chart_created", mapOf("edit" to "false"), 10_000, "one")
        val resumed = AnalyticsOutbox(store)
        val sent = resumed.batch(10_001)
        assertEquals("one", sent.single().id)
        resumed.enqueue("advice_saved", null, 10_002, "two")
        resumed.acknowledge(sent.map { it.id }.toSet(), 10_003)
        assertEquals("two", AnalyticsOutbox(store).batch(10_004).single().id)
    }
    @Test fun boundedQueueExpiresAndKeepsNewestEvents() {
        val store = Store()
        val outbox = AnalyticsOutbox(store)
        repeat(250) { index -> outbox.enqueue("app_open", null, 2_000L * index, "id-$index") }
        assertEquals("id-50", outbox.batch(500_000).first().id)
        assertTrue(store.value!!.length <= AnalyticsOutbox.MAX_BYTES)
        assertTrue(outbox.batch(500_000 + AnalyticsOutbox.MAX_AGE).isEmpty())
    }
    @Test fun localDuplicateCoalescingAndUniqueAcknowledgementsAreFinite() {
        val outbox = AnalyticsOutbox(Store())
        assertTrue(outbox.enqueue("today_viewed", null, 5_000, "one"))
        assertFalse(outbox.enqueue("today_viewed", null, 5_100, "different-id"))
        assertFalse(outbox.enqueue("chart_saved", null, 6_000, "one"))
        assertTrue(outbox.enqueue("today_viewed", null, 6_001, "two"))
        assertEquals(2, outbox.batch(6_100).size)
    }
    @Test fun privacyAllowlistDropsUnknownEventsAndPersonalProperties() {
        val store = Store()
        val outbox = AnalyticsOutbox(store)
        assertFalse(outbox.enqueue("some-user-name", null, 1_000, "one"))
        assertTrue(outbox.enqueue("forecast_result", mapOf("period" to "NEXT_WEEK", "result" to "Offline",
            "name" to "DO-NOT-PERSIST", "note" to "PRIVATE-TEXT", "edit" to "PRIVATE-BIRTH"), 1_000, "two"))
        val props = outbox.batch(1_001).single().event.props!!
        assertEquals(mapOf("period" to "NEXT_WEEK", "result" to "Offline"), props)
        assertFalse(store.value!!.contains("PRIVATE"))
        assertFalse(store.value!!.contains("DO-NOT-PERSIST"))
    }
    @Test fun everyCurrentForecastPeriodAndReportStateKeepsItsFiniteDiagnosticValue() {
        ru.astrosmap.app.ui.tools.ForecastPeriod.entries.forEach { period ->
            val event = AnalyticsEventPolicy.clean("forecast_loaded", mapOf("period" to period.name))!!
            assertEquals(period.name, event.props?.get("period"))
        }
        ru.astrosmap.app.ui.tools.ReportState::class.java.declaredClasses.filter {
            ru.astrosmap.app.ui.tools.ReportState::class.java.isAssignableFrom(it)
        }.forEach { state ->
            val result = if (state.simpleName == "Ready") "loaded" else state.simpleName
            val event = AnalyticsEventPolicy.clean("forecast_result", mapOf("result" to result))!!
            assertEquals(result, event.props?.get("result"))
        }
    }

    @Test fun corruptOrUntrustedDiskDataCannotBeSent() {
        val store = Store().apply { value = "broken{" }
        val outbox = AnalyticsOutbox(store)
        assertTrue(outbox.batch(1_000).isEmpty())
        store.value = """[{"id":"bad","createdAt":1000,"event":{"name":"chart_created","props":{"note":"PRIVATE-TEXT"}}}]"""
        assertTrue(outbox.batch(1_001).isEmpty())
        assertFalse(store.value!!.contains("PRIVATE"))
    }
}

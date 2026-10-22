package ru.astrosmap.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.astrosmap.app.data.api.EventIn

/** Finite enums only. Do not add user-entered values, birth inputs or report text here. */
internal object AnalyticsEventPolicy {
    private val names = setOf("app_open", "notif_opened", "notif_shown", "premium_tapped",
        "chart_requested", "chart_created", "chart_failed", "chart_saved", "journal_opened",
        "today_viewed", "lunar_calendar_opened", "forecast_retry", "forecast_retry_result",
        "forecast_result", "forecast_loaded", "advice_saved")
    private val values = mapOf(
        "edit" to setOf("true", "false"), "from" to setOf("main"),
        "period" to setOf("TODAY", "TOMORROW", "THIS_WEEK", "THIS_MONTH", "NEXT_WEEK", "NEXT_MONTH"),
        "result" to setOf("loaded", "cached", "Idle", "Loading", "Ready", "NeedAuth", "NeedPremium",
            "Offline", "Forbidden", "InvalidRequest", "ServerError", "RateLimited", "UnexpectedError", "MissingChart"),
    )
    fun clean(name: String, props: Map<String, String>?): EventIn? {
        if (name !in names) return null
        val clean = props.orEmpty().filter { (key, value) -> value in values[key].orEmpty() }.toSortedMap()
        return EventIn(name, clean.takeIf { it.isNotEmpty() })
    }
}

@Serializable
internal data class QueuedAnalyticsEvent(val id: String, val createdAt: Long, val event: EventIn)

internal interface AnalyticsQueueStorage {
    fun read(): String?
    fun write(value: String)
}

/** Caller serializes access. Acknowledgements target UUIDs, never clear newly enqueued events. */
internal class AnalyticsOutbox(private val storage: AnalyticsQueueStorage) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private fun load(now: Long): List<QueuedAnalyticsEvent> = runCatching {
        json.decodeFromString<List<QueuedAnalyticsEvent>>(storage.read() ?: "[]")
    }.getOrDefault(emptyList()).filter {
        it.createdAt <= now + 60_000 && now - it.createdAt <= MAX_AGE &&
            AnalyticsEventPolicy.clean(it.event.name, it.event.props) == it.event
    }.distinctBy { it.id }.takeLast(MAX_EVENTS)

    private fun store(events: List<QueuedAnalyticsEvent>) {
        var bounded = events.takeLast(MAX_EVENTS)
        var encoded = json.encodeToString(bounded)
        while (encoded.length > MAX_BYTES && bounded.isNotEmpty()) {
            bounded = bounded.drop(1)
            encoded = json.encodeToString(bounded)
        }
        storage.write(encoded)
    }

    fun enqueue(name: String, props: Map<String, String>?, now: Long, id: String): Boolean {
        val event = AnalyticsEventPolicy.clean(name, props) ?: return false
        val existing = load(now)
        if (existing.any { it.id == id || it.event == event && now >= it.createdAt && now - it.createdAt < 1_000 }) return false
        store(existing + QueuedAnalyticsEvent(id, now, event))
        return true
    }

    fun batch(now: Long): List<QueuedAnalyticsEvent> {
        val existing = load(now)
        store(existing) // Persist age/size eviction even when no further events arrive.
        return existing.take(BATCH_SIZE)
    }

    fun acknowledge(ids: Set<String>, now: Long) { store(load(now).filterNot { it.id in ids }) }
    companion object {
        const val MAX_EVENTS = 200
        const val MAX_BYTES = 65_536
        const val BATCH_SIZE = 20
        const val MAX_AGE = 7L * 24 * 60 * 60 * 1_000
    }
}

package ru.astrosmap.app.ui.tools

import android.content.Context
import ru.astrosmap.app.data.access.AccessState
import ru.astrosmap.app.data.access.Entitlement
import ru.astrosmap.app.data.access.SubscriptionPlan
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.time.DayOfWeek
import java.time.LocalDate

enum class ForecastPeriod {
    TODAY, TOMORROW, THIS_WEEK, NEXT_WEEK, THIS_MONTH, NEXT_MONTH;

    fun range(now: LocalDate = LocalDate.now()): Pair<LocalDate, LocalDate> = when (this) {
        TODAY -> now to now
        TOMORROW -> now.plusDays(1) to now.plusDays(1)
        THIS_WEEK -> now.with(DayOfWeek.MONDAY) to now.with(DayOfWeek.SUNDAY)
        NEXT_WEEK -> now.with(DayOfWeek.MONDAY).plusWeeks(1) to now.with(DayOfWeek.SUNDAY).plusWeeks(1)
        THIS_MONTH -> now.withDayOfMonth(1) to now.withDayOfMonth(now.lengthOfMonth())
        NEXT_MONTH -> now.plusMonths(1).withDayOfMonth(1) to
            now.plusMonths(1).withDayOfMonth(now.plusMonths(1).lengthOfMonth())
    }
}

@Serializable
data class ForecastSnapshot(
    val chartId: Long,
    val period: String,
    val start: String,
    val end: String,
    val createdAt: Long,
    val payload: String,
    val accessScope: String = "",
)

data class ForecastComparison(
    val added: Int,
    val ended: Int,
    val continued: Int,
    val favorableDelta: Int,
    val summaryChanged: Boolean,
)

object ForecastHistory {
    private const val PREFS = "forecast_history_v1"
    private const val KEY = "items"
    private const val MAX_ITEMS = 30
    private val json = Json { ignoreUnknownKeys = true }

    fun save(context: Context, chartId: Long, period: ForecastPeriod, range: Pair<LocalDate, LocalDate>, data: JsonObject, accessScope: String = "") {
        val snapshot = ForecastSnapshot(chartId, period.name, range.first.toString(), range.second.toString(), System.currentTimeMillis(), data.toString(), accessScope)
        val updated = (list(context).filterNot {
            it.accessScope == accessScope && it.chartId == chartId && it.period == period.name && it.start == snapshot.start && it.end == snapshot.end
        } + snapshot).sortedByDescending { it.createdAt }.take(MAX_ITEMS)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, json.encodeToString(updated)).apply()
    }

    fun list(context: Context, chartId: Long? = null): List<ForecastSnapshot> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        val all = runCatching { json.decodeFromString<List<ForecastSnapshot>>(raw) }.getOrDefault(emptyList())
        return if (chartId == null) all else all.filter { it.chartId == chartId }
    }

    fun matching(items: List<ForecastSnapshot>, period: ForecastPeriod, range: Pair<LocalDate, LocalDate>, scope: String): ForecastSnapshot? =
        items.firstOrNull { it.accessScope == scope && it.period == period.name &&
            it.start == range.first.toString() && it.end == range.second.toString() }

    fun scope(token: String?, entitlements: Set<String>): String {
        val identity = token?.let { java.security.MessageDigest.getInstance("SHA-256")
            .digest(it.toByteArray()).joinToString("") { byte -> "%02x".format(byte) } } ?: "guest"
        return identity + ":" + entitlements.sorted().joinToString(",")
    }

    // Offline grants expire within 24 hours, or sooner at subscription expiry. Token identity
    // changes on logout/account switch; denied authentication never uses this fallback.
    fun rememberAccess(context: Context, token: String?, access: AccessState, premiumUntil: Long?) {
        val key = scope(token, emptySet())
        val now = System.currentTimeMillis()
        val expiry = accessCacheExpiry(now, access.premium, premiumUntil)
        context.getSharedPreferences("forecast_access_v1", Context.MODE_PRIVATE).edit()
            .putString(key + "_grants", access.entitlements.joinToString(",") { it.wireName })
            .putString(key + "_plan", access.plan.wireName)
            .putBoolean(key + "_premium", access.premium)
            .putLong(key + "_expiry", expiry).apply()
    }

    fun revokeAccess(context: Context, token: String?) {
        val key = scope(token, emptySet())
        context.getSharedPreferences("forecast_access_v1", Context.MODE_PRIVATE).edit()
            .remove(key + "_grants").remove(key + "_plan")
            .remove(key + "_premium").remove(key + "_expiry").apply()
    }

    fun offlineAccess(context: Context, token: String?): AccessState? {
        val key = scope(token, emptySet())
        val prefs = context.getSharedPreferences("forecast_access_v1", Context.MODE_PRIVATE)
        if (prefs.getLong(key + "_expiry", 0) <= System.currentTimeMillis()) return null
        val premium = prefs.getBoolean(key + "_premium", false)
        return AccessState(
            plan = SubscriptionPlan.fromWire(prefs.getString(key + "_plan", null)),
            premium = premium,
            entitlements = prefs.getString(key + "_grants", "").orEmpty().split(",").mapNotNull(Entitlement::fromWire).toSet(),
        )
    }

    fun payload(snapshot: ForecastSnapshot): JsonObject? =
        runCatching { json.parseToJsonElement(snapshot.payload) as JsonObject }.getOrNull()

    fun compare(previous: JsonObject, current: JsonObject): ForecastComparison {
        fun eventKeys(data: JsonObject) = data.a("events").map {
            listOf(it.s("p1"), it.s("aspect") ?: it.s("aspect_symbol") ?: it.s("aspect_ru"),
                it.s("p2"), it.s("date") ?: it.s("exact_datetime")?.take(10)).joinToString("|")
        }.toSet()
        fun favorable(data: JsonObject) = data.a("sphere_forecast").count { it.s("tone") == "favorable" }
        val oldKeys = eventKeys(previous)
        val newKeys = eventKeys(current)
        return ForecastComparison(
            added = (newKeys - oldKeys).size,
            ended = (oldKeys - newKeys).size,
            continued = (newKeys intersect oldKeys).size,
            favorableDelta = favorable(current) - favorable(previous),
            summaryChanged = previous.s("summary") != current.s("summary"),
        )
    }
}

internal fun accessCacheExpiry(now: Long, premium: Boolean, premiumUntil: Long?): Long =
    if (premium) minOf(now + 86_400_000L, premiumUntil ?: now) else now + 86_400_000L

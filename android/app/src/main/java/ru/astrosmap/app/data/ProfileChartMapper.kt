package ru.astrosmap.app.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

internal object ProfileChartMapper {
    /** Профиль сайта -> локальная карта; кривые данные пропускаем. */
    fun fromServer(serverId: Long, label: String, data: JsonObject, owner: String, nearestTz: (Double, Double) -> String?): ChartEntity? {
        fun int(key: String): Int? = (data[key] as? JsonPrimitive)?.intOrNull
        fun dbl(key: String): Double? = (data[key] as? JsonPrimitive)?.doubleOrNull
        fun str(key: String): String? = (data[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

        if (serverId <= 0 || label.isBlank()) return null
        val year = int("year") ?: return null
        val month = int("month") ?: return null
        val day = int("day") ?: return null
        val lat = dbl("lat") ?: return null
        val lng = dbl("lng") ?: return null
        val hour = int("hour") ?: 12
        val minute = int("minute") ?: 0
        if (year !in 1..3000 || hour !in 0..23 || minute !in 0..59 ||
            !lat.isFinite() || !lng.isFinite() || lat !in -90.0..90.0 || lng !in -180.0..180.0 ||
            runCatching { java.time.LocalDate.of(year, month, day) }.isFailure) return null
        // Сайт может не хранить таймзону — берём зону ближайшего города из офлайн-базы.
        val tz = str("tz_str").takeUnless { it.isNullOrBlank() } ?: nearestTz(lat, lng) ?: "UTC"
        if (runCatching { java.time.ZoneId.of(tz) }.isFailure) return null
        return ChartEntity(
            serverId = serverId, ownerKey = owner,
            name = label,
            year = year, month = month, day = day,
            hour = hour, minute = minute,
            lat = lat, lng = lng, tz = tz,
            city = str("city").orEmpty(),
        )
    }
}

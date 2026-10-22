package ru.astrosmap.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class ProfileChartMapperTest {
    private val data = Json.parseToJsonElement("""{"year":1990,"month":1,"day":1,"hour":12,"minute":0,"lat":0.0,"lng":0.0,"tz_str":"UTC"}""").jsonObject
    private fun map(value: JsonObject = data) = ProfileChartMapper.fromServer(5, "Synthetic", value, "user:1") { _, _ -> "UTC" }
    @Test fun validProfileHasVerifiedOwnerAndStableServerAssociation() {
        val chart = map()!!
        assertEquals("user:1", chart.ownerKey)
        assertEquals(5L, chart.serverId)
        assertEquals("UTC", chart.tz)
    }
    @Test fun rejectsInvalidDateAndCoordinatesBeforeReachingEngine() {
        for ((key, value) in listOf("month" to 13, "day" to 32, "hour" to 24, "minute" to -1, "lat" to 91, "lng" to -181)) {
            assertNull("Must reject $key", map(JsonObject(data + (key to JsonPrimitive(value)))))
        }
        assertNull(map(JsonObject(data + ("tz_str" to JsonPrimitive("Bad/Zone")))))
    }
    @Test fun legacyMissingZoneUsesOfflineCityFallback() {
        val legacy = JsonObject(data - "tz_str")
        val chart = ProfileChartMapper.fromServer(5, "Synthetic", legacy, "user:1") { _, _ -> "Europe/Moscow" }!!
        assertEquals("Europe/Moscow", chart.tz)
    }
}

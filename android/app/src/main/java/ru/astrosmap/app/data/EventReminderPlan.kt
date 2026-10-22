package ru.astrosmap.app.data

import java.security.MessageDigest
import java.time.*

/** Technical event metadata only; never contains birth input or authored interpretation. */
data class EventReminderPlan(
    val owner: String, val date: String, val title: String, val city: String, val zoneId: String,
) {
    val id: String get() = MessageDigest.getInstance("SHA-256")
        .digest(listOf(owner, date, title, city, zoneId).joinToString("\u0000").toByteArray())
        .joinToString("") { "%02x".format(it) }

    /** Return null for a past/invalid time. DST gaps/overlaps must be resolved explicitly. */
    fun instant(hour: Int, minute: Int, now: Instant = Instant.now()): Instant? = runCatching {
        require(owner == TokenStore.GUEST || owner.matches(Regex("user:[1-9][0-9]*")))
        require(title.isNotBlank() && title.length <= 180 && city.length <= 180)
        val local = LocalDate.parse(date).atTime(hour, minute)
        val zone = ZoneId.of(zoneId)
        val offsets = zone.rules.getValidOffsets(local)
        // An ambiguous clock time is deliberately rejected rather than choosing silently.
        require(offsets.size == 1)
        local.toInstant(offsets.single()).takeIf { it.isAfter(now) }
    }.getOrNull()
}

/** A delayed reminder on its event day still belongs to the user and cannot be evicted. */
internal fun reminderMayBeEvicted(delivered: Boolean, date: String, zone: String, now: Instant = Instant.now()): Boolean =
    delivered || runCatching { LocalDate.parse(date).isBefore(now.atZone(ZoneId.of(zone)).toLocalDate()) }.getOrDefault(false)

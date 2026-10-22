package ru.astrosmap.app.data

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class EventReminderPlanTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private fun plan(date: String = "2026-10-25", zone: String = "Europe/Berlin", owner: String = "user:1") =
        EventReminderPlan(owner, date, "Sun trine Moon", "Berlin", zone)

    @Test fun localCityClockControlsDeliveryInstant() {
        assertEquals(Instant.parse("2026-10-25T08:00:00Z"), plan().instant(9, 0, now))
        assertEquals(Instant.parse("2026-10-24T19:00:00Z"), plan(zone = "Pacific/Kiritimati").instant(9, 0, now))
    }
    @Test fun pastInvalidAndDstAmbiguityAreRejected() {
        assertNull(plan().instant(9, 0, Instant.parse("2026-10-25T09:00:00Z")))
        assertNull(plan(date = "bad").instant(9, 0, now))
        assertNull(plan(zone = "bad").instant(9, 0, now))
        assertNull(plan().instant(25, 0, now))
        assertNull(plan().instant(2, 30, now)) // DST overlap; no silently chosen offset.
        assertNull(plan(date = "2026-03-29").instant(2, 30, now)) // DST gap.
        assertNull(plan(owner = "").instant(9, 0, now))
    }
    @Test fun stableIdentitySeparatesOwnersCitiesAndDates() {
        assertEquals(plan().id, plan().id)
        assertNotEquals(plan().id, plan(owner = "user:2").id)
        assertNotEquals(plan().id, plan(date = "2026-10-26").id)
        assertNotEquals(plan().id, plan(zone = "UTC").id)
        assertEquals(64, plan().id.length)
        assertNotNull(plan(owner = "guest").instant(9, 0, now))
    }
    @Test fun delayedTodayPendingIsNotEvictedForCapacity() {
        val late = Instant.parse("2026-10-25T22:00:00Z")
        assertFalse(reminderMayBeEvicted(false, "2026-10-25", "Europe/Berlin", late))
        assertFalse(reminderMayBeEvicted(false, "2026-10-26", "Europe/Berlin", late))
        assertTrue(reminderMayBeEvicted(false, "2026-10-24", "Europe/Berlin", late))
        assertTrue(reminderMayBeEvicted(true, "2026-10-25", "Europe/Berlin", late))
        assertFalse(reminderMayBeEvicted(false, "bad", "bad", late))
    }
}

package ru.astrosmap.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Disposable Room databases and fake identities only; no real account or chart is modified. */
class ChartOwnershipTest {
    private class Identity(owner: String?) : SessionIdentity {
        override val owners = MutableStateFlow(owner)
        override suspend fun owner() = owners.value
    }
    private fun chart(owner: String, name: String) = ChartEntity(ownerKey = owner, name = name,
        year = 1990, month = 1, day = 1, hour = 12, minute = 0,
        lat = 0.0, lng = 0.0, tz = "UTC", city = "Synthetic")

    @Test fun accountSwitchHidesOtherChartsIncludingDirectIdAndMutations() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, ChartDb::class.java).build()
        try {
            val identity = Identity("user:1")
            val dao = ChartDao(db.chartDao(), identity)
            val a = chart("user:1", "A")
            val aid = dao.upsert(a)
            assertEquals(listOf("A"), dao.search("").first().map { it.name })
            identity.owners.value = "user:2"
            assertTrue(dao.allOnce().isEmpty())
            assertNull(dao.byId(aid))
            dao.delete(aid)
            dao.updateTexts(aid, "do not write", "en")
            assertEquals("A", db.chartDao().byId(aid, "user:1")?.name)
            assertNull(db.chartDao().byId(aid, "user:1")?.textsJson)
            try { dao.upsert(a.copy(id = aid)); fail("Cross-owner save must fail") } catch (_: IllegalStateException) { }
            identity.owners.value = null
            assertTrue(dao.search("").first().isEmpty())
            assertTrue(dao.allOnce().isEmpty())
        } finally { db.close() }
    }

    @Test fun explicitGuestImportPreservesOriginalAndIsIdempotent() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, ChartDb::class.java).build()
        try {
            val raw = db.chartDao()
            val guest = chart("guest", "Guest").copy(serverId = 123, textsJson = "old cache", textsLang = "en")
            val guestId = raw.upsert(guest)
            val identity = Identity("user:1")
            val dao = ChartDao(raw, identity)
            assertTrue(dao.allOnce().isEmpty()) // Login alone never imports.
            assertEquals(1, dao.importGuests(setOf(guestId)))
            assertEquals(0, dao.importGuests(setOf(guestId)))
            val copy = dao.allOnce().single()
            assertNull(copy.serverId)
            assertNull(copy.textsJson)
            assertEquals("user:1", copy.ownerKey)
            assertNotEquals(guestId, copy.id)
            assertEquals(123L, raw.byId(guestId, "guest")?.serverId)
            identity.owners.value = "user:2"
            assertTrue(dao.allOnce().isEmpty())
            identity.owners.value = "guest"
            assertEquals(guestId, dao.allOnce().single().id)
        } finally { db.close() }
    }

    @Test fun explicitReimportAfterDeletionUsesNewNonceButRepeatedTapsDoNotDuplicate() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, ChartDb::class.java).build()
        try {
            val raw = db.chartDao()
            val guestId = raw.upsert(chart("guest", "Reimport synthetic"))
            val dao = ChartDao(raw, Identity("user:1"))
            assertEquals(1, dao.importGuests(setOf(guestId)))
            val first = dao.allOnce().single()
            raw.markPendingDelete(first.id, "user:1")
            assertEquals(1, dao.importGuests(setOf(guestId)))
            val second = dao.allOnce().single { !it.pendingDelete }
            assertNotEquals(first.clientId, second.clientId)
            assertTrue(second.clientId.length <= 80)
            assertEquals(0, dao.importGuests(setOf(guestId)))
            raw.delete(first.id, "user:1")
            raw.delete(second.id, "user:1")
            assertEquals(1, dao.importGuests(setOf(guestId)))
            assertNotEquals(second.clientId, dao.allOnce().single().clientId)
            assertNotNull(raw.byId(guestId, "guest"))
        } finally { db.close() }
    }

    @Test fun legacyImportDoesNotDuplicateAlreadyDownloadedExactChart() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, ChartDb::class.java).build()
        try {
            val raw = db.chartDao()
            val guest = chart("guest", "Same synthetic")
            val guestId = raw.upsert(guest)
            raw.upsert(guest.copy(id = 0, ownerKey = "user:1", clientId = "already-downloaded", serverId = 8))
            val dao = ChartDao(raw, Identity("user:1"))
            assertEquals(0, dao.importGuests(setOf(guestId)))
            assertEquals(1, dao.allOnce().size)
            assertNotNull(raw.byId(guestId, "guest"))
        } finally { db.close() }
    }

    @Test fun acknowledgementKeepsEditsMadeWhileRequestWasInFlight() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, ChartDb::class.java).build()
        try {
            val raw = db.chartDao()
            val initial = chart("user:1", "Before").copy(updatedAt = 10, dirty = true)
            val id = raw.upsert(initial)
            raw.upsert(initial.copy(id = id, name = "Edited while uploading", updatedAt = 11))
            raw.markSynced(id, 42, expectedUpdatedAt = 10, owner = "user:1")
            val retained = raw.byId(id, "user:1")!!
            assertEquals(42L, retained.serverId)
            assertTrue(retained.dirty)
            assertEquals("Edited while uploading", retained.name)
            raw.markSynced(id, 42, expectedUpdatedAt = 11, owner = "user:1")
            assertFalse(raw.byId(id, "user:1")!!.dirty)
        } finally { db.close() }
    }

    @Test fun createAcknowledgementRetainsServerIdOnDeleteTombstone() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, ChartDb::class.java).build()
        try {
            val raw = db.chartDao()
            val beforeUpload = chart("user:1", "Deleted during upload").copy(updatedAt = 10)
            val id = raw.upsert(beforeUpload)
            // The upload captured this row, then the user deleted before POST returned.
            raw.markPendingDelete(id, "user:1")
            raw.markSynced(id, 42, expectedUpdatedAt = 10, owner = "user:1")
            val retained = raw.byId(id, "user:1")!!
            assertEquals(42L, retained.serverId)
            assertTrue(retained.pendingDelete)
            assertEquals(beforeUpload.clientId, retained.clientId)
            assertTrue(raw.search("", "user:1").first().isEmpty())
        } finally { db.close() }
    }

    @Test fun remoteMergePreservesLocalIdentityAndNeverOverwritesDirtyEdits() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, ChartDb::class.java).build()
        try {
            val raw = db.chartDao()
            val initial = chart("user:1", "Before").copy(updatedAt = 10, serverId = 55, textsJson = "cached", textsLang = "en")
            val id = raw.upsert(initial)
            val same = initial.copy(id = id, clientId = "different-server-parser-key")
            assertTrue(raw.mergeServer(same, 10))
            assertEquals(initial.clientId, raw.byId(id, "user:1")!!.clientId)
            assertEquals("cached", raw.byId(id, "user:1")!!.textsJson)
            assertTrue(raw.mergeServer(same.copy(name = "Website change"), 10))
            assertNull(raw.byId(id, "user:1")!!.textsJson)
            raw.upsert(initial.copy(id = id, name = "New local edit", dirty = true, updatedAt = 11))
            assertFalse(raw.mergeServer(same.copy(name = "Stale remote"), 10))
            assertEquals("New local edit", raw.byId(id, "user:1")!!.name)
        } finally { db.close() }
    }

    @Test fun versionThreeMigrationPreservesAllLegacyDataWithoutAssigningAccount() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "ownership-migration-${UUID.randomUUID()}.db"
        val legacy = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
        legacy.execSQL("CREATE TABLE charts(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, serverId INTEGER, name TEXT NOT NULL, year INTEGER NOT NULL, month INTEGER NOT NULL, day INTEGER NOT NULL, hour INTEGER NOT NULL, minute INTEGER NOT NULL, lat REAL NOT NULL, lng REAL NOT NULL, tz TEXT NOT NULL, city TEXT NOT NULL, updatedAt INTEGER NOT NULL, textsJson TEXT, textsLang TEXT, dirty INTEGER NOT NULL, pendingDelete INTEGER NOT NULL)")
        legacy.execSQL("INSERT INTO charts VALUES(7, 99, 'Legacy synthetic', 1990, 1, 1, 12, 0, 0.0, 0.0, 'UTC', 'Synthetic', 10, 'old-cache', 'en', 1, 0)")
        legacy.version = 3
        legacy.close()
        val db = Room.databaseBuilder(context, ChartDb::class.java, name).addMigrations(ChartDb.MIGRATION_3_4).build()
        try {
            val original = db.chartDao().allOnce("guest").single()
            assertEquals(7L, original.id)
            assertEquals(99L, original.serverId)
            assertEquals("old-cache", original.textsJson)
            assertTrue(original.dirty)
            assertTrue(original.clientId.isNotBlank())
            assertTrue(db.chartDao().allOnce("user:1").isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}

package ru.astrosmap.app.data

import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emitAll

/** Every consumer, including workers and by-id routes, uses the active verified owner. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChartDao(private val raw: RoomChartDao, private val tokens: SessionIdentity) {
    private val importMutex = kotlinx.coroutines.sync.Mutex()
    suspend fun owner(): String? = tokens.owner()
    fun search(q: String): Flow<List<ChartEntity>> = flow {
        tokens.owner() // Load persisted identity before collecting; never briefly expose guest data.
        emitAll(tokens.owners.flatMapLatest { owner ->
            if (owner == null) flowOf(emptyList()) else raw.search(q, owner)
        })
    }
    suspend fun allOnce(): List<ChartEntity> = owner()?.let { raw.allOnce(it) }.orEmpty()
    suspend fun byId(id: Long): ChartEntity? = owner()?.let { raw.byId(id, it) }
    suspend fun upsert(entity: ChartEntity): Long {
        val owner = owner() ?: error("Account identity is not verified")
        check(entity.ownerKey == owner) { "Chart belongs to another session" }
        if (entity.id != 0L) check(raw.byId(entity.id, owner) != null) { "Chart not available" }
        return raw.upsert(entity)
    }
    suspend fun delete(id: Long) { owner()?.let { raw.delete(id, it) } }
    suspend fun updateTexts(id: Long, json: String, lang: String) { owner()?.let { raw.updateTexts(id, json, lang, it) } }
    suspend fun markPendingDelete(id: Long) { owner()?.let { raw.markPendingDelete(id, it) } }
    suspend fun markSynced(entity: ChartEntity, serverId: Long) {
        if (owner() == entity.ownerKey) raw.markSynced(entity.id, serverId, entity.updatedAt, entity.ownerKey)
    }

    suspend fun mergeServer(incoming: ChartEntity, expectedUpdatedAt: Long) {
        if (owner() == incoming.ownerKey) raw.mergeServer(incoming, expectedUpdatedAt)
    }

    fun guestCharts(): Flow<List<ChartEntity>> = raw.search("", TokenStore.GUEST)

    /** Explicit import copies, never moves/deletes guest originals or their server association. */
    suspend fun importGuests(ids: Set<Long>): Int = importMutex.withLock {
        val owner = owner()?.takeIf { it != TokenStore.GUEST } ?: return@withLock 0
        var imported = 0
        for (guest in raw.allOnce(TokenStore.GUEST).filter { it.id in ids && !it.pendingDelete }) {
            if (owner() != owner) break
            // Live lineage makes repeated taps idempotent. Each deliberate re-import after
            // deletion gets a new nonce, because the server correctly retains cancelled nonces.
            val legacyKey = "guest-${guest.clientId}"
            if (raw.allOnce(owner).none { !it.pendingDelete && (it.clientId == legacyKey ||
                    it.clientId.startsWith("$legacyKey-") ||
                    it.name == guest.name && it.city == guest.city && it.toBirthInput() == guest.toBirthInput()) }) {
                val copyKey = "$legacyKey-${java.util.UUID.randomUUID().toString().replace("-", "")}"
                raw.upsert(guest.copy(id = 0, ownerKey = owner, clientId = copyKey,
                    serverId = null, dirty = true, textsJson = null, textsLang = null))
                imported++
            }
        }
        imported
    }
}

package ru.astrosmap.app.data

import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import ru.astrosmap.app.data.api.AstroApi
import ru.astrosmap.app.data.api.NatalRequest
import ru.astrosmap.app.data.api.ProfileUpload
import javax.inject.Inject
import javax.inject.Singleton

/** Account-scoped sync. Updates are atomic; retries retain stable client identity. */
@Singleton
class SyncManager @Inject constructor(
    private val api: AstroApi,
    private val dao: ChartDao,
    private val tokenStore: TokenStore,
    private val cities: CityStore,
) {
    private val mutex = Mutex()
    private val backgroundScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    /** A confirmed local deletion must keep syncing even after its screen closes. */
    fun requestSync() { backgroundScope.launch { sync() } }

    /** true — синхронизация прошла (или не нужна: нет входа); false — сеть/сервер недоступны. */
    suspend fun sync(): Boolean = mutex.withLock {
        val token = tokenStore.get() ?: return true
        val authorization = "Bearer $token"
        try {
            val me = api.me(authorization)
            val uid = me.id ?: return false // Older server: fail closed, never guess ownership.
            if (!tokenStore.bindOwner(token, uid)) return false
            val owner = "user:$uid"
            suspend fun current() = tokenStore.get() == token && tokenStore.owner() == owner
            if (!current()) return false
            var complete = true
            val local = dao.allOnce()
            for (e in local.filter { it.pendingDelete }) {
                if (!current()) return false
                // This acknowledgement installs a server-side cancellation nonce even if
                // POST's response was lost or its transaction has not reached the server yet.
                // Older servers return 404/405/422: keep the local tombstone and retry later.
                api.discardProfile(e.clientId, authorization)
                if (!current()) return false
                e.serverId?.let { sid ->
                    try { api.deleteProfile(sid, authorization) }
                    catch (ex: HttpException) { if (ex.code() != 404) throw ex }
                }
                if (!current()) return false
                dao.delete(e.id)
            }
            for (e in local.filter { !it.pendingDelete && (it.dirty || it.serverId == null) }) {
                if (!current()) return false
                val created = try {
                    if (e.serverId != null) {
                        // No destructive delete/create fallback, even against an older server.
                        api.updateProfile(e.serverId, upload(e), authorization)
                    } else api.addProfile(upload(e), authorization)
                } catch (ex: HttpException) {
                    if (ex.code() == 402) { complete = false; continue } else throw ex
                }
                if (!current()) return false
                dao.markSynced(e, created.id)
            }
            if (!current()) return false
            val server = api.profiles(authorization)
            if (!current()) return false
            val fresh = dao.allOnce()
            val localByServerId = fresh.filter { it.serverId != null }.associateBy { it.serverId!! }
            for (p in server) {
                if (!current()) return false
                val incoming = ProfileChartMapper.fromServer(p.id, p.label, p.data, owner, cities::nearestTz) ?: continue
                val existing = localByServerId[p.id]
                if (existing == null) dao.upsert(incoming)
                else if (!existing.dirty && !existing.pendingDelete) {
                    dao.mergeServer(incoming.copy(id = existing.id), existing.updatedAt)
                }
            }
            // Retain absent records locally. A stale/partial response must never erase user data.
            // Explicit local deletion remains supported above.
            complete
        } catch (ex: kotlinx.coroutines.CancellationException) {
            throw ex
        } catch (ex: HttpException) {
            if (ex.code() == 401) tokenStore.clearIf(token)
            false
        } catch (ex: Exception) {
            false
        }
    }

    private fun upload(e: ChartEntity) = ProfileUpload(
        clientId = e.clientId,
        label = e.name,
        data = NatalRequest(
            name = e.name, year = e.year, month = e.month, day = e.day,
            hour = e.hour, minute = e.minute, lat = e.lat, lng = e.lng,
            tzStr = e.tz, city = e.city,
        ),
    )

}

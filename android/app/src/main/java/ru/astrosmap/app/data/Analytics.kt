package ru.astrosmap.app.data

import android.content.Context
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import ru.astrosmap.app.data.api.AstroApi
import ru.astrosmap.app.data.api.EventBatch
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Bounded, best-effort pseudonymous analytics. No account token or personal content is queued.
 * A lost HTTP response can cause a duplicate on retry: the server has no event-id deduplication.
 */
@Singleton
class Analytics @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: AstroApi,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queueMutex = Mutex()
    private val flushMutex = Mutex()
    private val outbox by lazy {
        val file = AtomicFile(File(context.noBackupFilesDir, "analytics-events-v1.json"))
        AnalyticsOutbox(object : AnalyticsQueueStorage {
            override fun read(): String? = try { file.openRead().bufferedReader().use { it.readText() } } catch (_: java.io.FileNotFoundException) { null }
            override fun write(value: String) {
                val stream = file.startWrite()
                try { stream.write(value.toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
                catch (e: Exception) { file.failWrite(stream); throw e }
            }
        })
    }
    private val deviceId: String by lazy {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("device_id", it).commit()
        }
    }
    init { scope.launch { safelyFlush() } }

    fun track(name: String, props: Map<String, String>? = null) {
        // Validate before scheduling; callers cannot persist arbitrary user content accidentally.
        val event = AnalyticsEventPolicy.clean(name, props) ?: return
        scope.launch {
            try {
                queueMutex.withLock { outbox.enqueue(event.name, event.props, System.currentTimeMillis(), UUID.randomUUID().toString()) }
                safelyFlush()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Storage/network failures never block the user journey. */ }
        }
    }

    private suspend fun safelyFlush() {
        if (!flushMutex.tryLock()) return
        try {
            var failures = 0
            // Limit one flush to the queue capacity even if the user keeps creating new events.
            var batches = 0
            while (batches < AnalyticsOutbox.MAX_EVENTS / AnalyticsOutbox.BATCH_SIZE) {
                val batch = queueMutex.withLock { outbox.batch(System.currentTimeMillis()) }
                if (batch.isEmpty()) return
                try {
                    // Empty explicit header suppresses interceptor auth: queued events must not
                    // be attributed to another account after login/logout while offline.
                    api.events(EventBatch(deviceId, batch.map { it.event }), authorization = "")
                    queueMutex.withLock { outbox.acknowledge(batch.map { it.id }.toSet(), System.currentTimeMillis()) }
                    failures = 0
                    batches++
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    if (e is HttpException && e.code() in setOf(400, 404, 413, 422)) {
                        queueMutex.withLock { outbox.acknowledge(batch.map { it.id }.toSet(), System.currentTimeMillis()) }
                        batches++
                    } else {
                        if (++failures >= 4) return // Persisted remainder resumes at next track/app start.
                        delay(2_000L shl (failures - 1))
                    }
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* Corrupt/unavailable local storage is non-fatal. */ }
        finally { flushMutex.unlock() }
    }
}

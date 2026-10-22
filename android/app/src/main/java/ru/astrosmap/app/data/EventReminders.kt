package ru.astrosmap.app.data

import android.Manifest
import android.app.PendingIntent
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.json.JSONObject
import ru.astrosmap.app.MainActivity
import ru.astrosmap.app.R
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

object EventReminders {
    private const val PREFS = "event_reminders_v176"
    private const val PREFIX = "event-reminder-"
    private const val MAX_PENDING = 50
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun name(id: String) = PREFIX + id

    fun scheduled(context: Context, plan: EventReminderPlan): Boolean =
        runCatching { !JSONObject(prefs(context).getString(plan.id, null) ?: return false)
            .optBoolean("delivered") }.getOrDefault(false)

    /** Must be called inside TokenStore.withCurrentSession. */
    fun schedule(context: Context, plan: EventReminderPlan, at: Instant): Operation? {
        if (!notificationsAllowed(context) || !at.isAfter(Instant.now())) return null
        val p = prefs(context)
        if (!p.contains(plan.id) && p.all.size >= MAX_PENDING) {
            val oldestExpired = p.all.mapNotNull { (id, value) ->
                runCatching {
                    val record = JSONObject(value as String)
                    if (reminderMayBeEvicted(record.optBoolean("delivered"), record.getString("date"), record.getString("zone")))
                        id to record.getLong("at") else null
                }.getOrNull()
            }.minByOrNull { it.second }
            if (oldestExpired == null) return null else cancel(context, oldestExpired.first)
        }
        return try {
        // WorkData only stores an opaque key. Private metadata remains in excluded app preferences.
        p.edit().putString(plan.id, JSONObject().put("owner", plan.owner).put("date", plan.date)
            .put("title", plan.title).put("city", plan.city).put("zone", plan.zoneId)
            .put("at", at.toEpochMilli()).toString()).apply()
        WorkManager.getInstance(context).enqueueUniqueWork(name(plan.id), ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<EventReminderWorker>()
                .setInputData(workDataOf("id" to plan.id, "at" to at.toEpochMilli()))
                .setInitialDelay(Duration.between(Instant.now(), at).toMillis().coerceAtLeast(1), TimeUnit.MILLISECONDS)
                .build())
        } catch (_: Exception) {
            runCatching { discardIfCurrent(context, plan.id, at.toEpochMilli()) }
            null
        }
    }

    internal fun discardIfCurrent(context: Context, id: String, at: Long) {
        if (record(context, id)?.optLong("at") == at) cancel(context, id)
    }

    fun notificationsAllowed(context: Context): Boolean {
        DailyNotify.ensureChannel(context)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = context.getSystemService(NotificationManager::class.java)
                ?.getNotificationChannel(DailyNotify.EVENT_CHANNEL_ID)
            if (channel?.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }

    fun cancel(context: Context, id: String) {
        prefs(context).edit().remove(id).apply()
        WorkManager.getInstance(context).cancelUniqueWork(name(id))
        NotificationManagerCompat.from(context).cancel(name(id), 0)
    }

    fun onSessionChanged(context: Context, owner: String?) {
        prefs(context).all.forEach { (id, value) ->
            if (runCatching { JSONObject(value as String).getString("owner") }.getOrNull() != owner) cancel(context, id)
        }
    }

    internal fun record(context: Context, id: String): JSONObject? =
        runCatching { JSONObject(prefs(context).getString(id, null) ?: return null) }.getOrNull()

    internal fun complete(context: Context, id: String) {
        // Retain the owner of delivered notifications so logout can remove those too.
        record(context, id)?.let { prefs(context).edit().putString(id, it.put("delivered", true).toString()).apply() }
    }
}

@HiltWorker
class EventReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val tokens: TokenStore,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("id") ?: return Result.success()
        val record = EventReminders.record(context, id) ?: return Result.success()
        val expectedAt = inputData.getLong("at", 0L)
        if (record.optLong("at") != expectedAt || record.optBoolean("delivered")) return Result.success()
        val owner = record.optString("owner")
        val current = tokens.owner()
        val token = tokens.get()
        if (owner != current) return Result.success() // Session observer owns cleanup; never delete a newer generation.
        tokens.withCurrentSession(owner, token) {
            if (isStopped) return@withCurrentSession
            val latest = EventReminders.record(context, id)
            if (latest?.optLong("at") != expectedAt || latest.optBoolean("delivered")) return@withCurrentSession
            val onEventDay = runCatching {
                java.time.LocalDate.now(java.time.ZoneId.of(record.getString("zone"))).toString() == record.getString("date")
            }.getOrDefault(false)
            if (!isStopped && onEventDay && System.currentTimeMillis() >= expectedAt && EventReminders.notificationsAllowed(context)) {
                DailyNotify.ensureChannel(context)
                val intent = Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra(DailyNotify.ROUTE, DailyNotify.ROUTE_TODAY)
                val pending = PendingIntent.getActivity(context, id.hashCode(), intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val body = listOf(record.optString("date"), record.optString("city"), record.optString("zone"))
                    .filter { it.isNotBlank() }.joinToString(" · ")
                val notification = NotificationCompat.Builder(context, DailyNotify.EVENT_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_chart).setContentTitle(record.optString("title"))
                    .setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setContentIntent(pending).setAutoCancel(true)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    EventReminders.complete(context, id)
                    return@withCurrentSession
                }
                try { NotificationManagerCompat.from(context).notify("event-reminder-$id", 0, notification) }
                catch (_: SecurityException) { /* Permission was revoked after the explicit opt-in. */ }
            }
            EventReminders.complete(context, id)
        }
        return Result.success()
    }
}

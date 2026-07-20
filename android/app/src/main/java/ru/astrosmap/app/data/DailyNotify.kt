package ru.astrosmap.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import ru.astrosmap.app.MainActivity
import ru.astrosmap.app.R
import ru.astrosmap.app.astro.AstroEngine
import ru.astrosmap.app.astro.BirthInput
import ru.astrosmap.app.ui.AstroLabels
import ru.astrosmap.app.ui.tools.LunarTexts
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Ежедневное напоминание — единственный внешний повод вернуться в приложение.
 *
 * Считается офлайн движком astrocore: ни сервера, ни FCM, ни токенов устройств.
 * Сознательно ровно одно уведомление в сутки, время выбирает пользователь,
 * и в тексте никогда нет рекламы подписки.
 */
object DailyNotify {

    const val WORK_NAME = "daily-notify"
    const val CHANNEL_ID = "daily"
    const val FROM_NOTIFICATION = "from_notification"

    private const val PREFS = "settings"
    private const val KEY_ON = "notify_enabled"
    private const val KEY_HOUR = "notify_hour"
    private const val KEY_MIN = "notify_min"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ON, false)
    fun hour(context: Context): Int = prefs(context).getInt(KEY_HOUR, 9)
    fun minute(context: Context): Int = prefs(context).getInt(KEY_MIN, 0)

    fun setTime(context: Context, h: Int, m: Int) {
        prefs(context).edit().putInt(KEY_HOUR, h).putInt(KEY_MIN, m).apply()
        if (isEnabled(context)) schedule(context)
    }

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ON, on).apply()
        if (on) schedule(context) else WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /**
     * Ставит задачу на ближайшее наступление выбранного времени.
     *
     * Одноразовая задача, а не периодическая: периодическая с политикой UPDATE
     * срабатывала раньше выбранного времени (WorkManager считает период от старой
     * постановки). После показа воркер сам ставит следующий день — см. DailyWorker.
     */
    fun schedule(context: Context) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(LocalTime.of(hour(context), minute(context)))
        if (!next.isAfter(now)) next = next.plusDays(1)
        // Именно в секундах: toMinutes() отбрасывает остаток и уводит показ на минуту раньше.
        val delay = Duration.between(now, next).seconds.coerceAtLeast(1)
        val request = OneTimeWorkRequestBuilder<DailyWorker>()
            .setInitialDelay(delay, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notify_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.notify_channel_desc) },
        )
    }
}

@HiltWorker
class DailyWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val dao: ChartDao,
    private val engine: AstroEngine,
    private val analytics: Analytics,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!DailyNotify.isEnabled(context)) return Result.success()
        val (title, text) = runCatching { buildText() }.getOrElse {
            // Сбой расчёта не роняет задачу, но должен быть виден в логах, а не проглатываться.
            Log.w("DailyWorker", "Не удалось собрать текст напоминания", it)
            return Result.success()
        }
        show(title, text)
        analytics.track("notif_shown")
        DailyNotify.schedule(context) // ставим завтрашний показ
        return Result.success()
    }

    /** Фаза и знак Луны на сегодня + самый точный транзит к сохранённой карте (всё офлайн). */
    private suspend fun buildText(): Pair<String, String> {
        val today = LocalDate.now()
        val tz = ZoneId.systemDefault().id
        // Луна не зависит от места — считаем на полдень в нулевой точке, как в лунном календаре.
        val sky = engine.natal(BirthInput(today.year, today.monthValue, today.dayOfMonth, 12, 0, 0.0, 0.0, tz))
        val phaseKey = sky.lunarPhase.name
        val moonSign = sky.points.first { it.name == "Moon" }.sign
        val title = context.getString(
            R.string.notify_title,
            "${LunarTexts.phaseEmoji[phaseKey] ?: ""} ${LunarTexts.phaseName(phaseKey)}",
            AstroLabels.sign(moonSign),
        )

        val mood = LunarTexts.moonMood(moonSign)
        val chart = dao.allOnce().firstOrNull { !it.pendingDelete }
            ?: return title to mood
        val natal = chart.toBirthInput()
        val transit = BirthInput(
            year = today.year, month = today.monthValue, day = today.dayOfMonth,
            hour = 12, minute = 0, lat = natal.lat, lng = natal.lng, tzId = natal.tzId,
        )
        val strongest = engine.transit(natal, transit).aspects.minByOrNull { it.orbit }
            ?: return title to mood
        val line = "${AstroLabels.pointGlyphs[strongest.p2] ?: strongest.p2} " +
            "${AstroLabels.aspectGlyphs[strongest.aspect] ?: ""} " +
            "${AstroLabels.pointGlyphs[strongest.p1] ?: strongest.p1} · ${AstroLabels.aspect(strongest.aspect)}"
        return title to "$line — $mood"
    }

    private fun show(title: String, text: String) {
        DailyNotify.ensureChannel(context)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(DailyNotify.FROM_NOTIFICATION, true)
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, DailyNotify.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_chart)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(1, notification) }
    }
}

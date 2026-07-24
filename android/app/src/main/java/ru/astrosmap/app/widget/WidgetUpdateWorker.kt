package ru.astrosmap.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import ru.astrosmap.app.MainActivity
import ru.astrosmap.app.R
import ru.astrosmap.app.astro.AstroEngine
import ru.astrosmap.app.astro.BirthInput
import ru.astrosmap.app.ui.AstroLabels
import ru.astrosmap.app.ui.tools.LunarTexts
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Обновляет виджет «Сегодня»: дата, фаза и знак Луны, настрой дня. Всё офлайн (движок),
 * как в напоминании DailyWorker. Планету не тянем — Луна не зависит от места (0/0, полдень).
 */
@HiltWorker
class WidgetUpdateWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val engine: AstroEngine,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))
        if (ids.isEmpty()) return Result.success()
        val views = runCatching { buildViews() }.getOrElse { return Result.success() }
        ids.forEach { mgr.updateAppWidget(it, views) }
        return Result.success()
    }

    private fun buildViews(): RemoteViews {
        val today = LocalDate.now()
        val tz = ZoneId.systemDefault().id
        val sky = engine.natal(BirthInput(today.year, today.monthValue, today.dayOfMonth, 12, 0, 0.0, 0.0, tz))
        val phaseKey = sky.lunarPhase.name
        val moonSign = sky.points.first { it.name == "Moon" }.sign

        val v = RemoteViews(context.packageName, R.layout.widget_today)
        val date = today.format(DateTimeFormatter.ofPattern("d MMMM, EEEE", Locale.getDefault()))
        v.setTextViewText(R.id.w_date, date.replaceFirstChar { it.uppercase() })
        v.setTextViewText(
            R.id.w_moon,
            "${LunarTexts.phaseEmoji[phaseKey] ?: ""} ${LunarTexts.phaseName(phaseKey)} · ${AstroLabels.sign(moonSign)}",
        )
        v.setTextViewText(R.id.w_mood, LunarTexts.moonMood(moonSign))

        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        v.setOnClickPendingIntent(R.id.w_root, pi)
        return v
    }
}

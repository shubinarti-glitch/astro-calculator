package ru.astrosmap.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
import ru.astrosmap.app.ui.LangPref
import ru.astrosmap.app.ui.tools.LunarTexts
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Считает данные дня офлайн (движок) и обновляет все виджеты AstroSMap: «День», «Луна»,
 * «Совет дня». Язык — как выбран в приложении (LangPref), а не системный. По завершении
 * переставляет суточную задачу на следующую полночь.
 */
@HiltWorker
class WidgetUpdateWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val engine: AstroEngine,
) : CoroutineWorker(context, params) {

    private data class DayData(val date: String, val moonLine: String, val mood: String, val advice: String)

    override suspend fun doWork(): Result {
        // Язык виджета следует за выбором в приложении.
        val lang = LangPref.get(context)
        if (lang != null) Locale.setDefault(Locale(lang))
        val res = localizedContext(lang)

        val mgr = AppWidgetManager.getInstance(context)
        val data = runCatching { compute() }.getOrNull()
        if (data != null) {
            updateType(mgr, TodayWidgetProvider::class.java) { dayViews(data) }
            updateType(mgr, MoonWidgetProvider::class.java) { moonViews(data) }
            updateType(mgr, AdviceWidgetProvider::class.java) { adviceViews(res, data) }
        }
        WidgetUpdater.scheduleMidnight(context) // следующий показ — в полночь
        return Result.success()
    }

    private fun localizedContext(lang: String?): Context {
        if (lang == null) return context
        val cfg = Configuration(context.resources.configuration)
        cfg.setLocale(Locale(lang))
        return context.createConfigurationContext(cfg)
    }

    private fun updateType(mgr: AppWidgetManager, cls: Class<*>, build: () -> RemoteViews) {
        val ids = mgr.getAppWidgetIds(ComponentName(context, cls))
        if (ids.isEmpty()) return
        val views = build()
        ids.forEach { mgr.updateAppWidget(it, views) }
    }

    private fun compute(): DayData {
        val today = LocalDate.now()
        val tz = ZoneId.systemDefault().id
        val sky = engine.natal(BirthInput(today.year, today.monthValue, today.dayOfMonth, 12, 0, 0.0, 0.0, tz))
        val phaseKey = sky.lunarPhase.name
        val moonSign = sky.points.first { it.name == "Moon" }.sign
        val date = today.format(DateTimeFormatter.ofPattern("d MMMM, EEEE", Locale.getDefault()))
            .replaceFirstChar { it.uppercase() }
        val moonLine = "${LunarTexts.phaseEmoji[phaseKey] ?: ""} ${LunarTexts.phaseName(phaseKey)} · ${AstroLabels.sign(moonSign)}"
        return DayData(date, moonLine, LunarTexts.moonMood(moonSign), LunarTexts.phaseAdvice(phaseKey))
    }

    private fun tapIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun dayViews(d: DayData) = RemoteViews(context.packageName, R.layout.widget_today).apply {
        setTextViewText(R.id.w_date, d.date)
        setTextViewText(R.id.w_moon, d.moonLine)
        setTextViewText(R.id.w_mood, d.mood)
        setOnClickPendingIntent(R.id.w_root, tapIntent())
    }

    private fun moonViews(d: DayData) = RemoteViews(context.packageName, R.layout.widget_moon).apply {
        setTextViewText(R.id.wm_moon, d.moonLine)
        setTextViewText(R.id.wm_advice, d.advice.ifBlank { d.mood })
        setOnClickPendingIntent(R.id.wm_root, tapIntent())
    }

    private fun adviceViews(res: Context, d: DayData) = RemoteViews(context.packageName, R.layout.widget_advice).apply {
        setTextViewText(R.id.wa_title, "💡 " + res.getString(R.string.widget_advice_title))
        setTextViewText(R.id.wa_text, d.advice.ifBlank { d.mood })
        setOnClickPendingIntent(R.id.wa_root, tapIntent())
    }
}

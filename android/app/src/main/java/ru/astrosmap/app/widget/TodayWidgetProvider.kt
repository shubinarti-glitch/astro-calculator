package ru.astrosmap.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Виджет «Сегодня» на домашнем экране. Обновление — через WidgetUpdateWorker
 * (Hilt даёт движок). Сразу при добавлении + периодически, чтобы фаза/знак Луны не устаревали.
 */
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        refreshNow(context)
        scheduleDaily(context)
    }

    override fun onEnabled(context: Context) {
        scheduleDaily(context)
    }

    override fun onDisabled(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(DAILY)
    }

    companion object {
        private const val DAILY = "widget_today_refresh"

        fun refreshNow(context: Context) {
            WorkManager.getInstance(context)
                .enqueue(OneTimeWorkRequestBuilder<WidgetUpdateWorker>().build())
        }

        // Луна меняет знак раз в ~2,5 дня, фаза — за сутки; 6 ч держит виджет свежим без спешки.
        fun scheduleDaily(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                DAILY,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WidgetUpdateWorker>(6, TimeUnit.HOURS).build(),
            )
        }
    }
}

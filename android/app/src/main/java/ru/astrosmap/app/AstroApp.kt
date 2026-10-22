package ru.astrosmap.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import ru.astrosmap.app.data.SyncWorker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.astrosmap.app.data.TokenStore
import ru.astrosmap.app.data.DailyNotify
import ru.astrosmap.app.widget.WidgetUpdateWorker
import ru.astrosmap.app.widget.WidgetUpdater
import javax.inject.Inject

@HiltAndroidApp
class AstroApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Inject lateinit var tokens: TokenStore
    @Inject lateinit var syncManager: ru.astrosmap.app.data.SyncManager

    @Inject lateinit var api: ru.astrosmap.app.data.api.AstroApi

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        ru.astrosmap.app.data.CrashDiagnostics.install(this, api)
        applicationScope.launch {
            tokens.owner() // Load the persisted identity before observing; null never means guest.
            tokens.owners.collectLatest { owner ->
                val token = tokens.get()
                val current = tokens.withCurrentSession(owner, token) {
                    DailyNotify.onSessionChanged(this@AstroApp, owner)
                    ru.astrosmap.app.data.EventReminders.onSessionChanged(this@AstroApp, owner)
                    // RemoteViews survive process death and must be scrubbed immediately,
                    // before the new owner's possibly slow network/engine calculation.
                    runCatching { WidgetUpdateWorker.clearPersonalizedViews(this@AstroApp) }
                        .onFailure { android.util.Log.w("AstroApp", "Widget host update failed", it) }
                }
                if (current && WidgetUpdater.hasAnyWidget(this@AstroApp)) WidgetUpdater.refreshNow(this@AstroApp)
                if (current && owner != null && owner != TokenStore.GUEST) {
                    applicationScope.launch { syncManager.sync() }
                }
            }
        }
        // Фоновая синхронизация карт с кабинетом — раз в 12 часов при наличии сети.
        val request = PeriodicWorkRequestBuilder<SyncWorker>(12, TimeUnit.HOURS)
            .setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SyncWorker.PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}

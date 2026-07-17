package ru.astrosmap.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ru.astrosmap.app.data.SyncManager
import ru.astrosmap.app.ui.AstroRoot
import ru.astrosmap.app.ui.theme.AstroTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var syncManager: SyncManager

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ru.astrosmap.app.ui.LangPref.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AstroTheme {
                AstroRoot()
            }
        }
        // Синхронизация карт при каждом запуске (если выполнен вход и есть сеть).
        lifecycleScope.launch { syncManager.sync() }
    }
}

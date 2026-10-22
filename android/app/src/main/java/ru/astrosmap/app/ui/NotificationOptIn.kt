package ru.astrosmap.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import ru.astrosmap.app.R
import ru.astrosmap.app.data.DailyNotify

/** Однократное понятное приглашение вместо спрятанного переключателя в кабинете. */
@Composable
fun NotificationOptInPrompt(eligible: Boolean = false) {
    val context = LocalContext.current
    var showOffer by remember { mutableStateOf(false) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            DailyNotify.setEnabled(context, true)
        } // A refusal closes the invitation; settings remain available in Account.
    }

    LaunchedEffect(eligible) {
        if (eligible && DailyNotify.shouldShowPrompt(context)) {
            delay(1_500)
            showOffer = true
        }
    }

    LaunchedEffect(eligible) { if (!eligible) showOffer = false }

    fun dismissOffer() {
        DailyNotify.markPromptShown(context)
        showOffer = false
    }

    fun enable() {
        DailyNotify.markPromptShown(context)
        showOffer = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            DailyNotify.setEnabled(context, true)
        }
    }

    if (showOffer) {
        AlertDialog(
            onDismissRequest = ::dismissOffer,
            title = { Text(stringResource(R.string.notify_offer_title)) },
            text = { Text(stringResource(R.string.notify_offer_text)) },
            confirmButton = {
                TextButton(onClick = ::enable) {
                    Text(stringResource(R.string.notify_offer_enable))
                }
            },
            dismissButton = {
                TextButton(onClick = ::dismissOffer) {
                    Text(stringResource(R.string.notify_offer_later))
                }
            },
        )
    }

}

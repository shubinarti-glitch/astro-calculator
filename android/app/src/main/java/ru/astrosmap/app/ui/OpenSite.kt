package ru.astrosmap.app.ui

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/** Открывает сайт (оплата подписки идёт там — RuStore/APK это разрешают). */
fun openSite(context: Context) {
    CustomTabsIntent.Builder().build()
        .launchUrl(context, Uri.parse("https://astrosmap.ru/"))
}

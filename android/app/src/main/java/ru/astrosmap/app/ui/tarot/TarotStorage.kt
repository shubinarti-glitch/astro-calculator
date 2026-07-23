package ru.astrosmap.app.ui.tarot

import android.content.Context
import java.time.LocalDate

/**
 * Локальное состояние Таро: карта дня (одна в сутки) и лимит раскладов.
 *
 * ponytail: SharedPreferences, как язык и напоминание — своей таблицы Room не заводим.
 * Лимиты — на устройстве: премиум проверяется флагом, без обращения к серверу.
 */
object TarotStorage {

    private const val PREFS = "settings"
    private const val KEY_DAY_CARD = "tarot_day_card"     // id карты дня
    private const val KEY_DAY_DATE = "tarot_day_date"     // дата (epochDay), когда вытянули
    private const val KEY_SPREAD_DATE = "tarot_spread_date" // дата последнего расклада (epochDay)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun today() = LocalDate.now().toEpochDay()

    /** Карта дня, если уже вытянута сегодня; иначе null (нужно тянуть). */
    fun todayCard(context: Context): TarotCard? {
        val p = prefs(context)
        if (p.getLong(KEY_DAY_DATE, -1) != today()) return null
        return p.getString(KEY_DAY_CARD, null)?.let { TarotDeck.byId(it) }
    }

    fun saveDayCard(context: Context, card: TarotCard) {
        prefs(context).edit()
            .putString(KEY_DAY_CARD, card.id)
            .putLong(KEY_DAY_DATE, today())
            .apply()
    }

    /**
     * Можно ли сделать расклад. Премиум — каждый день, бесплатно — раз в 7 дней.
     * Возвращает число дней до следующего бесплатного расклада (0 — можно сейчас).
     */
    fun spreadCooldownDays(context: Context, premium: Boolean): Int {
        if (premium) return 0
        val last = prefs(context).getLong(KEY_SPREAD_DATE, -8)
        if (last < 0) return 0
        val passed = (today() - last).toInt()
        return (7 - passed).coerceAtLeast(0)
    }

    fun markSpreadDone(context: Context) {
        prefs(context).edit().putLong(KEY_SPREAD_DATE, today()).apply()
    }
}

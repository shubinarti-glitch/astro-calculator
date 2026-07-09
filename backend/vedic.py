# -*- coding: utf-8 -*-
"""Ведический календарь (Панчанг): титхи, накшатра, вара и оценка благоприятности дней.

Считается в сидерическом зодиаке (аянамса Лахири) на Swiss Ephemeris. Для конкретного
человека добавляется Тарабала — качество дня относительно его джанма-накшатры.
"""
from __future__ import annotations

import calendar as _cal
from datetime import datetime
from typing import Optional

try:
    from zoneinfo import ZoneInfo
except Exception:  # pragma: no cover
    ZoneInfo = None

from .ephe import ensure_ephemeris

ensure_ephemeris()

import swisseph as swe  # noqa: E402

swe.set_sid_mode(swe.SIDM_LAHIRI)
_SFLAG = swe.FLG_SWIEPH | swe.FLG_SIDEREAL
_SEG = 360.0 / 27.0

# 27 накшатр: (RU, EN, качество)
NAKSHATRAS = [
    ("Ашвини", "Ashwini", "good"), ("Бхарани", "Bharani", "bad"),
    ("Криттика", "Krittika", "bad"), ("Рохини", "Rohini", "good"),
    ("Мригашира", "Mrigashira", "good"), ("Ардра", "Ardra", "bad"),
    ("Пунарвасу", "Punarvasu", "good"), ("Пушья", "Pushya", "good"),
    ("Ашлеша", "Ashlesha", "bad"), ("Магха", "Magha", "neutral"),
    ("Пурва Пхалгуни", "Purva Phalguni", "neutral"), ("Уттара Пхалгуни", "Uttara Phalguni", "good"),
    ("Хаста", "Hasta", "good"), ("Читра", "Chitra", "good"),
    ("Свати", "Swati", "good"), ("Вишакха", "Vishakha", "neutral"),
    ("Анурадха", "Anuradha", "good"), ("Джйештха", "Jyeshtha", "bad"),
    ("Мула", "Mula", "bad"), ("Пурва Ашадха", "Purva Ashadha", "neutral"),
    ("Уттара Ашадха", "Uttara Ashadha", "good"), ("Шравана", "Shravana", "good"),
    ("Дхаништха", "Dhanishta", "good"), ("Шатабхиша", "Shatabhisha", "neutral"),
    ("Пурва Бхадрапада", "Purva Bhadrapada", "bad"), ("Уттара Бхадрапада", "Uttara Bhadrapada", "good"),
    ("Ревати", "Revati", "good"),
]

# Названия титхи (1..15), используются в обоих пакшах
_TITHI_NAMES = [
    ("Пратипада", "Pratipada"), ("Двитийя", "Dwitiya"), ("Тритийя", "Tritiya"),
    ("Чатуртхи", "Chaturthi"), ("Панчами", "Panchami"), ("Шаштхи", "Shashthi"),
    ("Саптами", "Saptami"), ("Аштами", "Ashtami"), ("Навами", "Navami"),
    ("Дашами", "Dashami"), ("Экадаши", "Ekadashi"), ("Двадаши", "Dwadashi"),
    ("Трайодаши", "Trayodashi"), ("Чатурдаши", "Chaturdashi"),
]
_FULL = ("Пурнима", "Purnima")
_NEW = ("Амавасья", "Amavasya")

# Рикта-титхи (4, 9, 14) — неблагоприятны для начинаний
_RIKTA = {4, 9, 14}

# Тарабала: 9 тар (RU, EN, качество)
_TARAS = [
    ("Джанма", "Janma", "neutral"), ("Сампат", "Sampat", "good"),
    ("Випат", "Vipat", "bad"), ("Кшема", "Kshema", "good"),
    ("Пратьяк", "Pratyak", "bad"), ("Садхана", "Sadhana", "good"),
    ("Вадха", "Vadha", "bad"), ("Митра", "Mitra", "good"),
    ("Ати-Митра", "Ati-Mitra", "good"),
]

_WEEKDAYS = [
    ("Понедельник", "Monday"), ("Вторник", "Tuesday"), ("Среда", "Wednesday"),
    ("Четверг", "Thursday"), ("Пятница", "Friday"), ("Суббота", "Saturday"),
    ("Воскресенье", "Sunday"),
]

_QUALITY_RU = {"good": "благоприятный", "neutral": "нейтральный", "bad": "неблагоприятный"}
_QUALITY_EN = {"good": "favorable", "neutral": "neutral", "bad": "unfavorable"}

# Человеческое описание дня по накшатре (что за энергия, для чего хорош) — простым языком.
NAKSHATRA_GUIDE = [
    ("быстрая, лёгкая энергия — хорош для начала дел, поездок, лечения и спорта",
     "fast, light energy — good for starting things, travel, healing and sport"),
    ("интенсивный день — лучше завершать начатое, чем браться за новое",
     "intense day — better to finish things than to start new ones"),
    ("острая, очищающая энергия — подходит для решительных действий и порядка, но возможны вспышки",
     "sharp, cleansing energy — suits decisive action and tidying up, but flare-ups are possible"),
    ("плодородный, приятный день — любовь, покупки, творчество и всё, что должно расти",
     "fertile, pleasant day — love, shopping, creativity and anything meant to grow"),
    ("лёгкий, ищущий день — поездки, общение, поиск и учёба",
     "light, searching day — travel, communication, seeking and study"),
    ("грозовой день перемен — подходит для расчистки старого, но не для важных стартов",
     "stormy day of change — good for clearing out the old, not for important starts"),
    ("день обновления — возвращения, дом, переезды и второй шанс",
     "day of renewal — returns, home, moving and a second chance"),
    ("один из лучших дней — важные дела, забота, учёба и духовные практики (но не свадьба)",
     "one of the best days — important matters, care, study and spiritual practice (but not weddings)"),
    ("скрытный, интуитивный день — хорош для исследования, но осторожнее с доверием и новыми делами",
     "secretive, intuitive day — good for research, but be careful with trust and new ventures"),
    ("день уважения к корням — семья, традиции, признание заслуг и церемонии",
     "day of honoring roots — family, tradition, recognition and ceremonies"),
    ("день удовольствий и отдыха — любовь, праздники, творчество и забота о себе",
     "day of pleasure and rest — love, celebrations, creativity and self-care"),
    ("надёжный день — договоры, помощь, дружба и долгие обязательства",
     "reliable day — agreements, helping, friendship and lasting commitments"),
    ("умелый день — ручная работа, ремесло, сделки и практичные дела",
     "skilful day — handiwork, craft, deals and practical tasks"),
    ("яркий, творческий день — красота, дизайн, покупки и всё эффектное",
     "bright, creative day — beauty, design, shopping and anything striking"),
    ("независимый день — торговля, поездки, гибкость и новые связи",
     "independent day — trade, travel, flexibility and new connections"),
    ("целеустремлённый день — упорные усилия к цели, но возможна нетерпеливость",
     "goal-driven day — persistent effort toward a goal, but impatience is possible"),
    ("дружелюбный день — дружба, сотрудничество, путешествия и преданность",
     "friendly day — friendship, cooperation, travel and devotion"),
    ("напряжённый день — защита своего, но возможны соперничество и усталость",
     "tense day — protecting what's yours, but rivalry and fatigue are possible"),
    ("корчующий день — докапывание до сути и исследование, но не для важных стартов",
     "uprooting day — getting to the root and research, but not for important starts"),
    ("вдохновляющий день — убеждение, дебаты, очищение и смелые планы",
     "inspiring day — persuasion, debate, cleansing and bold plans"),
    ("день победы — важные начинания, ответственность и дела с долгим результатом",
     "day of victory — important undertakings, responsibility and matters with lasting results"),
    ("слушающий день — учёба, переговоры, музыка и связи",
     "listening day — study, negotiations, music and connections"),
    ("ритмичный, щедрый день — музыка, группы, финансы и активность",
     "rhythmic, generous day — music, groups, finance and activity"),
    ("целительный, нестандартный день — медицина, тайны, технологии и уединение",
     "healing, unconventional day — medicine, mysteries, technology and solitude"),
    ("серьёзный, интенсивный день — глубокие темы, но осторожнее с резкостью и риском",
     "serious, intense day — deep matters, but be careful with harshness and risk"),
    ("глубокий, спокойный день — мудрость, благотворительность и долгие решения",
     "deep, calm day — wisdom, charity and long-term decisions"),
    ("мягкий, завершающий день — забота, путешествия, искусство и завершение дел",
     "gentle, finishing day — care, travel, art and wrapping things up"),
]

# Совет по фазе Луны (пакше) — интуитивно понятно обычному пользователю.
_PAKSHA_ADVICE = {
    "waxing": ("Растущая Луна — время начинать, расти, набирать и развивать задуманное.",
               "The waxing Moon is a time to start, grow, gather and develop your plans."),
    "waning": ("Убывающая Луна — время завершать, отпускать, убирать лишнее и наводить порядок.",
               "The waning Moon is a time to finish, release, clear out and tidy up."),
}
# Итоговый совет по качеству дня.
_DAY_ADVICE = {
    "good": ("Хороший день для важных дел, начинаний и всего, что для вас значимо.",
             "A good day for important matters, new starts and anything meaningful to you."),
    "neutral": ("Обычный, ровный день — подойдёт для повседневных дел и текущих задач.",
                "An ordinary, steady day — fine for everyday matters and routine tasks."),
    "bad": ("Лучше отложить важные начинания; займитесь рутиной, отдыхом и завершением дел.",
            "Better to postpone important starts; focus on routine, rest and finishing things."),
}


def _li(pair, lang):
    return pair[1] if lang == "en" else pair[0]


def _jd_for_local_noon(year: int, month: int, day: int, tz_str: str) -> float:
    """Julian Day (UT) для местного полудня указанной даты."""
    if ZoneInfo is not None and tz_str:
        try:
            local = datetime(year, month, day, 12, 0, tzinfo=ZoneInfo(tz_str))
            ut = local.astimezone(ZoneInfo("UTC"))
            return swe.julday(ut.year, ut.month, ut.day, ut.hour + ut.minute / 60)
        except Exception:
            pass
    return swe.julday(year, month, day, 12.0)


def _sidereal(jd: float, body: int) -> float:
    return swe.calc_ut(jd, body, _SFLAG)[0][0]


def nakshatra_index(jd: float) -> int:
    return int(_sidereal(jd, swe.MOON) // _SEG) % 27


def birth_nakshatra_from_jd(jd: float) -> int:
    return nakshatra_index(jd)


def vedic_calendar(
    year: int,
    month: int,
    lat: float,
    lng: float,
    tz_str: str = "UTC",
    birth_nak: Optional[int] = None,
    lang: str = "ru",
) -> dict:
    """Панчанг на месяц: для каждого дня — титхи, накшатра, вара и оценка благоприятности."""
    days_in_month = _cal.monthrange(year, month)[1]
    days = []
    counts = {"good": 0, "neutral": 0, "bad": 0}

    for day in range(1, days_in_month + 1):
        jd = _jd_for_local_noon(year, month, day, tz_str)
        moon = _sidereal(jd, swe.MOON)
        sun = _sidereal(jd, swe.SUN)

        nak = int(moon // _SEG) % 27
        nak_ru, nak_en, nak_quality = NAKSHATRAS[nak]

        diff = (moon - sun) % 360
        tithi = int(diff // 12) + 1  # 1..30
        paksha_ru, paksha_en = ("Шукла (растущая)", "Shukla (waxing)") if tithi <= 15 else ("Кришна (убывающая)", "Krishna (waning)")
        if tithi == 15:
            tithi_name = _FULL
        elif tithi == 30:
            tithi_name = _NEW
        else:
            tithi_name = _TITHI_NAMES[(tithi - 1) % 15]
        is_rikta = (tithi % 15) in _RIKTA

        weekday = datetime(year, month, day).weekday()

        # Оценка
        score = 0
        notes = []
        if nak_quality == "good":
            score += 2
        elif nak_quality == "bad":
            score -= 2
        if is_rikta:
            score -= 2
            notes.append("Рикта-титхи — не лучший день для новых начинаний" if lang != "en" else "Rikta tithi — not ideal for new beginnings")
        if tithi == 30:
            score -= 1
            notes.append("Амавасья (новолуние)" if lang != "en" else "Amavasya (new moon)")
        if tithi == 15:
            score += 1

        tara = None
        if birth_nak is not None:
            count = (nak - birth_nak) % 27
            tara_idx = count % 9
            t_ru, t_en, t_quality = _TARAS[tara_idx]
            tara = {"name": _li((t_ru, t_en), lang), "quality": t_quality}
            if t_quality == "good":
                score += 2
            elif t_quality == "bad":
                score -= 2
                notes.append((f"Тара «{t_ru}» — неблагоприятный день для вас") if lang != "en" else f"Tara “{t_en}” — unfavorable day for you")

        quality = "good" if score >= 2 else ("bad" if score <= -2 else "neutral")
        counts[quality] += 1

        # Человеческое пояснение дня
        nak_meaning = _li(NAKSHATRA_GUIDE[nak], lang)
        paksha_key = "waxing" if tithi <= 15 else "waning"
        paksha_advice = _li(_PAKSHA_ADVICE[paksha_key], lang)
        day_advice = _li(_DAY_ADVICE[quality], lang)
        q_word = (_QUALITY_EN if lang == "en" else _QUALITY_RU)[quality]
        nak_cap = nak_meaning[:1].upper() + nak_meaning[1:]
        if lang == "en":
            summary = f"A {q_word} day. {nak_cap}. {paksha_advice}"
        else:
            summary = f"День {q_word}. {nak_cap}. {paksha_advice}"

        days.append({
            "day": day,
            "date": f"{year:04d}-{month:02d}-{day:02d}",
            "weekday": _li(_WEEKDAYS[weekday], lang),
            "weekday_idx": weekday,
            "tithi": tithi if tithi <= 15 else tithi - 15,
            "tithi_name": _li(tithi_name, lang),
            "paksha": _li((paksha_ru, paksha_en), lang),
            "nakshatra": nak + 1,
            "nakshatra_name": _li((nak_ru, nak_en), lang),
            "tara": tara,
            "quality": quality,
            "quality_ru": (_QUALITY_EN if lang == "en" else _QUALITY_RU)[quality],
            "note": "; ".join(notes),
            "nak_meaning": nak_meaning,
            "paksha_advice": paksha_advice,
            "day_advice": day_advice,
            "summary": summary,
        })

    return {
        "year": year,
        "month": month,
        "personalized": birth_nak is not None,
        "counts": counts,
        "days": days,
    }

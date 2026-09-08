# -*- coding: utf-8 -*-
"""SEO-страницы: авторские трактовки как отдельные HTML-страницы для поисковиков.

240 страниц (10 планет × 12 знаков + 10 планет × 12 домов) + каталог,
sitemap.xml и robots.txt. Тексты берутся через аксессоры interpretations
(учитывают правки из админки). RU URL сохранены; EN доступен через ?lang=en.
"""
from __future__ import annotations
from html import escape

from fastapi import APIRouter, HTTPException, Request
from fastapi.responses import HTMLResponse, PlainTextResponse, Response

from . import constants, interpretations

router = APIRouter()

_PLANET_SLUG = {
    "Sun": "solntse", "Moon": "luna", "Mercury": "merkuriy", "Venus": "venera",
    "Mars": "mars", "Jupiter": "yupiter", "Saturn": "saturn", "Uranus": "uran",
    "Neptune": "neptun", "Pluto": "pluton",
}
_SIGN_SLUG = {
    "Ari": "ovne", "Tau": "teltse", "Gem": "bliznetsah", "Can": "rake",
    "Leo": "lve", "Vir": "deve", "Lib": "vesah", "Sco": "skorpione",
    "Sag": "streltse", "Cap": "kozeroge", "Aqu": "vodolee", "Pis": "rybah",
}


def _planet_ru(planet: str) -> str:
    return constants.POINTS[planet]["ru"]


def _pages() -> dict[str, dict]:
    """slug -> {title, h1, text()} для всех 240 страниц. Тексты лениво (правки админки)."""
    pages = {}
    for planet, pslug in _PLANET_SLUG.items():
        for sign, sslug in _SIGN_SLUG.items():
            h1 = f"{_planet_ru(planet)} {constants.sign_in(sign)}"
            pages[f"{pslug}-v-{sslug}"] = {
                "h1": h1,
                "title": f"{h1} — значение в натальной карте",
                "get_text": (lambda p=planet, s=sign: interpretations.authored_sign(p, s, "ru")),
            }
        for house in range(1, 13):
            h1 = f"{_planet_ru(planet)} в {house} доме"
            pages[f"{pslug}-v-{house}-dome"] = {
                "h1": h1,
                "title": f"{h1} — значение в натальной карте",
                "get_text": (lambda p=planet, h=house: interpretations.authored_house(p, h, "ru")),
            }
    return pages


PAGES = _pages()


def _english_pages():
    pages = {}
    for planet, pslug in _PLANET_SLUG.items():
        for sign, sslug in _SIGN_SLUG.items():
            pages[f"{pslug}-v-{sslug}"] = {
                "h1": f"{planet} in {constants.SIGNS[sign]['en']}",
                "get_text": lambda p=planet, s=sign: interpretations.authored_sign(p, s, "en"),
            }
        for house in range(1, 13):
            ordinal = {1: "1st", 2: "2nd", 3: "3rd"}.get(house, f"{house}th")
            pages[f"{pslug}-v-{house}-dome"] = {
                "h1": f"{planet} in the {ordinal} house",
                "get_text": lambda p=planet, h=house: interpretations.authored_house(p, h, "en"),
            }
    return pages


EN_PAGES = _english_pages()


def _language_links(request):
    path = escape(request.url.path, quote=True)
    base = escape(str(request.base_url).rstrip("/"), quote=True)
    return (f'<link rel="alternate" hreflang="ru" href="{base}{path}">'
            f'<link rel="alternate" hreflang="en" href="{base}{path}?lang=en">'
            f'<link rel="alternate" hreflang="x-default" href="{base}{path}">')


def _english_html(request, slug=None):
    page = EN_PAGES.get(slug) if slug else None
    heading = page["h1"] if page else "Planets in signs and houses"
    title = f"{heading} — natal chart meaning" if page else f"{heading} | AstroSMap"
    text = page["get_text"]() if page else "Original interpretations of planetary placements in zodiac signs and natal chart houses."
    description = text.split(".")[0][:160] + "."
    override = _SEO_OVERRIDES_EN.get(slug, {})
    title = override.get("title", title)
    description = override.get("description", description)
    quick_answer = ""
    if override.get("answer"):
        quick_answer = ('<section class="quick-answer" aria-label="Quick answer">'
                        f'<h2>In brief</h2><p>{escape(override["answer"])}</p></section>')
    path = request.url.path
    canonical = escape(str(request.base_url).rstrip("/") + path + "?lang=en", quote=True)
    content = "".join(f"<p>{escape(p)}</p>" for p in text.split("\n") if p.strip())
    prefix = slug.split("-v-")[0] + "-v-" if slug else ""
    links = " ".join(f'<a href="/opisanie/{s}?lang=en">{escape(p["h1"])}</a>'
                     for s, p in EN_PAGES.items() if s != slug and s.startswith(prefix))
    related_heading = f"{_slug_planet(slug.split('-v-')[0])} in other placements:" if page else "All interpretations"
    cluster = ""
    featured = ""
    for slugs, _ in _SEO_CLUSTERS:
        planet = _slug_planet(slugs[0].split("-v-")[0])
        label = f"the {planet}" if planet in ("Sun", "Moon") else planet
        cluster_links = "".join(
            f'<a href="/opisanie/{s}?lang=en">{escape(EN_PAGES[s]["h1"])}</a> '
            for s in slugs if s != slug
        )
        if slug in slugs:
            cluster = f'<section class="cluster"><h2>Read more about {label}</h2>{cluster_links}</section>'
        if not page:
            featured += f'<section class="featured"><h2>Popular articles about {label}</h2>{cluster_links}</section>'
    return f'''<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>{escape(title)}</title><meta name="description" content="{escape(description, quote=True)}">
<meta property="og:title" content="{escape(title, quote=True)}">
<meta property="og:description" content="{escape(description, quote=True)}">
<meta property="og:locale" content="en_US"><meta property="og:url" content="{canonical}">
<link rel="canonical" href="{canonical}">{_language_links(request)}
<link rel="icon" href="/icon.svg" type="image/svg+xml"><style>{_STYLE}</style></head>
<body><main><nav aria-label="Language"><a href="{escape(path)}" lang="ru">RU</a> · <a href="?lang=en" lang="en" aria-current="page">EN</a></nav>
<h1>{escape(heading)}</h1>{quick_answer}{content}{featured}
<a class="cta" href="/?lang=en">Calculate your natal chart for free</a>
{cluster}
<section class="rel"><h2>{related_heading}</h2>{links}</section>
<footer><a href="/opisaniya?lang=en">All interpretations</a> · Calculations by Swiss Ephemeris. This service is for information and entertainment. 18+</footer>
</main></body></html>'''

# Точечный SEO-слой для страниц, которые уже получают показы и находятся рядом
# с первой страницей выдачи. Авторские трактовки остаются нетронутыми: этот
# словарь управляет только сниппетом, кратким ответом и контекстными ссылками.
_SEO_OVERRIDES = {
    "uran-v-1-dome": {
        "title": "Уран в 1 доме — характер и самовыражение | Натальная карта",
        "description": (
            "Что означает Уран в 1 доме натальной карты: независимый характер, "
            "необычный образ, сильные стороны, отношения с людьми и точки роста."
        ),
        "answer": (
            "Уран в 1 доме делает независимость частью характера и внешнего образа. "
            "Человек стремится действовать по-своему, легко меняет способы "
            "самовыражения и тяжело переносит навязанные роли."
        ),
    },
    "uran-v-3-dome": {
        "title": "Уран в 3 доме — мышление и общение | Натальная карта",
        "description": (
            "Что означает Уран в 3 доме: нестандартное мышление, общение, обучение, "
            "отношения с близким окружением, сильные стороны и трудности."
        ),
        "answer": (
            "Уран в 3 доме даёт нестандартное мышление, быстрые озарения и потребность "
            "говорить своими словами. Обучение идёт лучше через свободу поиска, новые "
            "технологии и задачи, в которых нет единственного готового ответа."
        ),
    },
    "uran-v-5-dome": {
        "title": "Уран в 5 доме — творчество и любовь | Натальная карта",
        "description": (
            "Уран в 5 доме натальной карты: оригинальное творчество, неожиданные "
            "романы, отношения с детьми, потребность в свободе и точки роста."
        ),
        "answer": (
            "Уран в 5 доме проявляет свободу через творчество, любовь и яркое "
            "самовыражение. Вдохновение приходит внезапно, а в романтических "
            "отношениях особенно важны новизна, равенство и личное пространство."
        ),
    },
    "uran-v-11-dome": {
        "title": "Уран в 11 доме — друзья и планы | Натальная карта",
        "description": (
            "Уран в 11 доме натальной карты: необычные друзья, сообщества, планы на "
            "будущее, свобода в отношениях с группой и возможные трудности."
        ),
        "answer": (
            "Уран в 11 доме усиливает интерес к необычным людям, сообществам и идеям "
            "будущего. Дружба строится на равенстве и свободе, а лучшие проекты "
            "рождаются там, где можно обновлять правила вместе с единомышленниками."
        ),
    },
    "uran-v-lve": {
        "title": "Уран во Льве — творчество и свобода | Натальная карта",
        "description": (
            "Уран во Льве в натальной карте: поколенческая тяга к свободному "
            "творчеству, яркому самовыражению и обновлению привычных форм."
        ),
        "answer": (
            "Уран во Льве — поколенческое положение: стремление обновлять творчество, "
            "лидерство и способы быть заметным. В личной карте особенности проявления "
            "уточняют дом Урана и его аспекты."
        ),
    },
    "luna-v-2-dome": {
        "title": "Луна во 2 доме — деньги и чувство опоры | Натальная карта",
        "description": (
            "Что означает Луна во 2 доме: связь эмоций с деньгами, самооценкой и "
            "стабильностью, привычки в расходах, сильные стороны и точки роста."
        ),
        "answer": (
            "Луна во 2 доме связывает эмоциональную безопасность с материальной "
            "устойчивостью и ощущением собственной ценности. Настроение может влиять "
            "на траты, поэтому внутреннюю опору важно не сводить только к накоплениям."
        ),
    },
    "luna-v-4-dome": {
        "title": "Луна в 4 доме — семья и внутренний мир | Натальная карта",
        "description": (
            "Луна в 4 доме натальной карты: семья, дом, корни, отношения с матерью, "
            "потребность в безопасности, сильные стороны и эмоциональные трудности."
        ),
        "answer": (
            "Луна в 4 доме усиливает связь с семьёй, домом и личным прошлым. Для "
            "восстановления особенно важно безопасное пространство, где можно быть "
            "собой, заботиться о близких и не скрывать свои чувства."
        ),
    },
    "luna-v-5-dome": {
        "title": "Луна в 5 доме — любовь и творчество | Натальная карта",
        "description": (
            "Что означает Луна в 5 доме: эмоциональное творчество, романтические "
            "отношения, дети, потребность во внимании, таланты и точки роста."
        ),
        "answer": (
            "Луна в 5 доме раскрывает чувства через творчество, романтику, игру и "
            "отношения с детьми. Эмоциональная наполненность приходит, когда можно "
            "искренне проявляться и делиться теплом без постоянной оценки окружающих."
        ),
    },
    "luna-v-10-dome": {
        "title": "Луна в 10 доме — карьера и признание | Натальная карта",
        "description": (
            "Луна в 10 доме натальной карты: карьера, репутация, общественное "
            "признание, отношения с руководством, призвание и эмоциональные задачи."
        ),
        "answer": (
            "Луна в 10 доме делает карьеру и общественное признание эмоционально "
            "значимыми. Профессиональный путь может меняться вместе с внутренними "
            "потребностями, а успех часто связан с заботой и пониманием людей."
        ),
    },
    "luna-v-11-dome": {
        "title": "Луна в 11 доме — друзья и мечты | Натальная карта",
        "description": (
            "Что означает Луна в 11 доме: дружба, сообщества, планы и мечты, "
            "эмоциональная связь с единомышленниками, сильные стороны и трудности."
        ),
        "answer": (
            "Луна в 11 доме даёт потребность чувствовать себя частью дружеского круга "
            "или сообщества. Настроение связано с отношениями с единомышленниками, а "
            "мечты легче воплощаются в атмосфере поддержки и общей цели."
        ),
    },
    "mars-v-5-dome": {
        "title": "Марс в 5 доме — творчество, любовь и азарт | Натальная карта",
        "description": (
            "Марс в 5 доме натальной карты: активное творчество, страсть в любви, "
            "спорт, азарт, отношения с детьми, сильные стороны и точки роста."
        ),
        "answer": (
            "Марс в 5 доме направляет энергию в творчество, романтику, спорт и яркое "
            "самовыражение. Человеку важно действовать увлечённо и видеть отклик, но "
            "полезно отличать здоровую смелость от борьбы за внимание любой ценой."
        ),
    },
    "mars-v-6-dome": {
        "title": "Марс в 6 доме — работа и повседневные дела | Натальная карта",
        "description": (
            "Что означает Марс в 6 доме: энергия в работе и повседневных делах, "
            "привычки, нагрузка, здоровье, отношения с коллегами и точки роста."
        ),
        "answer": (
            "Марс в 6 доме побуждает действовать через конкретные задачи, работу и "
            "улучшение повседневных процессов. Высокая продуктивность раскрывается "
            "лучше при понятном режиме, движении и умении не воевать с каждой мелочью."
        ),
    },
    "mars-v-10-dome": {
        "title": "Марс в 10 доме — карьера и амбиции | Натальная карта",
        "description": (
            "Марс в 10 доме натальной карты: карьерные амбиции, лидерство, отношения "
            "с руководством, стремление к результату, конфликты и точки роста."
        ),
        "answer": (
            "Марс в 10 доме усиливает амбиции, инициативу и стремление самостоятельно "
            "влиять на профессиональный путь. Результат приходит быстрее, когда "
            "напор соединён со стратегией, ответственностью и уважением к границам."
        ),
    },
    "mars-v-11-dome": {
        "title": "Марс в 11 доме — друзья и общие цели | Натальная карта",
        "description": (
            "Что означает Марс в 11 доме: активность в дружбе и сообществах, борьба "
            "за общие идеи, планы на будущее, лидерство, конфликты и точки роста."
        ),
        "answer": (
            "Марс в 11 доме даёт энергию для командных проектов, общественных идей и "
            "смелых планов на будущее. В кругу друзей человек способен становиться "
            "инициатором, если соревнование не заслоняет общую цель."
        ),
    },
    "mars-v-lve": {
        "title": "Марс во Льве — воля и яркое действие | Натальная карта",
        "description": (
            "Марс во Льве в натальной карте: яркая воля, лидерство, страсть, смелость, "
            "творческая энергия, поведение в конфликте и возможные трудности."
        ),
        "answer": (
            "Марс во Льве побуждает действовать заметно, смело и творчески, добиваясь "
            "признания через личную инициативу. Сила положения раскрывается в "
            "великодушном лидерстве, а не в драматичной борьбе за превосходство."
        ),
    },
    "solntse-v-3-dome": {
        "title": "Солнце в 3 доме — мышление и общение | Натальная карта",
        "description": (
            "Солнце в 3 доме натальной карты: самовыражение через общение, обучение, "
            "контакты, отношения с близким окружением, таланты и точки роста."
        ),
        "answer": (
            "Солнце в 3 доме раскрывает личность через знания, речь, обучение и обмен "
            "идеями. Уверенность растёт, когда человек формулирует собственную точку "
            "зрения, оставаясь любознательным и внимательным к собеседникам."
        ),
    },
    "solntse-v-7-dome": {
        "title": "Солнце в 7 доме — отношения и партнёрство | Натальная карта",
        "description": (
            "Что означает Солнце в 7 доме: самореализация в отношениях, выбор "
            "партнёра, сотрудничество, открытые конфликты, сильные стороны и трудности."
        ),
        "answer": (
            "Солнце в 7 доме помогает лучше понимать себя через близкие отношения и "
            "сотрудничество. Важно видеть в партнёре равного человека, сохраняя "
            "собственные цели и не передавая другому право определять свою ценность."
        ),
    },
    "solntse-v-8-dome": {
        "title": "Солнце в 8 доме — трансформация и общие ресурсы | Натальная карта",
        "description": (
            "Что означает Солнце в 8 доме: глубокие перемены, кризисы и возрождение, "
            "общие деньги, близость, внутренняя сила, способности и точки роста."
        ),
        "answer": (
            "Солнце в 8 доме раскрывает личную силу через глубокие перемены, близость "
            "и темы общих ресурсов. Человек становится увереннее, когда не избегает "
            "сложных переживаний, а осознанно превращает их в опыт и внутреннюю опору."
        ),
    },
    "solntse-v-10-dome": {
        "title": "Солнце в 10 доме — карьера и призвание | Натальная карта",
        "description": (
            "Солнце в 10 доме натальной карты: карьера, призвание, амбиции, репутация, "
            "отношения с авторитетами, лидерские качества и точки роста."
        ),
        "answer": (
            "Солнце в 10 доме направляет самореализацию в профессию, общественную роль "
            "и достижение значимых целей. Признание приходит устойчивее, когда выбор "
            "пути опирается на собственные ценности, а не только на ожидания общества."
        ),
    },
    "solntse-v-11-dome": {
        "title": "Солнце в 11 доме — друзья и цели будущего | Натальная карта",
        "description": (
            "Что означает Солнце в 11 доме: самореализация среди друзей и в группе, "
            "единомышленники, мечты, общественные проекты, таланты и трудности."
        ),
        "answer": (
            "Солнце в 11 доме раскрывает индивидуальность через дружбу, сообщества и "
            "проекты, направленные в будущее. Человек ярче проявляет себя рядом с "
            "единомышленниками, если сохраняет личный голос внутри общей идеи."
        ),
    },
}

# Faithful translations of the Russian editorial layer, separate from authored text.
# Keep keys/fields in sync with _SEO_OVERRIDES (enforced by regression tests).
_SEO_OVERRIDES_EN = {
    "uran-v-1-dome": {
        "title": "Uranus in the 1st house — character and self-expression | Natal chart",
        "description": "What Uranus in the 1st house of a natal chart means: an independent character, an unusual image, strengths, relationships with others and areas for growth.",
        "answer": "Uranus in the 1st house makes independence part of one's character and outward image. The person seeks to act in their own way, readily changes how they express themselves and finds imposed roles difficult to tolerate.",
    },
    "uran-v-3-dome": {
        "title": "Uranus in the 3rd house — thinking and communication | Natal chart",
        "description": "What Uranus in the 3rd house means: unconventional thinking, communication, learning, relationships with one's immediate circle, strengths and difficulties.",
        "answer": "Uranus in the 3rd house brings unconventional thinking, sudden insights and a need to speak in one's own words. Learning works better through freedom to explore, new technologies and tasks with no single ready-made answer.",
    },
    "uran-v-5-dome": {
        "title": "Uranus in the 5th house — creativity and love | Natal chart",
        "description": "Uranus in the 5th house of a natal chart: original creativity, unexpected romances, relationships with children, a need for freedom and areas for growth.",
        "answer": "Uranus in the 5th house expresses freedom through creativity, love and vivid self-expression. Inspiration comes suddenly, while novelty, equality and personal space are especially important in romantic relationships.",
    },
    "uran-v-11-dome": {
        "title": "Uranus in the 11th house — friends and plans | Natal chart",
        "description": "Uranus in the 11th house of a natal chart: unusual friends, communities, future plans, freedom in relationships with a group and possible difficulties.",
        "answer": "Uranus in the 11th house strengthens interest in unusual people, communities and ideas for the future. Friendship is built on equality and freedom, and the best projects arise where rules can be renewed together with like-minded people.",
    },
    "uran-v-lve": {
        "title": "Uranus in Leo — creativity and freedom | Natal chart",
        "description": "Uranus in Leo in a natal chart: a generational drive toward free creativity, vivid self-expression and renewal of familiar forms.",
        "answer": "Uranus in Leo is a generational placement: a drive to renew creativity, leadership and ways of being noticed. In an individual chart, Uranus's house and aspects clarify how this is expressed.",
    },
    "luna-v-2-dome": {
        "title": "Moon in the 2nd house — money and a sense of security | Natal chart",
        "description": "What the Moon in the 2nd house means: the connection between emotions, money, self-esteem and stability, spending habits, strengths and areas for growth.",
        "answer": "The Moon in the 2nd house links emotional security to material stability and a sense of self-worth. Mood can influence spending, so it is important not to reduce one's inner sense of security to savings alone.",
    },
    "luna-v-4-dome": {
        "title": "Moon in the 4th house — family and the inner world | Natal chart",
        "description": "The Moon in the 4th house of a natal chart: family, home, roots, relationships with the mother, a need for security, strengths and emotional difficulties.",
        "answer": "The Moon in the 4th house strengthens the connection to family, home and one's personal past. A safe space where one can be oneself, care for loved ones and not hide one's feelings is especially important for recovery.",
    },
    "luna-v-5-dome": {
        "title": "Moon in the 5th house — love and creativity | Natal chart",
        "description": "What the Moon in the 5th house means: emotional creativity, romantic relationships, children, a need for attention, talents and areas for growth.",
        "answer": "The Moon in the 5th house reveals feelings through creativity, romance, play and relationships with children. Emotional fulfilment comes when one can express oneself sincerely and share warmth without constant judgment from others.",
    },
    "luna-v-10-dome": {
        "title": "Moon in the 10th house — career and recognition | Natal chart",
        "description": "The Moon in the 10th house of a natal chart: career, reputation, public recognition, relationships with management, vocation and emotional challenges.",
        "answer": "The Moon in the 10th house makes career and public recognition emotionally significant. The professional path may change along with inner needs, and success is often linked to caring for and understanding people.",
    },
    "luna-v-11-dome": {
        "title": "Moon in the 11th house — friends and dreams | Natal chart",
        "description": "What the Moon in the 11th house means: friendship, communities, plans and dreams, emotional connections with like-minded people, strengths and difficulties.",
        "answer": "The Moon in the 11th house brings a need to feel part of a circle of friends or a community. Mood is linked to relationships with like-minded people, and dreams are easier to realise in an atmosphere of support and shared purpose.",
    },
    "mars-v-5-dome": {
        "title": "Mars in the 5th house — creativity, love and excitement | Natal chart",
        "description": "Mars in the 5th house of a natal chart: active creativity, passion in love, sport, excitement, relationships with children, strengths and areas for growth.",
        "answer": "Mars in the 5th house directs energy into creativity, romance, sport and vivid self-expression. It is important for the person to act with enthusiasm and see a response, but it is helpful to distinguish healthy courage from fighting for attention at any cost.",
    },
    "mars-v-6-dome": {
        "title": "Mars in the 6th house — work and everyday tasks | Natal chart",
        "description": "What Mars in the 6th house means: energy in work and everyday tasks, habits, workload, health, relationships with colleagues and areas for growth.",
        "answer": "Mars in the 6th house encourages action through concrete tasks, work and improvement of everyday processes. High productivity develops best with a clear routine, movement and the ability not to battle over every little thing.",
    },
    "mars-v-10-dome": {
        "title": "Mars in the 10th house — career and ambition | Natal chart",
        "description": "Mars in the 10th house of a natal chart: career ambitions, leadership, relationships with management, a drive for results, conflicts and areas for growth.",
        "answer": "Mars in the 10th house strengthens ambition, initiative and the desire to shape one's professional path independently. Results come faster when drive is combined with strategy, responsibility and respect for boundaries.",
    },
    "mars-v-11-dome": {
        "title": "Mars in the 11th house — friends and shared goals | Natal chart",
        "description": "What Mars in the 11th house means: activity in friendships and communities, fighting for shared ideas, future plans, leadership, conflicts and areas for growth.",
        "answer": "Mars in the 11th house provides energy for team projects, social ideas and bold plans for the future. Among friends, the person can become an initiator if competition does not overshadow the shared goal.",
    },
    "mars-v-lve": {
        "title": "Mars in Leo — willpower and bold action | Natal chart",
        "description": "Mars in Leo in a natal chart: expressive willpower, leadership, passion, courage, creative energy, behaviour in conflict and possible difficulties.",
        "answer": "Mars in Leo encourages visible, bold and creative action, seeking recognition through personal initiative. The strength of this placement emerges in generous leadership rather than a dramatic struggle for superiority.",
    },
    "solntse-v-3-dome": {
        "title": "Sun in the 3rd house — thinking and communication | Natal chart",
        "description": "The Sun in the 3rd house of a natal chart: self-expression through communication, learning, contacts, relationships with one's immediate circle, talents and areas for growth.",
        "answer": "The Sun in the 3rd house reveals personality through knowledge, speech, learning and the exchange of ideas. Confidence grows when the person formulates their own point of view while remaining curious and attentive to those they speak with.",
    },
    "solntse-v-7-dome": {
        "title": "Sun in the 7th house — relationships and partnership | Natal chart",
        "description": "What the Sun in the 7th house means: self-realisation in relationships, choosing a partner, cooperation, open conflicts, strengths and difficulties.",
        "answer": "The Sun in the 7th house helps one understand oneself better through close relationships and cooperation. It is important to see a partner as an equal while maintaining one's own goals and not handing someone else the right to define one's worth.",
    },
    "solntse-v-8-dome": {
        "title": "Sun in the 8th house — transformation and shared resources | Natal chart",
        "description": "What the Sun in the 8th house means: profound changes, crises and rebirth, shared money, intimacy, inner strength, abilities and areas for growth.",
        "answer": "The Sun in the 8th house reveals personal strength through profound changes, intimacy and matters of shared resources. The person becomes more confident when, rather than avoiding difficult experiences, they consciously turn them into experience and inner support.",
    },
    "solntse-v-10-dome": {
        "title": "Sun in the 10th house — career and vocation | Natal chart",
        "description": "The Sun in the 10th house of a natal chart: career, vocation, ambitions, reputation, relationships with authority figures, leadership qualities and areas for growth.",
        "answer": "The Sun in the 10th house directs self-realisation toward a profession, a public role and the achievement of meaningful goals. Recognition is more enduring when the chosen path rests on one's own values, not only on society's expectations.",
    },
    "solntse-v-11-dome": {
        "title": "Sun in the 11th house — friends and future goals | Natal chart",
        "description": "What the Sun in the 11th house means: self-realisation among friends and in a group, like-minded people, dreams, social projects, talents and difficulties.",
        "answer": "The Sun in the 11th house reveals individuality through friendship, communities and projects aimed at the future. The person expresses themselves more vividly alongside like-minded people if they retain their own voice within the shared idea.",
    },
}

_URANUS_CLUSTER = (
    "uran-v-1-dome", "uran-v-3-dome", "uran-v-5-dome",
    "uran-v-11-dome", "uran-v-lve",
)

_MOON_CLUSTER = (
    "luna-v-2-dome", "luna-v-4-dome", "luna-v-5-dome",
    "luna-v-10-dome", "luna-v-11-dome",
)

_MARS_CLUSTER = (
    "mars-v-5-dome", "mars-v-6-dome", "mars-v-10-dome",
    "mars-v-11-dome", "mars-v-lve",
)

_SUN_CLUSTER = (
    "solntse-v-3-dome", "solntse-v-7-dome", "solntse-v-8-dome",
    "solntse-v-10-dome", "solntse-v-11-dome",
)

_SEO_CLUSTERS = (
    (_URANUS_CLUSTER, "Читайте также об Уране"),
    (_MOON_CLUSTER, "Читайте также о Луне"),
    (_MARS_CLUSTER, "Читайте также о Марсе"),
    (_SUN_CLUSTER, "Читайте также о Солнце"),
)

_STYLE = """
body{margin:0;background:#0d0b1a;color:#e8e4f0;font:18px/1.7 Georgia,serif;}
main{max-width:720px;margin:0 auto;padding:40px 20px;}
h1{color:#e8c66f;font-size:1.9em;line-height:1.3;}
h2{color:#e8c66f;font-size:1.25em;line-height:1.4;}
a{color:#b79ce8;}
.quick-answer{margin:22px 0 28px;padding:16px 18px;background:#17132a;border-left:3px solid #e8c66f;
border-radius:0 10px 10px 0;}
.quick-answer h2{margin:0 0 6px;font-size:1.05em;}
.quick-answer p{margin:0;}
.cta{display:inline-block;margin-top:28px;padding:12px 22px;background:#e8c66f;color:#1a1430;
border-radius:8px;text-decoration:none;font-weight:bold;}
.cluster{margin-top:32px;padding:18px;background:#17132a;border-radius:10px;}
.cluster h2{margin:0 0 8px;}
.cluster a{display:inline-block;margin:3px 14px 3px 0;}
.rel{margin-top:36px;padding-top:16px;border-top:1px solid #2e2750;font-size:.85em;}
.rel a{margin-right:12px;white-space:nowrap;line-height:2;}
.featured{margin:24px 0;padding:18px;background:#17132a;border-radius:10px;}
.featured h2{margin-top:0;}
footer{margin-top:36px;font-size:.8em;color:#8a83a8;}
"""


def _page_html(slug: str, page: dict, request: Request) -> str:
    text = page["get_text"]()
    paragraphs = "".join(f"<p>{p}</p>" for p in text.split("\n") if p.strip())
    override = _SEO_OVERRIDES.get(slug, {})
    title = override.get("title", page["title"])
    # Для приоритетных страниц — ручной сниппет; для остальных сохраняем шаблон.
    descr = override.get("description", text.split(".")[0][:160] + ".")
    quick_answer = ""
    if override.get("answer"):
        quick_answer = (
            '<section class="quick-answer" aria-label="Краткий ответ">'
            f'<h2>Кратко</h2><p>{override["answer"]}</p></section>'
        )
    base = str(request.base_url).rstrip("/")
    pslug = slug.split("-v-")[0]
    cluster = ""
    for slugs, heading in _SEO_CLUSTERS:
        if slug in slugs:
            links = "".join(
                f'<a href="/opisanie/{s}">{PAGES[s]["h1"]}</a>'
                for s in slugs if s != slug
            )
            cluster = f'<section class="cluster"><h2>{heading}</h2>{links}</section>'
            break
    related = "".join(
        f'<a href="/opisanie/{s}">{p["h1"]}</a> '
        for s, p in PAGES.items() if s.startswith(pslug + "-v-") and s != slug
    )
    return f"""<!doctype html>
<html lang="ru"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>{title}</title>
<meta name="description" content="{descr}">
<link rel="canonical" href="{base}/opisanie/{slug}">
<link rel="icon" href="/icon.svg" type="image/svg+xml">
<meta property="og:title" content="{title}">
<meta property="og:description" content="{descr}">
<style>{_STYLE}</style></head>
<body><main>
<h1>{page['h1']}</h1>
{quick_answer}
{paragraphs}
<a class="cta" href="/">Рассчитать свою натальную карту бесплатно</a>
{cluster}
<div class="rel"><b>{_planet_ru(_slug_planet(pslug))} в других положениях:</b><br>{related}</div>
<footer><a href="/opisaniya">Все описания</a> · Расчёты — Swiss Ephemeris. Сервис носит информационно-развлекательный характер. 18+</footer>
</main></body></html>"""


def _slug_planet(pslug: str) -> str:
    return next(p for p, s in _PLANET_SLUG.items() if s == pslug)


@router.get("/opisanie/{slug}", response_class=HTMLResponse)
def seo_page(slug: str, request: Request):
    if request.query_params.get("lang") == "en":
        page = EN_PAGES.get(slug)
        if not page or not page["get_text"]():
            raise HTTPException(status_code=404, detail="Page not found")
        return _english_html(request, slug)
    page = PAGES.get(slug)
    if not page or not page["get_text"]():
        raise HTTPException(status_code=404, detail="Страница не найдена")
    return _page_html(slug, page, request).replace("</head>", _language_links(request) + "</head>").replace(
        "<body><main>", '<body><main><nav aria-label="Язык"><a href="?lang=ru" lang="ru">RU</a> · <a href="?lang=en" lang="en">EN</a></nav>')


@router.get("/opisaniya", response_class=HTMLResponse)
def seo_index(request: Request):
    if request.query_params.get("lang") == "en":
        return _english_html(request)
    links = "".join(f'<a href="/opisanie/{s}">{p["h1"]}</a> ' for s, p in PAGES.items())
    featured = "".join(
        f'<a href="/opisanie/{s}">{PAGES[s]["h1"]}</a> ' for s in _URANUS_CLUSTER
    )
    moon_featured = "".join(
        f'<a href="/opisanie/{s}">{PAGES[s]["h1"]}</a> ' for s in _MOON_CLUSTER
    )
    mars_featured = "".join(
        f'<a href="/opisanie/{s}">{PAGES[s]["h1"]}</a> ' for s in _MARS_CLUSTER
    )
    sun_featured = "".join(
        f'<a href="/opisanie/{s}">{PAGES[s]["h1"]}</a> ' for s in _SUN_CLUSTER
    )
    return f"""<!doctype html>
<html lang="ru"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Планеты в знаках и домах — все описания | Астрокалькулятор</title>
<meta name="description" content="Авторские описания всех положений планет в знаках зодиака и домах натальной карты.">
<link rel="icon" href="/icon.svg" type="image/svg+xml">
<link rel="canonical" href="{escape(str(request.base_url).rstrip('/'))}/opisaniya">
{_language_links(request)}<style>{_STYLE}</style></head>
<body><main><nav aria-label="Язык"><a href="?lang=ru" lang="ru">RU</a> · <a href="?lang=en" lang="en">EN</a></nav><h1>Планеты в знаках и домах</h1>
<section class="featured"><h2>Популярные материалы об Уране</h2>{featured}</section>
<section class="featured"><h2>Популярные материалы о Луне</h2>{moon_featured}</section>
<section class="featured"><h2>Популярные материалы о Марсе</h2>{mars_featured}</section>
<section class="featured"><h2>Популярные материалы о Солнце</h2>{sun_featured}</section>
<div class="rel">{links}</div>
<a class="cta" href="/">Рассчитать свою натальную карту бесплатно</a>
</main></body></html>"""


@router.get("/sitemap.xml")
def sitemap(request: Request):
    base = str(request.base_url).rstrip("/")
    urls = [base + "/", base + "/opisaniya"] + [f"{base}/opisanie/{s}" for s in PAGES]
    urls += [base + "/opisaniya?lang=en"] + [f"{base}/opisanie/{s}?lang=en" for s in EN_PAGES]
    body = "".join(f"<url><loc>{u}</loc></url>" for u in urls)
    xml = f'<?xml version="1.0" encoding="UTF-8"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">{body}</urlset>'
    return Response(content=xml, media_type="application/xml")


@router.get("/robots.txt", response_class=PlainTextResponse)
def robots(request: Request):
    base = str(request.base_url).rstrip("/")
    return f"User-agent: *\nAllow: /\nDisallow: /api/\nSitemap: {base}/sitemap.xml\n"

# -*- coding: utf-8 -*-
"""SEO-страницы: авторские трактовки как отдельные HTML-страницы для поисковиков.

240 страниц (10 планет × 12 знаков + 10 планет × 12 домов) + каталог,
sitemap.xml и robots.txt. Тексты берутся через аксессоры interpretations
(учитывают правки из админки). Только RU — под русскоязычный поисковый трафик.
"""
from __future__ import annotations

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
    page = PAGES.get(slug)
    if not page or not page["get_text"]():
        raise HTTPException(status_code=404, detail="Страница не найдена")
    return _page_html(slug, page, request)


@router.get("/opisaniya", response_class=HTMLResponse)
def seo_index(request: Request):
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
<style>{_STYLE}</style></head>
<body><main><h1>Планеты в знаках и домах</h1>
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
    body = "".join(f"<url><loc>{u}</loc></url>" for u in urls)
    xml = f'<?xml version="1.0" encoding="UTF-8"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">{body}</urlset>'
    return Response(content=xml, media_type="application/xml")


@router.get("/robots.txt", response_class=PlainTextResponse)
def robots(request: Request):
    base = str(request.base_url).rstrip("/")
    return f"User-agent: *\nAllow: /\nDisallow: /api/\nSitemap: {base}/sitemap.xml\n"

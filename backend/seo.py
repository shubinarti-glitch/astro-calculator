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

_STYLE = """
body{margin:0;background:#0d0b1a;color:#e8e4f0;font:18px/1.7 Georgia,serif;}
main{max-width:720px;margin:0 auto;padding:40px 20px;}
h1{color:#e8c66f;font-size:1.9em;line-height:1.3;}
a{color:#b79ce8;}
.cta{display:inline-block;margin-top:28px;padding:12px 22px;background:#e8c66f;color:#1a1430;
border-radius:8px;text-decoration:none;font-weight:bold;}
.rel{margin-top:36px;padding-top:16px;border-top:1px solid #2e2750;font-size:.85em;}
.rel a{margin-right:12px;white-space:nowrap;line-height:2;}
footer{margin-top:36px;font-size:.8em;color:#8a83a8;}
"""


def _page_html(slug: str, page: dict, request: Request) -> str:
    text = page["get_text"]()
    paragraphs = "".join(f"<p>{p}</p>" for p in text.split("\n") if p.strip())
    # Описание для сниппета — первое предложение трактовки.
    descr = text.split(".")[0][:160] + "."
    base = str(request.base_url).rstrip("/")
    pslug = slug.split("-v-")[0]
    related = "".join(
        f'<a href="/opisanie/{s}">{p["h1"]}</a> '
        for s, p in PAGES.items() if s.startswith(pslug + "-v-") and s != slug
    )
    return f"""<!doctype html>
<html lang="ru"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>{page['title']}</title>
<meta name="description" content="{descr}">
<link rel="canonical" href="{base}/opisanie/{slug}">
<meta property="og:title" content="{page['title']}">
<meta property="og:description" content="{descr}">
<style>{_STYLE}</style></head>
<body><main>
<h1>{page['h1']}</h1>
{paragraphs}
<a class="cta" href="/">Рассчитать свою натальную карту бесплатно</a>
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
    return f"""<!doctype html>
<html lang="ru"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Планеты в знаках и домах — все описания | Астрокалькулятор</title>
<meta name="description" content="Авторские описания всех положений планет в знаках зодиака и домах натальной карты.">
<style>{_STYLE}</style></head>
<body><main><h1>Планеты в знаках и домах</h1>
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

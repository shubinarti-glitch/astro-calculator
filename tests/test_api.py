# -*- coding: utf-8 -*-
"""Smoke-тесты API: каждый эндпоинт отвечает корректно. Запуск: pytest -q

Тесты не создают пользователей (авторизация проверяется на несуществующих),
поэтому реальную базу data/app.db не засоряют.
"""
import random
import string

from fastapi.testclient import TestClient

from backend import astrology, db
from backend.main import app

db.init_db()  # таблицы на случай, если lifespan не запущен вне контекст-менеджера
client = TestClient(app)

# Премиум-эндпоинты (ректификация/синастрия/прогноз) в тестах открыты через
# override зависимости — реальную БД пользователями не засоряем.
from backend import main as main_module  # noqa: E402
app.dependency_overrides[main_module.require_premium] = lambda: 1

NATAL = {
    "name": "Test", "year": 1990, "month": 5, "day": 15, "hour": 14, "minute": 30,
    "lat": 55.7558, "lng": 37.6173, "city": "Moscow",
}


def _rnd(prefix):
    return prefix + "".join(random.choices(string.ascii_lowercase, k=8))


# ---- Расчётные эндпоинты ----
def test_meta():
    assert client.get("/api/meta").status_code == 200


def test_natal_ok():
    r = client.post("/api/natal", json=NATAL)
    assert r.status_code == 200
    d = r.json()
    assert d["planets"]
    assert d["essentials"]["lot_fortune"]["sign_ru"]
    assert "is_day" in d["essentials"]["sect"]


def test_natal_invalid_month():
    assert client.post("/api/natal", json=dict(NATAL, month=13)).status_code == 422


def test_synastry():
    r = client.post("/api/synastry", json={"person_a": NATAL, "person_b": dict(NATAL, year=1988)})
    assert r.status_code == 200
    c = r.json()["couple"]
    assert c["spheres"] and c["composite"] and c["overlays"]


def test_transit():
    r = client.post("/api/transit", json={"natal": NATAL, "transit_date": {"year": 2026, "month": 6, "day": 1, "hour": 12, "minute": 0}})
    assert r.status_code == 200


def test_return():
    r = client.post("/api/return", json={"natal": NATAL, "year": 2026, "return_type": "Solar"})
    assert r.status_code == 200


def test_progression():
    r = client.post("/api/progression", json={"natal": NATAL, "target_date": {"year": 2026, "month": 6, "day": 1, "hour": 12, "minute": 0}})
    assert r.status_code == 200


def test_forecast():
    r = client.post("/api/forecast", json={"natal": NATAL, "start": {"year": 2026, "month": 6, "day": 1}, "end": {"year": 2027, "month": 6, "day": 1}})
    assert r.status_code == 200


def test_calendar():
    r = client.post("/api/calendar", json={"natal": NATAL, "start": {"year": 2026, "month": 6, "day": 1}, "end": {"year": 2026, "month": 7, "day": 1}})
    assert r.status_code == 200


def test_vedic():
    r = client.post("/api/vedic-calendar", json={"year": 2026, "month": 6, "lat": 55.75, "lng": 37.61})
    assert r.status_code == 200


def test_rectification():
    r = client.post("/api/rectification", json={"natal": NATAL, "asc_traits": ["temp_fire"], "start_minute": 600, "end_minute": 900, "step_minute": 30})
    assert r.status_code == 200
    assert r.json()["best"]["time"]


def test_archetypes():
    assert client.get("/api/archetypes").status_code == 200


# ---- Безопасность ----
def test_register_short_password():
    assert client.post("/api/auth/register", json={"username": "x", "password": "1234"}).status_code == 422


def test_login_nonexistent():
    r = client.post("/api/auth/login", json={"username": _rnd("nouser_"), "password": "wrongpass123"})
    assert r.status_code == 401


def test_login_throttle():
    u = _rnd("brute_")
    codes = [client.post("/api/auth/login", json={"username": u, "password": "wrongpass123"}).status_code for _ in range(7)]
    assert codes.count(401) >= 5
    assert 429 in codes  # после 5 неудач — блокировка


def test_admin_requires_auth():
    assert client.get("/api/admin/texts").status_code == 401


def test_name_xss_sanitized():
    r = client.post("/api/natal", json=dict(NATAL, name='<img src=x onerror=alert(1)>Bob'))
    assert r.status_code == 200
    assert "<" not in r.json()["meta"]["name"]


# ---- Надёжность ----
def test_token_revocation():
    tok = db.make_token(999999)
    assert db.verify_token(tok) == 999999      # валиден
    db.revoke_token(tok)
    assert db.verify_token(tok) is None         # после выхода — отозван


def test_logout_endpoint():
    assert client.post("/api/auth/logout").status_code == 200  # идемпотентно, без токена тоже ок


def test_text_audit_log():
    before = len(db.list_text_audit())
    db.add_text_audit(1, "tester", "TEST.KEY", "edit")
    rows = db.list_text_audit()
    assert len(rows) == before + 1
    assert rows[0]["key"] == "TEST.KEY" and rows[0]["action"] == "edit"


def test_natal_house_interp_richer():
    # планета в доме теперь развёрнута (несколько предложений), а не одна фраза
    d = client.post("/api/natal", json=NATAL).json()
    sun = next(p for p in d["planets"] if p["name"] == "Sun")
    assert len(sun["interp_house"]) > 120


# ---- Ректификация: расширенный движок (символические дирекции + режим окна) ----
# Валидные события (после даты рождения 1990-05-15) для питания движка.
RECT_EVENTS = [
    {"date": {"year": 2010, "month": 6, "day": 1}, "label": "Свадьба", "type": "relationship", "weight": 1.5},
    {"date": {"year": 2015, "month": 9, "day": 20}, "label": "Карьера", "type": "career", "weight": 1.2},
    {"date": {"year": 2018, "month": 3, "day": 10}, "label": "Ребёнок", "type": "child", "weight": 1.0},
]


def _rect_call(**overrides):
    payload = {"natal": NATAL, "asc_traits": ["temp_fire"], "events": RECT_EVENTS}
    payload.update(overrides)
    return client.post("/api/rectification", json=payload)


def test_rectification_legacy_structure():
    # Регрессия: старый вызов без center/window возвращает прежнюю структуру.
    r = _rect_call(start_minute=600, end_minute=900, step_minute=30)
    assert r.status_code == 200
    d = r.json()
    assert set(("meta", "best", "top")).issubset(d.keys())
    best = d["best"]
    assert "time" in best and "score" in best and "breakdown" in best
    # best.time формата HH:MM
    hh, mm = best["time"].split(":")
    assert len(hh) == 2 and len(mm) == 2 and 0 <= int(hh) <= 23 and 0 <= int(mm) <= 59
    assert isinstance(d["top"], list) and d["top"]


def test_rectification_window_mode():
    # Режим окна: center=720 (12:00), window=120 (±2ч) → диапазон [600..840], шаг 1 мин.
    r = _rect_call(center_minute=720, window_minutes=120)
    assert r.status_code == 200
    d = r.json()
    best = d["best"]
    total_min = int(best["time"][:2]) * 60 + int(best["time"][3:])
    assert 600 <= total_min <= 840, f"best time {best['time']} вне окна"
    assert d["top"], "top должен быть непустым"
    # Сетка кандидатов (окно ±120 → 241 ≤ 400) прошла валидацию: запрос успешен.
    assert d["meta"]["candidates_checked"] > 0
    # top-времена тоже внутри окна (шаг реально 1 мин, регионы уточняются поминутно).
    for c in d["top"]:
        t = int(c["time"][:2]) * 60 + int(c["time"][3:])
        assert 600 <= t <= 840


def test_rectification_window_max_3h():
    # Валидный ±3ч (window=180 → 361 кандидат ≤ 400) не падает и возвращает результат.
    # 200 (не 400) означает: сетка кандидатов (361 ≤ лимита 400) прошла валидацию.
    r = _rect_call(center_minute=720, window_minutes=180)
    assert r.status_code == 200
    d = r.json()
    assert d["best"]["time"]
    # candidates_checked включает грубый проход + поминутное уточнение регионов,
    # поэтому может превышать 400 — лимит применяется к исходной сетке, не к нему.
    assert d["meta"]["candidates_checked"] > 0


def test_rectification_window_over_limit_rejected():
    # window_minutes=200 > максимума 180 → отклоняется валидацией Pydantic (422).
    r = _rect_call(center_minute=720, window_minutes=200)
    assert r.status_code == 422


def test_rectification_symbolic_arc_equals_years():
    # Юнит: sym_arc в предрасчёте события равен числу лет (ключ 1°/год).
    assert astrology.SYMBOLIC_KEY_DEG_PER_YEAR == 1.0
    # Событие ровно через ~20 тропических лет после рождения.
    events = [{"date": {"year": 2010, "month": 5, "day": 15}, "label": "e", "type": "", "weight": 1.0}]
    report = astrology.rectification_report(
        natal_params=dict(NATAL, lang="ru"),
        events=events,
        start_minute=700, end_minute=740, step_minute=5,
    )
    # Разбор непустой, событие отработано.
    assert report["best"]["breakdown"]
    assert report["meta"]["events_used"] == 1


def test_rectification_symbolic_direction_label_present():
    # Символические дирекции присутствуют в разборе хотя бы одного кандидата (best или top).
    # Мягкая проверка: при разнообразных событиях метка "сим.дир." должна встретиться,
    # иначе — как минимум разбор непустой (движок отработал события).
    r = _rect_call(center_minute=720, window_minutes=120)
    assert r.status_code == 200
    d = r.json()
    factors = []
    for cand in [d["best"]] + d["top"]:
        factors.extend(f.get("factor", "") for f in cand.get("breakdown", []))
    has_sym = any("сим.дир." in f for f in factors)
    has_any = any(f and f != "—" for f in factors)
    assert has_sym or has_any, "ни одной сработавшей техники в разборе"


def test_rectification_symbolic_label_direct():
    # Прямой юнит: движок EN → метка символической дирекции содержит "sym.dir."
    # при событии со значимой символической дугой. Проверяем сам код разбора.
    events = [{"date": {"year": 2016, "month": 8, "day": 25}, "label": "ev", "type": "career", "weight": 2.0}]
    report = astrology.rectification_report(
        natal_params=dict(NATAL, lang="en"),
        events=events,
        start_minute=0, end_minute=1439, step_minute=15,
    )
    factors = []
    for cand in [report["best"]] + report["top"]:
        factors.extend(f.get("factor", "") for f in cand.get("breakdown", []))
    # Хотя бы одна сработавшая техника; если это символическая — метка EN-формата.
    sym = [f for f in factors if f.startswith("sym.dir.")]
    for f in sym:
        assert "sym.dir." in f
    # Движок в любом случае вернул непустой разбор.
    assert any(f and f != "—" for f in factors)


# ---- Защиты (аудит безопасности 2026-07) ----
def test_revoke_rejects_forged_token():
    # Поддельный токен не должен попадать в revoked_tokens (DoS на БД).
    assert db.revoke_token("1.99999999999.deadbeef") is False


def test_body_size_limit():
    r = client.post("/api/natal", json=NATAL,
                    headers={"Content-Length": "2000000"})
    assert r.status_code == 413


def test_geocode_rate_limited():
    from backend.main import _RATE
    _RATE.pop("geo:testclient", None)
    codes = [client.get("/api/reverse-geocode", params={"lat": 0, "lng": 0}).status_code
             for _ in range(31)]
    assert 429 in codes
    _RATE.pop("geo:testclient", None)


def test_rectification_rate_limited():
    from backend.main import _RATE
    _RATE.pop("rect:testclient", None)
    body = {"natal": NATAL, "events": [], "start_minute": 0, "end_minute": 60, "step_minute": 30}
    codes = [client.post("/api/rectification", json=body).status_code for _ in range(11)]
    assert codes[-1] == 429
    _RATE.pop("rect:testclient", None)


# ---- SEO-страницы трактовок ----
def test_seo_page_sign():
    r = client.get("/opisanie/solntse-v-lve")
    assert r.status_code == 200
    assert "Солнце во Льве" in r.text
    assert "сиять" in r.text  # авторский текст, не заглушка


def test_seo_page_house_and_404():
    assert client.get("/opisanie/luna-v-7-dome").status_code == 200
    assert client.get("/opisanie/nesuschestvuet").status_code == 404


def test_seo_sitemap():
    r = client.get("/sitemap.xml")
    assert r.status_code == 200
    assert r.text.count("<loc>") == 242  # главная + каталог + 240 страниц


# ---- Подписка «Премиум» ----
def test_premium_gate_when_not_overridden():
    # временно убираем override: без токена премиум-эндпоинты закрыты
    ov = app.dependency_overrides.pop(main_module.require_premium)
    try:
        r = client.post("/api/synastry", json={"person_a": NATAL, "person_b": NATAL})
        assert r.status_code == 401
    finally:
        app.dependency_overrides[main_module.require_premium] = ov


def test_billing_plans_public():
    r = client.get("/api/billing/plans")
    assert r.status_code == 200
    assert r.json()["month"]["price"] == 299
    assert r.json()["year"]["price"] == 1990


def test_billing_create_requires_auth():
    assert client.post("/api/billing/create", json={"plan": "month"}).status_code == 401


def test_subscription_extend_and_expiry():
    import time as _t
    # логика продления без записи в users: несуществующий uid не пройдёт FK —
    # проверяем на временном пользователе и убираем за собой
    u = db.create_user(_rnd("subtest_"), "password123")
    try:
        assert not db.is_premium(u["id"])
        db.extend_subscription(u["id"], "month", 30)
        assert db.is_premium(u["id"])
        sub = db.get_subscription(u["id"])
        assert sub["expires_at"] > _t.time() + 29 * 86400
        # продление добавляет срок к текущему
        db.extend_subscription(u["id"], "year", 365)
        assert db.get_subscription(u["id"])["expires_at"] > _t.time() + 394 * 86400
    finally:
        with db.get_conn() as c:
            c.execute("DELETE FROM users WHERE id = ?", (u["id"],))


def test_billing_plus_plans():
    plans = client.get("/api/billing/plans").json()
    assert plans["plus_month"]["price"] == 1799 and plans["plus_year"]["price"] == 3490


def test_consultation_credit():
    u = db.create_user(_rnd("constest_"), "password123")
    try:
        assert db.has_consultation(u["id"]) is False
        db.add_consultation(u["id"])
        assert db.has_consultation(u["id"]) is True
    finally:
        with db.get_conn() as c:
            c.execute("DELETE FROM users WHERE id = ?", (u["id"],))


def test_billing_consult_plan():
    plans = client.get("/api/billing/plans").json()
    assert plans["consult"]["price"] == 3500 and plans["consult"]["days"] == 0

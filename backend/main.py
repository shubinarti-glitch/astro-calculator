# -*- coding: utf-8 -*-
"""FastAPI-приложение: API расчётов + раздача фронтенда."""
from __future__ import annotations

import logging
import time
from collections import defaultdict
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Optional

import requests
from fastapi import Depends, FastAPI, Header, HTTPException, Query, Request
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field, field_validator

from . import astrology, constants, content_store, db, payments, seo, vedic

logger = logging.getLogger("astro")

FRONTEND_DIR = Path(__file__).resolve().parent.parent / "frontend"

# Content-Security-Policy (defense-in-depth поверх экранирования ввода).
# 'sha256-...' разрешает единственный инлайн-скрипт темы (frontend/index.html).
# ВАЖНО: если правишь тот инлайн-скрипт — пересчитай хэш, иначе тема сломается.
# script-src НЕ содержит 'unsafe-inline' намеренно (иначе XSS-защита обнулится).
# style-src 'unsafe-inline' нужен из-за массовых inline style="..." в разметке и SVG.
CSP_POLICY = (
    "default-src 'self'; "
    "script-src 'self' 'sha256-2L7jEbKJgxbMdp7zMlk3HkAKXazyKLdNzE+aD8DoxXs='; "
    "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
    "font-src 'self' https://fonts.gstatic.com; "
    "img-src 'self' data: blob:; "
    "connect-src 'self'; "
    "base-uri 'self'; form-action 'self'; "
    "frame-ancestors 'none'; object-src 'none'"
)


@asynccontextmanager
async def lifespan(app: FastAPI):
    db.init_db()
    content_store.init()
    yield


app = FastAPI(title="Астрология — натальная карта и транзиты", version="0.1.0", lifespan=lifespan)

# CORS-middleware нет намеренно: API и фронтенд на одном источнике,
# кросс-доменные запросы не нужны — браузерный same-origin по умолчанию строже.


# --------------------------------------------------------------------------- #
#  Простой лимитер попыток (в памяти) — защита логина/регистрации от перебора
# --------------------------------------------------------------------------- #
_RATE: dict[str, list[float]] = defaultdict(list)


def _client_ip(request: Request) -> str:
    return request.client.host if request and request.client else "?"


def _rate_limited(key: str, max_n: int, window: int) -> bool:
    now = time.time()
    times = _RATE[key]
    times[:] = [t for t in times if t > now - window]
    return len(times) >= max_n


def _rate_record(key: str) -> None:
    _RATE[key].append(time.time())


MAX_BODY_BYTES = 1_000_000  # самый крупный легитимный запрос (ректификация) — десятки КБ


@app.middleware("http")
async def _sync_content(request, call_next):
    # Лимит размера тела: защита от гигантских JSON (uvicorn сам его не ограничивает).
    cl = request.headers.get("content-length")
    if cl and cl.isdigit() and int(cl) > MAX_BODY_BYTES:
        from fastapi.responses import JSONResponse
        return JSONResponse(status_code=413, content={"detail": "Слишком большой запрос"})
    # Синхронизировать правки текстов из файла (дёшево, по mtime) — чтобы изменения
    # были видны во всех воркерах uvicorn, а не только там, где был сделан POST.
    content_store.refresh_if_changed()
    response = await call_next(request)
    # Security-заголовки на все ответы (defense-in-depth).
    response.headers["Content-Security-Policy"] = CSP_POLICY
    response.headers["X-Content-Type-Options"] = "nosniff"
    response.headers["Referrer-Policy"] = "strict-origin-when-cross-origin"
    response.headers["X-Frame-Options"] = "DENY"
    # Не кэшировать фронтенд (html/js/css), чтобы правки были видны без Ctrl+F5.
    path = request.url.path
    if path == "/" or path.endswith((".html", ".js", ".css")):
        response.headers["Cache-Control"] = "no-store, must-revalidate"
        response.headers["Pragma"] = "no-cache"
        response.headers["Expires"] = "0"
    return response


# --------------------------------------------------------------------------- #
#  Pydantic-схемы запросов
# --------------------------------------------------------------------------- #
class BirthData(BaseModel):
    name: str = "Без имени"
    year: int = Field(..., ge=1, le=3000)
    month: int = Field(..., ge=1, le=12)
    day: int = Field(..., ge=1, le=31)
    hour: int = Field(12, ge=0, le=23)
    minute: int = Field(0, ge=0, le=59)
    lat: float = Field(..., ge=-90, le=90)
    lng: float = Field(..., ge=-180, le=180)
    tz_str: Optional[str] = None
    city: str = ""
    nation: str = ""
    houses_system: str = "P"
    zodiac_type: str = "Tropic"
    lang: str = "ru"

    @field_validator("name", "city", "nation")
    @classmethod
    def _strip_markup(cls, v: str) -> str:
        # Имя/город попадают в SVG-карту и в HTML результата — убираем разметку (XSS).
        if isinstance(v, str):
            return v.replace("<", "").replace(">", "").replace("&", "").strip()[:80]
        return v


class TransitDate(BaseModel):
    year: int = Field(..., ge=1, le=3000)
    month: int = Field(..., ge=1, le=12)
    day: int = Field(..., ge=1, le=31)
    hour: int = Field(12, ge=0, le=23)
    minute: int = Field(0, ge=0, le=59)


class TransitLocation(BaseModel):
    lat: float = Field(..., ge=-90, le=90)
    lng: float = Field(..., ge=-180, le=180)


class TransitRequest(BaseModel):
    natal: BirthData
    transit_date: TransitDate
    transit_location: Optional[TransitLocation] = None


class SynastryRequest(BaseModel):
    person_a: BirthData
    person_b: BirthData


class ReturnRequest(BaseModel):
    natal: BirthData
    year: int = Field(..., ge=1, le=3000)
    month: Optional[int] = Field(None, ge=1, le=12)  # для лунара — месяц
    return_type: str = "Solar"  # "Solar" | "Lunar"
    location: Optional[TransitLocation] = None


class ProgressionRequest(BaseModel):
    natal: BirthData
    target_date: TransitDate


class CalendarDate(BaseModel):
    year: int = Field(..., ge=1, le=3000)
    month: int = Field(..., ge=1, le=12)
    day: int = Field(..., ge=1, le=31)


class CalendarRequest(BaseModel):
    natal: BirthData
    start: CalendarDate
    end: CalendarDate
    location: Optional[TransitLocation] = None


class ForecastRequest(BaseModel):
    natal: BirthData
    start: CalendarDate
    end: CalendarDate
    location: Optional[TransitLocation] = None


class LifeEvent(BaseModel):
    date: CalendarDate
    label: str = ""
    type: str = ""  # relationship|career|child|move|loss|health (для усиления сигнификаторов)
    weight: float = Field(1.0, ge=0.1, le=5.0)


class PredispositionRating(BaseModel):
    key: str = Field(..., max_length=30)
    rating: str = Field("unknown", max_length=12)  # yes_strong|yes|no_strong|no|unknown


class RectificationRequest(BaseModel):
    natal: BirthData
    events: list[LifeEvent] = Field(default_factory=list, max_length=40)
    asc_traits: list[str] = Field(default_factory=list, max_length=20)
    predispositions: list[PredispositionRating] = Field(default_factory=list, max_length=30)
    parent_sun_signs: list[str] = Field(default_factory=list, max_length=2)
    start_minute: int = Field(0, ge=0, le=1439)
    end_minute: int = Field(1439, ge=0, le=1439)
    step_minute: int = Field(15, ge=1, le=120)
    center_minute: Optional[int] = Field(None, ge=0, le=1439)  # центр окна уточнения (мин от 00:00)
    window_minutes: Optional[int] = Field(None, ge=1, le=180)  # полуширина окна уточнения (±мин, максимум ±3ч)


class AuthRequest(BaseModel):
    username: str = Field(..., min_length=2, max_length=50)
    password: str = Field(..., min_length=8, max_length=200)


class ProfileIn(BaseModel):
    label: str = Field(..., min_length=1, max_length=120)
    data: dict


# --------------------------------------------------------------------------- #
#  Аутентификация
# --------------------------------------------------------------------------- #
def current_user_id(authorization: Optional[str] = Header(None)) -> int:
    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(status_code=401, detail="Требуется вход")
    token = authorization.split(" ", 1)[1].strip()
    uid = db.verify_token(token)
    if uid is None:
        raise HTTPException(status_code=401, detail="Сессия истекла, войдите снова")
    return uid


@app.post("/api/auth/register")
def api_register(req: AuthRequest, request: Request):
    ip = _client_ip(request)
    if _rate_limited(f"reg:{ip}", max_n=10, window=3600):
        raise HTTPException(status_code=429, detail="Слишком много регистраций. Попробуйте позже.")
    try:
        user = db.create_user(req.username, req.password)
    except db.UserExistsError as exc:
        raise HTTPException(status_code=409, detail=str(exc))
    _rate_record(f"reg:{ip}")
    return {"token": db.make_token(user["id"]), "username": user["username"], "is_admin": user.get("is_admin", False)}


@app.post("/api/auth/login")
def api_login(req: AuthRequest, request: Request):
    ip = _client_ip(request)
    key = f"login:{ip}:{req.username.strip().lower()}"
    if _rate_limited(key, max_n=5, window=300):
        raise HTTPException(status_code=429, detail="Слишком много попыток входа. Попробуйте через несколько минут.")
    user = db.get_user_by_username(req.username)
    if not user or not db.verify_password(req.password, user["password_hash"]):
        _rate_record(key)  # учитываем только НЕудачные попытки
        raise HTTPException(status_code=401, detail="Неверное имя пользователя или пароль")
    _RATE.pop(key, None)  # успешный вход — сбрасываем счётчик
    return {"token": db.make_token(user["id"]), "username": user["username"], "is_admin": bool(user["is_admin"])}


@app.get("/api/auth/me")
def api_me(uid: int = Depends(current_user_id)):
    user = db.get_user_by_id(uid)
    if not user:
        raise HTTPException(status_code=401, detail="Пользователь не найден")
    sub = db.get_subscription(uid)
    return {"username": user["username"], "is_admin": bool(user["is_admin"]),
            "premium": db.is_premium(uid),
            "premium_until": sub["expires_at"] if sub else None}


def require_premium(uid: int = Depends(current_user_id)) -> int:
    if not db.is_premium(uid):
        raise HTTPException(status_code=402, detail="Доступно по подписке «Премиум»")
    return uid


# --------------------------------------------------------------------------- #
#  Подписка (ЮKassa)
# --------------------------------------------------------------------------- #
class BillingCreate(BaseModel):
    plan: str = Field(..., pattern="^(month|year)$")


@app.get("/api/billing/plans")
def api_billing_plans():
    return {p: {"price": price, "days": days} for p, (price, days) in payments.PLANS.items()}


@app.post("/api/billing/create")
def api_billing_create(body: BillingCreate, request: Request, uid: int = Depends(current_user_id)):
    return_url = str(request.base_url).rstrip("/") + "/?payment=check"
    try:
        url = payments.create_payment(uid, body.plan, return_url)
    except payments.NotConfiguredError as exc:
        raise HTTPException(status_code=503, detail=str(exc))
    except Exception:
        logger.exception("Ошибка создания платежа")
        raise HTTPException(status_code=502, detail="Платёжный сервис недоступен. Попробуйте позже.")
    return {"confirmation_url": url}


@app.post("/api/billing/check")
def api_billing_check(uid: int = Depends(current_user_id)):
    try:
        return payments.check_payments(uid)
    except payments.NotConfiguredError as exc:
        raise HTTPException(status_code=503, detail=str(exc))
    except Exception:
        logger.exception("Ошибка проверки платежа")
        raise HTTPException(status_code=502, detail="Платёжный сервис недоступен. Попробуйте позже.")


@app.post("/api/auth/logout")
def api_logout(authorization: Optional[str] = Header(None)):
    # Реальный выход: отзываем предъявленный токен (до истечения срока он больше не примется).
    if authorization and authorization.lower().startswith("bearer "):
        db.revoke_token(authorization.split(" ", 1)[1].strip())
    return {"ok": True}


def require_admin(uid: int = Depends(current_user_id)) -> int:
    if not db.is_admin(uid):
        raise HTTPException(status_code=403, detail="Доступ только для администратора")
    return uid


@app.get("/api/admin/stats")
def api_admin_stats(uid: int = Depends(require_admin)):
    return db.admin_stats()


@app.get("/api/admin/users")
def api_admin_users(uid: int = Depends(require_admin)):
    return db.list_all_users()


@app.delete("/api/admin/users/{user_id}")
def api_admin_delete_user(user_id: int, uid: int = Depends(require_admin)):
    if not db.admin_delete_user(user_id):
        raise HTTPException(status_code=400, detail="Нельзя удалить этого пользователя")
    return {"ok": True}


class TextOverride(BaseModel):
    key: str = Field(..., min_length=1, max_length=200)
    ru: str = Field(..., max_length=8000)
    en: str = Field(..., max_length=8000)


class TextKey(BaseModel):
    key: str = Field(..., min_length=1, max_length=200)


@app.get("/api/admin/texts")
def api_admin_texts(uid: int = Depends(require_admin),
                    search: str = Query("", max_length=200),
                    overridden: bool = Query(False),
                    offset: int = Query(0, ge=0),
                    limit: int = Query(60, ge=1, le=200)):
    return content_store.catalog(search=search, only_overridden=overridden,
                                 offset=offset, limit=limit)


def _admin_username(uid: int) -> Optional[str]:
    u = db.get_user_by_id(uid)
    return u["username"] if u else None


@app.post("/api/admin/texts")
def api_admin_set_text(body: TextOverride, uid: int = Depends(require_admin)):
    if not content_store.set_override(body.key, body.ru, body.en):
        raise HTTPException(status_code=404, detail="Неизвестный ключ текста")
    db.add_text_audit(uid, _admin_username(uid), body.key, "edit")
    return {"ok": True}


@app.post("/api/admin/texts/reset")
def api_admin_reset_text(body: TextKey, uid: int = Depends(require_admin)):
    if not content_store.reset(body.key):
        raise HTTPException(status_code=404, detail="Неизвестный ключ текста")
    db.add_text_audit(uid, _admin_username(uid), body.key, "reset")
    return {"ok": True}


@app.get("/api/admin/texts/audit")
def api_admin_texts_audit(uid: int = Depends(require_admin), limit: int = Query(100, ge=1, le=500)):
    return db.list_text_audit(limit=limit)


# --------------------------------------------------------------------------- #
#  Сохранённые карты
# --------------------------------------------------------------------------- #
@app.get("/api/profiles")
def api_list_profiles(uid: int = Depends(current_user_id)):
    return db.list_profiles(uid)


@app.post("/api/profiles")
def api_add_profile(profile: ProfileIn, uid: int = Depends(current_user_id)):
    return db.add_profile(uid, profile.label, profile.data)


@app.delete("/api/profiles/{profile_id}")
def api_delete_profile(profile_id: int, uid: int = Depends(current_user_id)):
    if not db.delete_profile(uid, profile_id):
        raise HTTPException(status_code=404, detail="Карта не найдена")
    return {"ok": True}


# --------------------------------------------------------------------------- #
#  API
# --------------------------------------------------------------------------- #
@app.post("/api/natal")
def api_natal(birth: BirthData):
    try:
        return astrology.natal_report(birth.model_dump())
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    except HTTPException:
        raise
    except Exception:
        logger.exception("Ошибка расчёта")
        raise HTTPException(status_code=400, detail="Не удалось выполнить расчёт. Проверьте корректность введённых данных.")


@app.post("/api/transit")
def api_transit(req: TransitRequest):
    try:
        return astrology.transit_report(
            natal_params=req.natal.model_dump(),
            transit_dt=req.transit_date.model_dump(),
            transit_location=req.transit_location.model_dump() if req.transit_location else None,
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    except HTTPException:
        raise
    except Exception:
        logger.exception("Ошибка расчёта")
        raise HTTPException(status_code=400, detail="Не удалось выполнить расчёт. Проверьте корректность введённых данных.")


@app.post("/api/return")
def api_return(req: ReturnRequest):
    try:
        return astrology.return_report(
            natal_params=req.natal.model_dump(),
            year=req.year,
            month=req.month,
            return_type=req.return_type,
            location=req.location.model_dump() if req.location else None,
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    except HTTPException:
        raise
    except Exception:
        logger.exception("Ошибка расчёта")
        raise HTTPException(status_code=400, detail="Не удалось выполнить расчёт. Проверьте корректность введённых данных.")


@app.post("/api/progression")
def api_progression(req: ProgressionRequest):
    try:
        return astrology.progression_report(
            natal_params=req.natal.model_dump(),
            target_date=req.target_date.model_dump(),
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    except HTTPException:
        raise
    except Exception:
        logger.exception("Ошибка расчёта")
        raise HTTPException(status_code=400, detail="Не удалось выполнить расчёт. Проверьте корректность введённых данных.")


@app.post("/api/calendar")
def api_calendar(req: CalendarRequest):
    try:
        return astrology.transit_calendar_report(
            natal_params=req.natal.model_dump(),
            start=req.start.model_dump(),
            end=req.end.model_dump(),
            location=req.location.model_dump() if req.location else None,
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    except HTTPException:
        raise
    except Exception:
        logger.exception("Ошибка расчёта")
        raise HTTPException(status_code=400, detail="Не удалось выполнить расчёт. Проверьте корректность введённых данных.")


@app.post("/api/forecast")
def api_forecast(req: ForecastRequest, uid: int = Depends(require_premium)):
    try:
        return astrology.forecast_report(
            natal_params=req.natal.model_dump(),
            start=req.start.model_dump(),
            end=req.end.model_dump(),
            location=req.location.model_dump() if req.location else None,
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    except HTTPException:
        raise
    except Exception:
        logger.exception("Ошибка расчёта")
        raise HTTPException(status_code=400, detail="Не удалось выполнить расчёт. Проверьте корректность введённых данных.")


@app.post("/api/rectification")
def api_rectification(req: RectificationRequest, request: Request, uid: int = Depends(require_premium)):
    # Самый тяжёлый расчёт (до ~1440 карт за запрос) — лимитируем per-IP от CPU-DoS.
    ip = _client_ip(request)
    if _rate_limited(f"rect:{ip}", max_n=10, window=60):
        raise HTTPException(status_code=429, detail="Слишком много запросов. Попробуйте через минуту.")
    _rate_record(f"rect:{ip}")
    try:
        return astrology.rectification_report(
            natal_params=req.natal.model_dump(),
            events=[e.model_dump() for e in req.events],
            asc_traits=req.asc_traits,
            predispositions=[p.model_dump() for p in req.predispositions],
            parent_sun_signs=req.parent_sun_signs,
            start_minute=req.start_minute,
            end_minute=req.end_minute,
            step_minute=req.step_minute,
            center_minute=req.center_minute,
            window_minutes=req.window_minutes,
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    except HTTPException:
        raise
    except Exception:
        logger.exception("Ошибка расчёта")
        raise HTTPException(status_code=400, detail="Не удалось выполнить расчёт. Проверьте корректность введённых данных.")


class VedicRequest(BaseModel):
    year: int = Field(..., ge=1, le=3000)
    month: int = Field(..., ge=1, le=12)
    lat: float = Field(..., ge=-90, le=90)
    lng: float = Field(..., ge=-180, le=180)
    tz_str: Optional[str] = None
    natal: Optional[BirthData] = None  # для персонализации (Тарабала)
    lang: str = "ru"


@app.post("/api/vedic-calendar")
def api_vedic(req: VedicRequest):
    try:
        tz = req.tz_str or astrology.resolve_timezone(req.lat, req.lng)
        birth_nak = None
        if req.natal:
            model = astrology.build_subject(**req.natal.model_dump())
            birth_nak = vedic.birth_nakshatra_from_jd(model.julian_day)
        return vedic.vedic_calendar(req.year, req.month, req.lat, req.lng, tz, birth_nak, req.lang)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    except HTTPException:
        raise
    except Exception:
        logger.exception("Ошибка расчёта")
        raise HTTPException(status_code=400, detail="Не удалось выполнить расчёт. Проверьте корректность введённых данных.")


@app.post("/api/synastry")
def api_synastry(req: SynastryRequest, uid: int = Depends(require_premium)):
    try:
        return astrology.synastry_report(
            person_a=req.person_a.model_dump(),
            person_b=req.person_b.model_dump(),
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    except HTTPException:
        raise
    except Exception:
        logger.exception("Ошибка расчёта")
        raise HTTPException(status_code=400, detail="Не удалось выполнить расчёт. Проверьте корректность введённых данных.")


@app.get("/api/geocode")
def api_geocode(q: str = Query(..., min_length=2), request: Request = None):
    """Поиск города через OpenStreetMap Nominatim (бесплатно, без ключа)."""
    # Лимит per-IP: не дать превратить сервер в открытый прокси Nominatim (бан от OSM).
    ip = _client_ip(request)
    if _rate_limited(f"geo:{ip}", max_n=30, window=60):
        raise HTTPException(status_code=429, detail="Слишком много запросов. Попробуйте через минуту.")
    _rate_record(f"geo:{ip}")
    try:
        resp = requests.get(
            "https://nominatim.openstreetmap.org/search",
            params={"q": q, "format": "json", "limit": 6, "accept-language": "ru"},
            headers={"User-Agent": "astro-natal-site/0.1 (educational project)"},
            timeout=10,
        )
        resp.raise_for_status()
        items = resp.json()
    except Exception as exc:
        logger.warning("Геокодинг недоступен: %s", exc)
        raise HTTPException(status_code=502, detail="Сервис геокодинга временно недоступен. Попробуйте позже или введите координаты вручную.")

    results = []
    for it in items:
        lat = float(it["lat"])
        lon = float(it["lon"])
        results.append(
            {
                "display_name": it.get("display_name", ""),
                "lat": round(lat, 5),
                "lng": round(lon, 5),
                "tz_str": astrology.resolve_timezone(lat, lon),
            }
        )
    return results


@app.get("/api/reverse-geocode")
def api_reverse_geocode(lat: float = Query(..., ge=-90, le=90), lng: float = Query(..., ge=-180, le=180),
                        request: Request = None):
    """Обратный геокодинг: по координатам определить населённый пункт (OpenStreetMap Nominatim)."""
    ip = _client_ip(request)
    if _rate_limited(f"geo:{ip}", max_n=30, window=60):
        raise HTTPException(status_code=429, detail="Слишком много запросов. Попробуйте через минуту.")
    _rate_record(f"geo:{ip}")
    try:
        resp = requests.get(
            "https://nominatim.openstreetmap.org/reverse",
            params={"lat": lat, "lon": lng, "format": "json", "accept-language": "ru", "zoom": 10},
            headers={"User-Agent": "astro-natal-site/0.1 (educational project)"},
            timeout=10,
        )
        resp.raise_for_status()
        data = resp.json()
    except Exception as exc:
        logger.warning("Обратный геокодинг недоступен: %s", exc)
        raise HTTPException(status_code=502, detail="Сервис геокодинга временно недоступен.")

    addr = data.get("address", {}) or {}
    city = (addr.get("city") or addr.get("town") or addr.get("village")
            or addr.get("municipality") or addr.get("hamlet") or addr.get("county") or "")
    country = addr.get("country", "")
    short = ", ".join([p for p in (city, country) if p]) or data.get("display_name", "")
    return {
        "display_name": data.get("display_name", ""),
        "short": short,
        "tz_str": astrology.resolve_timezone(lat, lng),
    }


@app.get("/api/archetypes")
def api_archetypes(lang: str = "ru"):
    """Справочник архетипов знаков (астропсихология)."""
    from . import interpretations as interp

    return interp.archetypes_list(lang)


@app.get("/api/meta")
def api_meta():
    """Справочные данные для интерфейса (системы домов и т.п.)."""
    return {
        "house_systems": constants.HOUSE_SYSTEMS,
    }


# --------------------------------------------------------------------------- #
#  Раздача фронтенда
# --------------------------------------------------------------------------- #
app.include_router(seo.router)  # SEO-страницы трактовок, sitemap.xml, robots.txt


@app.get("/")
def index():
    return FileResponse(FRONTEND_DIR / "index.html")


app.mount("/", StaticFiles(directory=str(FRONTEND_DIR), html=True), name="static")

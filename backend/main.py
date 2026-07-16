# -*- coding: utf-8 -*-
"""FastAPI-приложение: API расчётов + раздача фронтенда."""
from __future__ import annotations

import logging
import time
from collections import defaultdict
from datetime import datetime, timedelta, timezone
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Optional

import requests
from fastapi import Depends, FastAPI, Header, HTTPException, Query, Request
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field, field_validator

import re

from . import astrology, constants, content_store, db, emailer, payments, seo, vedic

logger = logging.getLogger("astro")

FRONTEND_DIR = Path(__file__).resolve().parent.parent / "frontend"

# Content-Security-Policy (defense-in-depth поверх экранирования ввода).
# 'sha256-...' разрешает единственный инлайн-скрипт темы (frontend/index.html).
# ВАЖНО: если правишь тот инлайн-скрипт — пересчитай хэш, иначе тема сломается.
# script-src НЕ содержит 'unsafe-inline' намеренно (иначе XSS-защита обнулится).
# style-src 'unsafe-inline' нужен из-за массовых inline style="..." в разметке и SVG.
CSP_POLICY = (
    "default-src 'self'; "
    "script-src 'self' 'sha256-KjK3Wt2+9ybbK9W/LL5ICjwaSgZmiEjQhTh5+xrBeEA='; "
    "style-src 'self' 'unsafe-inline'; "
    "font-src 'self'; "
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


EMAIL_RE = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")


class AuthRequest(BaseModel):
    username: str = Field(..., min_length=2, max_length=50)
    password: str = Field(..., min_length=8, max_length=200)


class RegisterRequest(AuthRequest):
    email: str = Field("", max_length=120)
    lang: str = "ru"

    @field_validator("email")
    @classmethod
    def _check_email(cls, v: str) -> str:
        v = v.strip().lower()
        if v and not EMAIL_RE.match(v):
            raise ValueError("Некорректный адрес почты")
        return v


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
    if db.is_banned(uid):
        raise HTTPException(status_code=403, detail="Аккаунт заблокирован")
    return uid


def _send_verify_email(request: Request, user_id: int, email: str, lang: str = "ru") -> bool:
    """Отправить письмо подтверждения. Возвращает True, если письмо реально принято сервером."""
    if not emailer.is_configured():
        return False
    token = db.create_email_token(user_id, "verify", 24 * 3600)
    link = str(request.base_url).rstrip("/") + f"/?email-token={token}"
    subject, body = emailer.verify_letter(link, lang)
    try:
        emailer.send(email, subject, body)
        return True
    except Exception:
        logger.exception("Не удалось отправить письмо подтверждения")
        return False


@app.post("/api/auth/register")
def api_register(req: RegisterRequest, request: Request):
    ip = _client_ip(request)
    if _rate_limited(f"reg:{ip}", max_n=10, window=3600):
        raise HTTPException(status_code=429, detail="Слишком много регистраций. Попробуйте позже.")
    if not req.email:
        raise HTTPException(status_code=422, detail="Укажите почту — она нужна для чека об оплате и восстановления пароля")
    try:
        user = db.create_user(req.username, req.password, req.email)
    except db.UserExistsError as exc:
        raise HTTPException(status_code=409, detail=str(exc))
    _rate_record(f"reg:{ip}")
    _send_verify_email(request, user["id"], req.email, req.lang)
    return {"token": db.make_token(user["id"]), "username": user["username"], "is_admin": user.get("is_admin", False)}


@app.post("/api/auth/login")
def api_login(req: AuthRequest, request: Request):
    ip = _client_ip(request)
    key = f"login:{ip}:{req.username.strip().lower()}"
    if _rate_limited(key, max_n=5, window=300):
        raise HTTPException(status_code=429, detail="Слишком много попыток входа. Попробуйте через несколько минут.")
    # Вход по имени пользователя или по почте — что ввели, то и ищем.
    ident = req.username.strip()
    user = db.get_user_by_email(ident) if "@" in ident else db.get_user_by_username(ident)
    if not user or not db.verify_password(req.password, user["password_hash"]):
        _rate_record(key)  # учитываем только НЕудачные попытки
        raise HTTPException(status_code=401, detail="Неверное имя пользователя или пароль")
    if user["is_banned"]:
        raise HTTPException(status_code=403, detail="Аккаунт заблокирован")
    _RATE.pop(key, None)  # успешный вход — сбрасываем счётчик
    return {"token": db.make_token(user["id"]), "username": user["username"], "is_admin": bool(user["is_admin"])}


class EmailAttach(BaseModel):
    email: str = Field(..., min_length=5, max_length=120)
    lang: str = "ru"

    @field_validator("email")
    @classmethod
    def _check_email(cls, v: str) -> str:
        v = v.strip().lower()
        if not EMAIL_RE.match(v):
            raise ValueError("Некорректный адрес почты")
        return v


@app.post("/api/auth/email")
def api_attach_email(req: EmailAttach, request: Request, uid: int = Depends(current_user_id)):
    """Привязать/сменить почту у существующего аккаунта."""
    ip = _client_ip(request)
    if _rate_limited(f"email:{ip}", max_n=5, window=3600):
        raise HTTPException(status_code=429, detail="Слишком много запросов. Попробуйте позже.")
    if not db.set_user_email(uid, req.email):
        raise HTTPException(status_code=409, detail="Эта почта уже привязана к другому аккаунту")
    _rate_record(f"email:{ip}")
    sent = _send_verify_email(request, uid, req.email, req.lang)
    return {"ok": True, "sent": sent}


class EmailToken(BaseModel):
    token: str = Field(..., min_length=10, max_length=200)


@app.post("/api/auth/verify-email")
def api_verify_email(req: EmailToken):
    uid = db.consume_email_token(req.token, "verify")
    if uid is None:
        raise HTTPException(status_code=400, detail="Ссылка недействительна или устарела")
    db.mark_email_verified(uid)
    gift = db.grant_welcome_report(uid)  # лид-магнит: 1 бесплатный PDF за подтверждение почты
    return {"ok": True, "report_gift": gift}


class ForgotRequest(BaseModel):
    email: str = Field(..., min_length=5, max_length=120)
    lang: str = "ru"


@app.post("/api/auth/forgot")
def api_forgot(req: ForgotRequest, request: Request):
    """Запрос сброса пароля. Ответ всегда ok — не раскрываем, есть ли такая почта."""
    ip = _client_ip(request)
    if _rate_limited(f"forgot:{ip}", max_n=5, window=3600):
        raise HTTPException(status_code=429, detail="Слишком много запросов. Попробуйте позже.")
    _rate_record(f"forgot:{ip}")
    if not emailer.is_configured():
        raise HTTPException(status_code=503, detail="Отправка писем временно недоступна")
    user = db.get_user_by_email(req.email)
    if user:
        token = db.create_email_token(user["id"], "reset", 3600)
        link = str(request.base_url).rstrip("/") + f"/?reset-token={token}"
        subject, body = emailer.reset_letter(link, req.lang)
        try:
            emailer.send(user["email"], subject, body)
        except Exception:
            logger.exception("Не удалось отправить письмо сброса пароля")
    return {"ok": True}


class ResetRequest(BaseModel):
    token: str = Field(..., min_length=10, max_length=200)
    new_password: str = Field(..., min_length=8, max_length=200)


@app.post("/api/auth/reset")
def api_reset(req: ResetRequest):
    uid = db.consume_email_token(req.token, "reset")
    if uid is None:
        raise HTTPException(status_code=400, detail="Ссылка недействительна или устарела. Запросите сброс ещё раз.")
    db.set_password(uid, req.new_password)
    return {"ok": True}


@app.get("/api/auth/me")
def api_me(uid: int = Depends(current_user_id)):
    user = db.get_user_by_id(uid)
    if not user:
        raise HTTPException(status_code=401, detail="Пользователь не найден")
    sub = db.get_subscription(uid)
    return {"username": user["username"], "is_admin": bool(user["is_admin"]),
            "email": user["email"], "email_verified": bool(user["email_verified"]),
            "premium": db.is_premium(uid),
            "premium_until": sub["expires_at"] if sub else None,
            "consultation": db.has_consultation(uid),
            "report_credits": db.get_report_credits(uid),
            "primary_profile_id": user["primary_profile_id"],
            "notify_weekly": bool(user["notify_weekly"])}


def require_premium(uid: int = Depends(current_user_id)) -> int:
    if not db.is_premium(uid):
        raise HTTPException(status_code=402, detail="Доступно по подписке «Премиум»")
    return uid


# --------------------------------------------------------------------------- #
#  Подписка (ЮKassa)
# --------------------------------------------------------------------------- #
class BillingCreate(BaseModel):
    plan: str = Field(..., pattern="^(month|year|plus_month|plus_year|consult|report)$")


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
        result = payments.check_payments(uid)
        result["consultation"] = db.has_consultation(uid)
        result["report_credits"] = db.get_report_credits(uid)
        return result
    except payments.NotConfiguredError as exc:
        raise HTTPException(status_code=503, detail=str(exc))
    except Exception:
        logger.exception("Ошибка проверки платежа")
        raise HTTPException(status_code=502, detail="Платёжный сервис недоступен. Попробуйте позже.")


class PasswordChange(BaseModel):
    old_password: str = Field(..., min_length=1, max_length=200)
    new_password: str = Field(..., min_length=8, max_length=200)


@app.post("/api/auth/password")
def api_change_password(req: PasswordChange, uid: int = Depends(current_user_id)):
    if not db.change_password(uid, req.old_password, req.new_password):
        raise HTTPException(status_code=400, detail="Текущий пароль неверен")
    return {"ok": True}


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


class PremiumGrant(BaseModel):
    days: int = Field(..., ge=0, le=3650)  # 0 = снять подписку


@app.post("/api/admin/users/{user_id}/premium")
def api_admin_premium(user_id: int, body: PremiumGrant, uid: int = Depends(require_admin)):
    if not db.admin_set_premium(user_id, body.days):
        raise HTTPException(status_code=404, detail="Пользователь не найден")
    return {"ok": True}


class BanRequest(BaseModel):
    banned: bool


@app.post("/api/admin/users/{user_id}/ban")
def api_admin_ban(user_id: int, body: BanRequest, uid: int = Depends(require_admin)):
    if not db.admin_set_banned(user_id, body.banned):
        raise HTTPException(status_code=400, detail="Нельзя заблокировать этого пользователя")
    return {"ok": True}


@app.get("/api/admin/payments")
def api_admin_payments(uid: int = Depends(require_admin)):
    data = db.list_payments()
    prices = {p: price for p, (price, _d) in payments.PLANS.items()}
    for it in data["items"]:
        it["amount"] = prices.get(it["plan"], 0)
        it["plan_title"] = payments.PLAN_TITLES.get(it["plan"], it["plan"])
    ok = [it for it in data["items"] if it["status"] == "succeeded"]
    month_ago = (datetime.now(timezone.utc) - timedelta(days=30)).isoformat()
    data["revenue_total"] = sum(it["amount"] for it in ok)
    data["revenue_30d"] = sum(it["amount"] for it in ok if it["created_at"] >= month_ago)
    return data


@app.get("/api/admin/usage")
def api_admin_usage(uid: int = Depends(require_admin)):
    return db.usage_summary(days=30)


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


class NoteIn(BaseModel):
    note: str = Field("", max_length=500)

    @field_validator("note")
    @classmethod
    def _strip_markup(cls, v: str) -> str:
        return v.replace("<", "").replace(">", "").replace("&", "").strip()


@app.post("/api/profiles/{profile_id}/note")
def api_profile_note(profile_id: int, body: NoteIn, uid: int = Depends(current_user_id)):
    if not db.set_profile_note(uid, profile_id, body.note):
        raise HTTPException(status_code=404, detail="Карта не найдена")
    return {"ok": True}


# --------------------------------------------------------------------------- #
#  Кабинет: история расчётов и свои платежи
# --------------------------------------------------------------------------- #
class HistoryIn(BaseModel):
    kind: str = Field(..., pattern="^(natal|transit|synastry|return|progression|calendar|forecast|rectification|vedic)$")
    label: str = Field("", max_length=160)
    params: dict

    @field_validator("label")
    @classmethod
    def _strip_markup(cls, v: str) -> str:
        return v.replace("<", "").replace(">", "").replace("&", "").strip()


@app.post("/api/history")
def api_add_history(body: HistoryIn, uid: int = Depends(current_user_id)):
    db.add_history(uid, body.kind, body.label, body.params)
    return {"ok": True}


@app.get("/api/history")
def api_list_history(uid: int = Depends(current_user_id)):
    return db.list_history(uid)


@app.get("/api/billing/my")
def api_my_payments(uid: int = Depends(current_user_id)):
    prices = {p: price for p, (price, _d) in payments.PLANS.items()}
    items = db.list_user_payments(uid)
    for it in items:
        it["amount"] = prices.get(it["plan"], 0)
    return {"items": items}


@app.post("/api/report/consume")
def api_report_consume(uid: int = Depends(current_user_id)):
    """Списать один разовый PDF-кредит (после успешного скачивания отчёта).
    Премиуму кредиты не нужны — у него PDF безлимитный, ничего не списываем."""
    if db.is_premium(uid):
        return {"ok": True, "premium": True, "report_credits": db.get_report_credits(uid)}
    if not db.consume_report_credit(uid):
        raise HTTPException(status_code=402, detail="Нет доступных отчётов")
    return {"ok": True, "premium": False, "report_credits": db.get_report_credits(uid)}


# --------------------------------------------------------------------------- #
#  Транзит дня и еженедельная рассылка
# --------------------------------------------------------------------------- #
def daily_top_aspects(natal_params: dict, when: datetime, limit: int = 3) -> list[dict]:
    """Топ-N самых точных транзитов к натальной карте на дату (для карточки «Ваш день»)."""
    rep = astrology.transit_report(
        natal_params=natal_params,
        transit_dt={"year": when.year, "month": when.month, "day": when.day, "hour": 12, "minute": 0},
        with_svg=False,
    )
    asp = [a for a in rep.get("aspects", []) if a.get("interp")]
    asp.sort(key=lambda a: a.get("orbit", 99))
    keep = ("p1_ru", "p1_symbol", "p2_ru", "p2_symbol", "aspect_ru", "aspect_symbol",
            "nature", "nature_label", "orbit", "interp")
    return [{k: a.get(k) for k in keep} for a in asp[:limit]]


@app.get("/api/daily")
def api_daily(uid: int = Depends(current_user_id)):
    """Транзит дня для основного человека. Free-функция; премиуму открыт прогноз вперёд."""
    primary = db.get_primary_profile(uid)
    if not primary:
        return {"has_primary": False, "premium": db.is_premium(uid)}
    params = dict(primary["data"])
    params["lang"] = "ru"
    try:
        aspects = daily_top_aspects(params, datetime.now(timezone.utc))
    except Exception:
        logger.exception("Ошибка расчёта транзита дня")
        raise HTTPException(status_code=400, detail="Не удалось рассчитать транзит дня")
    return {
        "has_primary": True,
        "person": primary["label"],
        "date": datetime.now(timezone.utc).date().isoformat(),
        "aspects": aspects,
        "premium": db.is_premium(uid),
    }


class PrimaryIn(BaseModel):
    profile_id: Optional[int] = None


@app.post("/api/profiles/primary")
def api_set_primary(body: PrimaryIn, uid: int = Depends(current_user_id)):
    if not db.set_primary_profile(uid, body.profile_id):
        raise HTTPException(status_code=404, detail="Карта не найдена")
    return {"ok": True}


class NotifyIn(BaseModel):
    weekly: bool


@app.post("/api/notify")
def api_set_notify(body: NotifyIn, uid: int = Depends(current_user_id)):
    user = db.get_user_by_id(uid)
    if body.weekly and not (user and user["email"] and user["email_verified"]):
        raise HTTPException(status_code=400, detail="Сначала подтвердите почту в кабинете")
    db.set_notify_weekly(uid, body.weekly)
    return {"ok": True}


class SupportIn(BaseModel):
    message: str = Field(..., min_length=1, max_length=4000)
    email: str = Field("", max_length=200)
    name: str = Field("", max_length=120)

    @field_validator("message", "email", "name")
    @classmethod
    def _strip_markup(cls, v: str) -> str:
        # Текст уходит в письмо и в БД (позже, возможно, в админку) — вырезаем разметку.
        if isinstance(v, str):
            return v.replace("<", "").replace(">", "").replace("&", "").strip()
        return v


@app.post("/api/support")
def api_support(body: SupportIn, request: Request, authorization: Optional[str] = Header(None)):
    """Обращение из формы поддержки: сохраняем в БД (страховка) и шлём оператору на почту."""
    ip = _client_ip(request)
    if _rate_limited(f"support:{ip}", max_n=5, window=3600):
        raise HTTPException(status_code=429, detail="Слишком много сообщений. Попробуйте позже.")
    # Опциональный вход: если есть валидный токен — подставим аккаунт и почту.
    uid = None
    email = body.email
    if authorization and authorization.lower().startswith("bearer "):
        uid = db.verify_token(authorization.split(" ", 1)[1].strip())
        if uid and not email:
            user = db.get_user_by_id(uid)
            if user and user["email"]:
                email = user["email"]
    message = body.message
    if not message:
        raise HTTPException(status_code=400, detail="Пустое сообщение")
    name = body.name
    db.add_support_message(uid, name, email, message)  # сохраняем всегда — не потерять
    if emailer.is_configured():
        subject, text = emailer.support_letter(name, email, message, uid)
        try:
            emailer.send(emailer.support_to(), subject, text, reply_to=email or None)
        except Exception:
            logger.exception("Не удалось отправить письмо поддержки")
    _rate_record(f"support:{ip}")
    return {"ok": True}


@app.get("/api/unsubscribe")
def api_unsubscribe(token: str = Query("", max_length=100)):
    """Отписка по ссылке из письма — без входа. Всегда отвечаем страницей-подтверждением."""
    db.unsubscribe_by_token(token)
    html = ("<!DOCTYPE html><html lang='ru'><head><meta charset='utf-8'>"
            "<meta name='viewport' content='width=device-width,initial-scale=1'>"
            "<title>Отписка</title></head>"
            "<body style='font-family:system-ui,sans-serif;max-width:520px;margin:60px auto;padding:0 20px;text-align:center'>"
            "<h2>Вы отписались от еженедельного дайджеста</h2>"
            "<p>Больше писем с прогнозом мы присылать не будем. "
            "Включить рассылку снова можно в кабинете на "
            "<a href='https://astrosmap.ru'>astrosmap.ru</a>.</p></body></html>")
    from fastapi.responses import HTMLResponse
    return HTMLResponse(content=html)


# --------------------------------------------------------------------------- #
#  API
# --------------------------------------------------------------------------- #
@app.post("/api/natal")
def api_natal(birth: BirthData, svg: bool = Query(True)):
    # svg=0 — для мобильного приложения: тексты без тяжёлого SVG (колесо рисует само).
    db.record_usage("natal")
    try:
        return astrology.natal_report(birth.model_dump(), with_svg=svg)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    except HTTPException:
        raise
    except Exception:
        logger.exception("Ошибка расчёта")
        raise HTTPException(status_code=400, detail="Не удалось выполнить расчёт. Проверьте корректность введённых данных.")


@app.post("/api/transit")
def api_transit(req: TransitRequest, svg: bool = Query(True)):
    db.record_usage("transit")
    try:
        return astrology.transit_report(
            natal_params=req.natal.model_dump(),
            transit_dt=req.transit_date.model_dump(),
            transit_location=req.transit_location.model_dump() if req.transit_location else None,
            with_svg=svg,
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    except HTTPException:
        raise
    except Exception:
        logger.exception("Ошибка расчёта")
        raise HTTPException(status_code=400, detail="Не удалось выполнить расчёт. Проверьте корректность введённых данных.")


@app.post("/api/return")
def api_return(req: ReturnRequest, uid: int = Depends(require_premium)):
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
def api_calendar(req: CalendarRequest, uid: int = Depends(require_premium)):
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
def api_forecast(req: ForecastRequest):
    db.record_usage("forecast")
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
    db.record_usage("rectification")
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
    db.record_usage("synastry")
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


@app.post("/api/synastry/preview")
def api_synastry_preview(req: SynastryRequest, request: Request):
    """Бесплатный тизер совместимости: индекс + тон по сферам, без детального разбора.
    Детальный разбор, композит, аспекты и карта остаются в «Премиуме» (/api/synastry)."""
    ip = _client_ip(request)
    if _rate_limited(f"synprev:{ip}", max_n=20, window=3600):
        raise HTTPException(status_code=429, detail="Слишком много запросов. Попробуйте позже.")
    _rate_record(f"synprev:{ip}")
    db.record_usage("synastry_preview")
    try:
        full = astrology.synastry_report(
            person_a=req.person_a.model_dump(),
            person_b=req.person_b.model_dump(),
            with_svg=False,
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    except Exception:
        logger.exception("Ошибка расчёта")
        raise HTTPException(status_code=400, detail="Не удалось выполнить расчёт. Проверьте корректность введённых данных.")
    couple = full.get("couple", {})
    return {
        "a_name": full["a_meta"]["name"],
        "b_name": full["b_meta"]["name"],
        "score": {
            "value": full["score"]["value"],
            "description_ru": full["score"]["description_ru"],
            "is_destiny_sign": full["score"]["is_destiny_sign"],
        },
        # Только ярлык и тон по сферам — текст разбора и советы прячем под подписку.
        "spheres": [{"key": s["key"], "label": s["label"], "tone": s["tone"]}
                    for s in couple.get("spheres", [])],
        "strength_count": len(couple.get("strengths", [])),
        "challenge_count": len(couple.get("challenges", [])),
    }


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


@app.get("/api/planets")
def api_planets(lang: str = "ru"):
    """Полные описания планет (для всплывающего окна в справочнике архетипов)."""
    from . import interpretations as interp

    return interp.planets_info(lang)


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

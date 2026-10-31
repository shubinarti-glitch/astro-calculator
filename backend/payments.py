# -*- coding: utf-8 -*-
"""Платная подписка через ЮKassa (redirect-flow).

Схема: create_payment создаёт платёж в ЮKassa и возвращает confirmation_url;
после оплаты пользователь возвращается на сайт, фронт зовёт check_payments,
мы запрашиваем статус у ЮKassa и при succeeded продлеваем подписку.
Ключи — в data/yookassa.json (боевые/тестовые вносит владелец вручную).
"""
from __future__ import annotations

import json
import uuid
from pathlib import Path

import requests

from . import db, sales
from . import robokassa

CONFIG_FILE = Path(__file__).resolve().parent.parent / "data" / "yookassa.json"
API = "https://api.yookassa.ru/v3/payments"

# план -> (цена в рублях, дней подписки)
# plus_* = подписка + разовая консультация астролога (+1500 ₽ к цене тарифа)
# consult = только консультация, без подписки (0 дней)
PLANS = {
    "month": (299, 30),
    "year": (1990, 365),
    "plus_month": (1799, 30),
    "plus_year": (3490, 365),
    "consult": (3500, 0),
    "report": (149, 0),  # разовый PDF-отчёт, без подписки (0 дней)
}

PLAN_TITLES = {
    "month": "Подписка «Премиум», месяц",
    "year": "Подписка «Премиум», год",
    "plus_month": "Подписка «Премиум+» (месяц + консультация)",
    "plus_year": "Подписка «Премиум+» (год + консультация)",
    "consult": "Консультация астролога",
    "report": "Разовый PDF-отчёт",
}


class NotConfiguredError(Exception):
    pass


def _auth() -> tuple[str, str]:
    if not CONFIG_FILE.exists():
        CONFIG_FILE.write_text(
            json.dumps({"shop_id": "", "secret_key": ""}, indent=2), encoding="utf-8"
        )
    cfg = json.loads(CONFIG_FILE.read_text(encoding="utf-8"))
    if not cfg.get("shop_id") or not cfg.get("secret_key"):
        raise NotConfiguredError("Платёжная система не настроена (data/yookassa.json)")
    return cfg["shop_id"], cfg["secret_key"]


def create_payment(user_id: int, plan: str, return_url: str, attribution: dict | None = None) -> str:
    """Create new payments through Robokassa; YooKassa remains for historical payments."""
    price, _days = PLANS[plan]
    user = db.get_user_by_id(user_id)
    receipt_email = user["email"] if user and user["email_verified"] else None
    return robokassa.create_payment(user_id, plan, return_url, price, attribution, receipt_email)


def _apply_status(payment_id: str, user_id: int, plan: str, status: str) -> bool:
    """Apply status and rights atomically; repeated and concurrent calls are safe."""
    return sales.apply_status(payment_id, user_id, plan, status, PLANS[plan][1])


def reconcile_one(payment_id: str) -> bool:
    """Перепроверить один платёж в ЮKassa (авторитетно) и применить статус, если у нас ещё
    pending. Источник истины — API ЮKassa, не тело вебхука. Возвращает True при активации."""
    rec = db.get_pending_payment(payment_id)
    if not rec:
        return False  # неизвестный или уже обработанный платёж — ничего не делаем
    if sales.payment_provider(payment_id) == "robokassa":
        # Robokassa owns the authoritative ResultURL callback; browser return is not trusted.
        return False
    resp = requests.get(f"{API}/{payment_id}", auth=_auth(), timeout=15)
    if resp.status_code != 200:
        return False
    return _apply_status(payment_id, rec["user_id"], rec["plan"], resp.json().get("status", ""))


def reconcile_pending(older_than_min: int = 30) -> int:
    """Досверка всех висящих pending старше N минут (для админки). Возвращает число обновлённых."""
    n = 0
    for p in db.pending_payments_all(older_than_min):
        try:
            if sales.payment_provider(p["payment_id"]) == "robokassa":
                continue  # status is advanced only by signed ResultURL notification
            resp = requests.get(f"{API}/{p['payment_id']}", auth=_auth(), timeout=15)
            if resp.status_code != 200:
                continue
            status = resp.json().get("status", "")
            if status in ("succeeded", "canceled"):
                _apply_status(p["payment_id"], p["user_id"], p["plan"], status)
                n += 1
        except requests.RequestException:
            continue  # сеть/ЮKassa недоступны — пропускаем, не роняем вызывающего
    return n


def check_payments(user_id: int, attempt: str | None = None) -> dict:
    """Проверить pending-платежи пользователя; активировать подписку при успехе."""
    selected = sales.find_attempt(user_id, attempt) if attempt else None
    if attempt and selected is None:
        raise LookupError("Payment attempt not found")
    activated = False
    pending = ([selected] if selected["status"] == "pending" else []) if selected else db.pending_payments(user_id)
    for p in pending:
        if sales.payment_provider(p["payment_id"]) == "robokassa":
            continue  # signed server callback may arrive shortly after browser return
        resp = requests.get(f"{API}/{p['payment_id']}", auth=_auth(), timeout=15)
        resp.raise_for_status()
        if _apply_status(p["payment_id"], user_id, p["plan"], resp.json().get("status", "")):
            activated = True
    selected = sales.find_attempt(user_id, attempt) if attempt else None
    return {"activated": activated, "premium": db.is_premium(user_id),
            "subscription": db.get_subscription(user_id),
            "payment": {"status": selected["status"], "plan": selected["plan"]} if selected else None}

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

from . import db

CONFIG_FILE = Path(__file__).resolve().parent.parent / "data" / "yookassa.json"
API = "https://api.yookassa.ru/v3/payments"

# план -> (цена в рублях, дней подписки)
# plus_* = подписка + разовая консультация астролога (+1500 ₽ к цене тарифа)
PLANS = {
    "month": (299, 30),
    "year": (1990, 365),
    "plus_month": (1799, 30),
    "plus_year": (3490, 365),
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


def create_payment(user_id: int, plan: str, return_url: str) -> str:
    """Создать платёж, вернуть URL страницы оплаты ЮKassa."""
    price, _days = PLANS[plan]
    resp = requests.post(
        API,
        auth=_auth(),
        headers={"Idempotence-Key": str(uuid.uuid4())},
        json={
            "amount": {"value": f"{price}.00", "currency": "RUB"},
            "capture": True,
            "confirmation": {"type": "redirect", "return_url": return_url},
            "description": f"Подписка «Премиум» ({'месяц' if plan == 'month' else 'год'}), пользователь {user_id}",
            "metadata": {"user_id": user_id, "plan": plan},
        },
        timeout=15,
    )
    resp.raise_for_status()
    data = resp.json()
    db.add_payment(data["id"], user_id, plan)
    return data["confirmation"]["confirmation_url"]


def check_payments(user_id: int) -> dict:
    """Проверить pending-платежи пользователя; активировать подписку при успехе."""
    auth = _auth()
    activated = False
    for p in db.pending_payments(user_id):
        resp = requests.get(f"{API}/{p['payment_id']}", auth=auth, timeout=15)
        if resp.status_code != 200:
            continue
        status = resp.json().get("status")
        if status == "succeeded":
            db.set_payment_status(p["payment_id"], "succeeded")
            db.extend_subscription(user_id, p["plan"], PLANS[p["plan"]][1])
            if p["plan"].startswith("plus_"):
                db.add_consultation(user_id)
            activated = True
        elif status == "canceled":
            db.set_payment_status(p["payment_id"], "canceled")
    return {"activated": activated, "premium": db.is_premium(user_id),
            "subscription": db.get_subscription(user_id)}

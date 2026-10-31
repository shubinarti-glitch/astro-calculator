# -*- coding: utf-8 -*-
"""Robokassa redirect checkout and signed ResultURL notifications."""
from __future__ import annotations

import hashlib
import json
import re
import secrets
from pathlib import Path
from urllib.parse import quote, urlencode
from uuid import uuid4

from . import sales

CONFIG_FILE = Path(__file__).resolve().parent.parent / "data" / "robokassa.json"
PAYMENT_URL = "https://auth.robokassa.ru/Merchant/Index.aspx"
ALGORITHMS = {"md5": hashlib.md5, "sha256": hashlib.sha256, "sha384": hashlib.sha384, "sha512": hashlib.sha512}
MAX_INVOICE_ID = 9_223_372_036_854_775_807


class NotConfiguredError(Exception):
    pass


def config():
    if not CONFIG_FILE.exists():
        CONFIG_FILE.write_text(json.dumps({"merchant_login": "", "password1": "", "password2": "",
                                           "hash_algorithm": "md5", "is_test": True}, indent=2), encoding="utf-8")
    try:
        cfg = json.loads(CONFIG_FILE.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        raise NotConfiguredError("Некорректный файл настроек Robokassa (data/robokassa.json)") from exc
    if not all(cfg.get(k) for k in ("merchant_login", "password1", "password2")):
        raise NotConfiguredError("Robokassa не настроена (data/robokassa.json)")
    algorithm = str(cfg.get("hash_algorithm", "md5")).lower()
    if algorithm not in ALGORITHMS:
        raise NotConfiguredError("Неподдерживаемый алгоритм подписи Robokassa")
    if not isinstance(cfg.get("is_test"), bool):
        raise NotConfiguredError("is_test должен быть true или false")
    return {**cfg, "hash_algorithm": algorithm}


def signature(value: str, password: str, algorithm: str) -> str:
    return ALGORITHMS[algorithm](f"{value}:{password}".encode("utf-8")).hexdigest()


def _receipt(plan: str, price: int) -> str:
    # NPD tax regime is account-level; omit sno so Robokassa uses its SMZ setting.
    items = _ITEMS[plan]
    if sum(item["sum"] for item in items) != price:
        raise ValueError("Receipt items do not match payment amount")
    return json.dumps({"items": items},
                      ensure_ascii=False, separators=(",", ":"))


_ITEM_NAMES = {
    "month": "Подписка Премиум на месяц", "year": "Подписка Премиум на год",
    "plus_month": "Подписка Премиум и консультация на месяц",
    "plus_year": "Подписка Премиум и консультация на год",
    "consult": "Консультация астролога", "report": "Разовый PDF отчет",
}

def _item(name: str, amount: int, payment_method: str) -> dict:
    return {"name": name, "quantity": 1, "sum": amount, "payment_method": payment_method,
            "payment_object": "service", "tax": "none"}


_ITEMS = {
    "month": [_item(_ITEM_NAMES["month"], 299, "full_payment")],
    "year": [_item(_ITEM_NAMES["year"], 1990, "full_payment")],
    "plus_month": [_item("Подписка Премиум на месяц", 299, "full_payment"),
                   _item("Консультация астролога", 1500, "full_prepayment")],
    "plus_year": [_item("Подписка Премиум на год", 1990, "full_payment"),
                  _item("Консультация астролога", 1500, "full_prepayment")],
    "consult": [_item(_ITEM_NAMES["consult"], 3500, "full_prepayment")],
    "report": [_item(_ITEM_NAMES["report"], 149, "full_payment")],
}


def create_payment(user_id: int, plan: str, return_url: str, price: int, attribution=None, email: str | None = None) -> str:
    cfg = config()
    if plan not in _ITEM_NAMES or not isinstance(price, int) or price <= 0:
        raise ValueError("Invalid Robokassa payment details")
    attempt = str(uuid4())
    invoice_id = str(secrets.randbelow(MAX_INVOICE_ID - 1) + 1)
    return_url += ("&" if "?" in return_url else "?") + "attempt=" + attempt
    receipt = _receipt(plan, price)
    receipt_signed = quote(receipt, safe="")
    fields = {
        "MerchantLogin": cfg["merchant_login"], "OutSum": f"{price}.00", "InvId": invoice_id,
        "Receipt": receipt, "Description": _ITEM_NAMES[plan], "Culture": "ru",
        "SuccessUrl2": return_url, "SuccessUrl2Method": "GET",
        "Shp_attempt": attempt,
    }
    if email and re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+", email):
        fields["Email"] = email
    signed = ":".join((fields["MerchantLogin"], fields["OutSum"], invoice_id, receipt_signed,
                       fields["SuccessUrl2"], fields["SuccessUrl2Method"], cfg["password1"],
                       f"Shp_attempt={attempt}"))
    fields["SignatureValue"] = ALGORITHMS[cfg["hash_algorithm"]](signed.encode("utf-8")).hexdigest()
    if cfg["is_test"]:
        fields["IsTest"] = "1"
    sales.register_payment(invoice_id, user_id, plan, attempt, price, attribution, provider="robokassa")
    return PAYMENT_URL + "?" + urlencode(fields)


def verify_result(params: dict[str, str], password2: str | None = None, algorithm: str | None = None) -> bool:
    cfg = config()
    password2 = password2 or cfg["password2"]
    algorithm = algorithm or cfg["hash_algorithm"]
    if algorithm not in ALGORITHMS:
        return False
    invoice = str(params.get("InvId", ""))
    amount = str(params.get("OutSum", ""))
    supplied = str(params.get("SignatureValue", "")).lower()
    if (not re.fullmatch(r"[1-9][0-9]{0,18}", invoice) or int(invoice) > MAX_INVOICE_ID
            or not re.fullmatch(r"[0-9]+(?:\.[0-9]{1,2})?", amount)):
        return False
    extras = sorted((k, v) for k, v in params.items() if k.startswith("Shp_"))
    text = f"{amount}:{invoice}:{password2}" + "".join(f":{k}={v}" for k, v in extras)
    expected = ALGORITHMS[algorithm](text.encode("utf-8")).hexdigest()
    return secrets.compare_digest(supplied, expected)

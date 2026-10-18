"""Payment receipts and web funnel, using the existing event store (no migration)."""
from __future__ import annotations

import json
import re
import time
from collections import Counter, defaultdict
from datetime import datetime, timedelta, timezone
from uuid import UUID

from . import db

WEB_EVENTS = frozenset({
    "web_visit", "web_chart_success", "web_signup_success", "web_pricing_view",
    "web_checkout_start", "web_checkout_error",
})
STEPS = ("web_visit", "web_chart_success", "web_pricing_view",
         "server_payment_created", "server_payment_succeeded")
UTM_KEYS = ("utm_source", "utm_medium", "utm_campaign", "utm_content")


def attribution(raw: dict | None) -> dict:
    """Only bounded campaign slugs and random identifiers; never arbitrary props."""
    if not isinstance(raw, dict):
        return {}
    result = {}
    for key in ("device_id", "session_id"):
        try:
            result[key] = str(UUID(str(raw.get(key, ""))))
        except (ValueError, TypeError, AttributeError):
            return {}
    result["source"] = "app" if raw.get("source") == "app" else "web"
    for key in UTM_KEYS:
        value = raw.get(key)
        if isinstance(value, str) and re.fullmatch(r"[A-Za-z0-9_-]{1,40}", value):
            result[key] = value
    return result


def clean_web_event(name: str, raw: dict | None, device_id: str) -> dict:
    if name not in WEB_EVENTS:
        raise ValueError("Unknown web event")
    context = attribution({**(raw or {}), "device_id": device_id})
    if not context:
        raise ValueError("Web event requires UUID device and session identifiers")
    context.pop("device_id")
    for key, allowed in (
        ("plan", {"month", "year", "plus_month", "plus_year", "consult", "report"}),
        ("kind", {"natal", "transit", "synastry", "return", "progression", "calendar", "forecast", "rectification", "vedic"}),
    ):
        if isinstance((raw or {}).get(key), str) and raw[key] in allowed:
            context[key] = raw[key]
    return context


def _event(c, user_id, name, props, stamp=None):
    c.execute(
        "INSERT INTO app_events (device_id,user_id,name,props,created_at) VALUES (?,?,?,?,?)",
        (props.get("device_id", "server-payment"), user_id, name,
         json.dumps(props, ensure_ascii=True), stamp or db._now_iso()),
    )


def register_payment(payment_id, user_id, plan, attempt, price, context=None):
    receipt = {**attribution(context), "payment_id": payment_id, "attempt": attempt,
               "plan": plan, "amount": price, "currency": "RUB"}
    with db.get_conn() as c:
        c.execute("INSERT INTO payments (payment_id,user_id,plan,created_at) VALUES (?,?,?,?)",
                  (payment_id, user_id, plan, db._now_iso()))
        _event(c, user_id, "server_payment_created", receipt)


def _receipt(c, payment_id):
    row = c.execute(
        "SELECT props FROM app_events WHERE name='server_payment_created' "
        "AND json_extract(props,'$.payment_id')=? ORDER BY id LIMIT 1", (payment_id,),
    ).fetchone()
    return json.loads(row["props"]) if row else {}


def find_attempt(user_id, attempt):
    with db.get_conn() as c:
        row = c.execute(
            "SELECT p.payment_id,p.plan,p.status FROM app_events e JOIN payments p "
            "ON p.payment_id=json_extract(e.props,'$.payment_id') "
            "WHERE e.name='server_payment_created' AND e.user_id=? AND p.user_id=? "
            "AND json_extract(e.props,'$.attempt')=? LIMIT 1", (user_id, user_id, attempt),
        ).fetchone()
    return dict(row) if row else None


def apply_status(payment_id, user_id, plan, status, days):
    """Claim status AND grant all rights in one transaction, including concurrent callers."""
    if status not in {"succeeded", "canceled"}:
        return False
    with db.get_conn() as c:
        claimed = c.execute(
            "UPDATE payments SET status=? WHERE payment_id=? AND user_id=? AND plan=? AND status='pending'",
            (status, payment_id, user_id, plan),
        )
        if not claimed.rowcount or status == "canceled":
            return False
        if days:
            previous = c.execute("SELECT expires_at FROM subscriptions WHERE user_id=?", (user_id,)).fetchone()
            expires = max(int(time.time()), previous["expires_at"] if previous else 0) + days * 86400
            c.execute(
                "INSERT INTO subscriptions (user_id,plan,expires_at,source) VALUES (?,?,?,'website') "
                "ON CONFLICT(user_id) DO UPDATE SET plan=excluded.plan,expires_at=excluded.expires_at,source=excluded.source",
                (user_id, plan, expires),
            )
        if plan.startswith("plus_") or plan == "consult":
            c.execute("INSERT INTO consultations (user_id,created_at) VALUES (?,?)", (user_id, db._now_iso()))
        if plan == "report":
            c.execute("UPDATE users SET report_credits=report_credits+1 WHERE id=?", (user_id,))
        receipt = _receipt(c, payment_id) or {"payment_id": payment_id, "plan": plan}
        _event(c, user_id, "server_payment_succeeded", receipt)
    return True


def funnel(days=30, now=None):
    now = now or datetime.now(timezone.utc)
    since = (now - timedelta(days=days)).isoformat()
    until = now.isoformat()
    with db.get_conn() as c:
        rows = c.execute(
            "SELECT id,device_id,user_id,name,props,created_at FROM app_events "
            "WHERE created_at>=? AND created_at<=? AND (name LIKE 'web_%' OR name LIKE 'server_payment_%') "
            "ORDER BY created_at,id", (since, until),
        ).fetchall()
        first = c.execute("SELECT MIN(created_at) FROM app_events WHERE name='web_visit'").fetchone()[0]
        # Count historical purchases only for repeat classification; no repricing of old receipts.
        successful = {r["payment_id"]: dict(r) for r in c.execute(
            "SELECT payment_id,user_id,created_at FROM payments WHERE status='succeeded'"
        )}
    sessions = defaultdict(list)
    receipts = {}
    for row in rows:
        try:
            props = json.loads(row["props"])
        except (ValueError, TypeError):
            continue
        session = props.get("session_id")
        key = (row["device_id"], session)
        if session and props.get("source") == "web":
            sessions[key].append((row["name"], row["created_at"], props))
        if row["name"] == "server_payment_succeeded" and props.get("payment_id") in successful:
            receipts[props["payment_id"]] = (dict(row), props)
    counts = [0] * len(STEPS)
    signups = 0
    full_path_payments = set()
    for events in sessions.values():
        reached = 0
        created_on_path = set()
        for name, stamp, props in events:
            if reached < len(STEPS) and name == STEPS[reached]:
                if name != "server_payment_succeeded" or props.get("payment_id") in created_on_path:
                    counts[reached] += 1
                    reached += 1
            if reached >= 4 and name == "server_payment_created":
                created_on_path.add(props.get("payment_id"))
            if reached >= 4 and name == "server_payment_succeeded" and props.get("payment_id") in created_on_path:
                full_path_payments.add(props.get("payment_id"))
        signups += any(e[0] == "web_signup_success" for e in events)
    earliest = {}
    for pid, purchase in successful.items():
        key = (purchase["created_at"], pid)
        earliest[purchase["user_id"]] = min(earliest.get(purchase["user_id"], key), key)
    by_plan, by_source = Counter(), Counter()
    repeat = unattributed = direct = 0
    for payment_id, (row, props) in receipts.items():
        by_plan[props.get("plan", "unknown")] += 1
        source = props.get("utm_source") or props.get("source") or "unattributed"
        by_source[(source, props.get("utm_campaign", ""))] += 1
        if not props.get("session_id"):
            unattributed += 1
        elif props.get("source") == "web" and payment_id not in full_path_payments:
            direct += 1
        current = successful[payment_id]
        repeat += earliest[current["user_id"]] < (current["created_at"], payment_id)
    return {
        "since": since, "until": until, "tracking_since": first,
        "steps": [{"name": name, "sessions": counts[i],
                   "conversion": round(counts[i] * 100 / counts[i-1], 1) if i and counts[i-1] else None}
                  for i, name in enumerate(STEPS)],
        "signups": signups, "purchases": len(receipts), "repeat_purchases": repeat,
        "unattributed_purchases": unattributed, "direct_purchases": direct,
        "by_plan": [{"plan": p, "purchases": n} for p, n in sorted(by_plan.items())],
        "by_source": [{"source": s, "campaign": c, "purchases": n} for (s, c), n in sorted(by_source.items())],
        "note": "UTC. Funnel: ordered steps in the same web session within the period. Purchases: server-confirmed receipts during the period, including other sessions and app traffic. Direct purchases lack the complete observed web path. Historical attribution is unavailable.",
    }

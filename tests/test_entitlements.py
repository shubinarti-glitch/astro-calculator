import hashlib
import sqlite3
import time
from concurrent.futures import ThreadPoolExecutor

import pytest
from fastapi import HTTPException

from backend import db
from backend import main


@pytest.fixture()
def isolated_db(tmp_path, monkeypatch):
    path = tmp_path / "access.db"
    monkeypatch.setattr(db, "DB_PATH", path)
    db.init_db()
    return path


def _user(name="access_user"):
    return db.create_user(name, "password123")


def test_additive_migration_preserves_legacy_subscription(tmp_path, monkeypatch):
    path = tmp_path / "legacy.db"
    monkeypatch.setattr(db, "DB_PATH", path)
    expires = int(time.time()) + 86400
    with sqlite3.connect(path) as conn:
        conn.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, username TEXT UNIQUE NOT NULL, password_hash TEXT NOT NULL, created_at TEXT NOT NULL, is_admin INTEGER NOT NULL DEFAULT 0)")
        conn.execute("INSERT INTO users VALUES (1, 'legacy', 'x', 'now', 0)")
        conn.execute("CREATE TABLE subscriptions (user_id INTEGER PRIMARY KEY, plan TEXT NOT NULL, expires_at INTEGER NOT NULL)")
        conn.execute("INSERT INTO subscriptions VALUES (1, 'month', ?)", (expires,))
    db.init_db()
    sub = db.get_subscription(1)
    assert sub == {"plan": "month", "expires_at": expires, "source": "legacy"}
    assert db.get_access_state(1)["plan"] == "premium"


def test_free_and_premium_access(isolated_db):
    user = _user()
    free = db.get_access_state(user["id"])
    assert free["plan"] == "free"
    assert free["premium"] is False
    assert free["entitlements"] == []

    db.extend_subscription(user["id"], "month", 30)
    premium = db.get_access_state(user["id"])
    assert premium["premium"] is True
    assert premium["subscription_source"] == "website"
    assert "advanced_forecasts" in premium["entitlements"]
    assert "professional_tools" not in premium["entitlements"]


def test_cancelled_pdf_reservation_does_not_consume_quota_and_can_retry(isolated_db):
    user = _user("pdf_retry")
    db.extend_subscription(user["id"], "month", 30, source="website")

    first = db.reserve_pdf_export(user["id"])
    assert first and first["remaining"] == 4
    assert db.finish_pdf_export(user["id"], first["token"], success=False)
    assert db.pdf_export_usage(user["id"])["remaining"] == 5

    retry = db.reserve_pdf_export(user["id"])
    assert retry and retry["remaining"] == 4
    assert db.finish_pdf_export(user["id"], retry["token"], success=True)
    assert db.pdf_export_usage(user["id"])["used"] == 1


def test_report_credit_is_reserved_then_refunded_or_completed(isolated_db):
    user = _user("report_credit_lifecycle")
    db.add_report_credit(user["id"])

    failed_export = db.reserve_report_credit(user["id"], now=1_000_000)
    assert failed_export and failed_export["report_credits"] == 0
    assert db.finish_report_credit_reservation(user["id"], failed_export["token"], success=False)
    assert db.get_report_credits(user["id"]) == 1
    assert not db.finish_report_credit_reservation(user["id"], failed_export["token"], success=False)

    successful_export = db.reserve_report_credit(user["id"], now=1_000_100)
    assert successful_export and successful_export["report_credits"] == 0
    assert db.finish_report_credit_reservation(user["id"], successful_export["token"], success=True)
    assert db.get_report_credits(user["id"]) == 0
    assert not db.finish_report_credit_reservation(user["id"], successful_export["token"], success=False)


def test_abandoned_report_credit_reservation_expires_and_restores_credit(isolated_db):
    user = _user("report_credit_expiry")
    db.add_report_credit(user["id"])
    reservation = db.reserve_report_credit(user["id"], now=1_000_000)
    assert reservation and reservation["report_credits"] == 0

    retry = db.reserve_report_credit(
        user["id"], now=1_000_000 + db.PDF_EXPORT_RESERVATION_SECONDS
    )
    assert retry and retry["report_credits"] == 0
    with db.get_conn() as conn:
        old = conn.execute(
            "SELECT status FROM report_credit_reservations WHERE token_hash = ?",
            (hashlib.sha256(reservation["token"].encode()).hexdigest(),),
        ).fetchone()
    assert old["status"] == "expired"


def test_pdf_reservation_allows_five_and_rejects_sixth(isolated_db):
    user = _user("pdf_limit")
    db.extend_subscription(user["id"], "month", 30, source="website")
    now = 1_800_000_000

    for expected_used in range(1, db.PDF_EXPORT_LIMIT + 1):
        reservation = db.reserve_pdf_export(user["id"], now=now)
        assert reservation is not None
        assert reservation["used"] == expected_used
        assert db.finish_pdf_export(user["id"], reservation["token"], success=True, now=now)

    assert db.reserve_pdf_export(user["id"], now=now) is None
    assert db.pdf_export_usage(user["id"], now=now) == {
        "used": 5, "limit": 5, "remaining": 0,
    }


def test_pdf_reservation_completed_exactly_thirty_days_ago_is_expired(isolated_db):
    user = _user("pdf_window")
    db.extend_subscription(user["id"], "month", 30, source="website")
    now = 1_800_000_000
    completed_at = now - db.PDF_EXPORT_WINDOW_SECONDS
    reservation = db.reserve_pdf_export(user["id"], now=completed_at)
    assert reservation is not None
    assert db.finish_pdf_export(user["id"], reservation["token"], success=True, now=completed_at)

    assert db.pdf_export_usage(user["id"], now=now)["used"] == 0
    assert db.reserve_pdf_export(user["id"], now=now) is not None


def test_concurrent_pdf_reservations_never_exceed_monthly_limit(isolated_db):
    user = _user("pdf_parallel")
    db.extend_subscription(user["id"], "month", 30, source="website")
    now = 1_800_000_000

    with ThreadPoolExecutor(max_workers=10) as executor:
        results = list(executor.map(
            lambda _: db.reserve_pdf_export(user["id"], now=now), range(10)
        ))

    accepted = [reservation for reservation in results if reservation]
    assert len(accepted) == db.PDF_EXPORT_LIMIT
    assert db.pdf_export_usage(user["id"], now=now) == {
        "used": 5, "limit": 5, "remaining": 0,
    }


def test_subscription_and_override_expiry(isolated_db):
    user = _user()
    now = int(time.time())
    with db.get_conn() as conn:
        conn.execute(
            "INSERT INTO subscriptions (user_id, plan, expires_at, source) VALUES (?, 'month', ?, 'legacy')",
            (user["id"], now - 1),
        )
    assert db.get_access_state(user["id"], now)["plan"] == "free"

    assert db.set_entitlement_override(
        user["id"], "pdf_export", "grant", expires_at=now + 10, reason="trial"
    )
    assert db.has_entitlement(user["id"], "pdf_export", now)
    assert not db.has_entitlement(user["id"], "pdf_export", now + 11)


def test_grant_and_deny_override_plan(isolated_db):
    user = _user()
    assert db.set_entitlement_override(user["id"], "full_calendar", "grant", reason="promo")
    assert db.has_entitlement(user["id"], "full_calendar")

    db.extend_subscription(user["id"], "year", 365)
    assert db.set_entitlement_override(user["id"], "full_calendar", "deny", reason="support")
    assert not db.has_entitlement(user["id"], "full_calendar")
    assert db.clear_entitlement_override(user["id"], "full_calendar", reason="resolved")
    assert db.has_entitlement(user["id"], "full_calendar")


def test_admin_grant_has_admin_source(isolated_db):
    user = _user()
    assert db.admin_set_premium(user["id"], 7)
    state = db.get_access_state(user["id"])
    assert state["plan"] == "premium"
    assert state["subscription_source"] == "admin"
    assert state["premium_until"] > time.time() + 6 * 86400
    assert db.admin_set_premium(user["id"], 0)
    assert db.get_access_state(user["id"])["plan"] == "free"


def test_me_response_is_backward_compatible_and_exposes_access(isolated_db):
    user = _user()
    db.extend_subscription(user["id"], "month", 30, source="rustore")
    result = main.api_me(user["id"])
    assert result["premium"] is True
    assert result["premium_until"] is not None
    assert result["plan"] == "premium"
    assert result["subscription_source"] == "rustore"
    assert "charts_unlimited" in result["entitlements"]


def test_require_entitlement_dependency(isolated_db):
    user = _user()
    dependency = main.require_entitlement("advanced_forecasts")
    with pytest.raises(HTTPException) as denied:
        dependency(user["id"])
    assert denied.value.status_code == 402
    db.set_entitlement_override(user["id"], "advanced_forecasts", "grant", reason="test")
    assert dependency(user["id"]) == user["id"]


def test_invalid_entitlement_rejected(isolated_db):
    user = _user()
    with pytest.raises(ValueError):
        db.set_entitlement_override(user["id"], "unknown", "grant")

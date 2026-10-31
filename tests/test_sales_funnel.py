from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
from urllib.parse import parse_qs, urlsplit
from uuid import uuid4
from unittest.mock import Mock

import pytest
import requests
from fastapi.testclient import TestClient

from backend import db, payments, sales
from backend.main import app


@pytest.fixture
def setup_db(tmp_path, monkeypatch):
    monkeypatch.setattr(db, "DB_PATH", tmp_path / "test.db")
    db.init_db()
    owner = db.create_user("owner", "test-password")
    other = db.create_user("other", "test-password")
    # All provider traffic is denied unless a test supplies an explicit response.
    monkeypatch.setattr(payments, "_auth", lambda: ("test", "test"))
    monkeypatch.setattr(requests, "get", Mock(side_effect=AssertionError("unexpected network")))
    monkeypatch.setattr(requests, "post", Mock(side_effect=AssertionError("unexpected network")))
    return owner["id"], other["id"]


def receipt(uid, plan="month", context=None):
    pid, attempt = str(uuid4()), str(uuid4())
    sales.register_payment(pid, uid, plan, attempt, payments.PLANS[plan][0], context)
    return pid, attempt


def context():
    return {"device_id": str(uuid4()), "session_id": str(uuid4()), "source": "web", "utm_source": "instagram"}


def emit(ctx, *names):
    db.record_events(ctx["device_id"], None, [{"name": n, "props": ctx} for n in names])


@pytest.mark.parametrize("plan", payments.PLANS)
def test_webhook_before_return_and_concurrent_duplicates(setup_db, plan):
    uid, _ = setup_db
    pid, attempt = receipt(uid, plan)
    with ThreadPoolExecutor(max_workers=4) as pool:
        results = list(pool.map(lambda _: payments._apply_status(pid, uid, plan, "succeeded"), range(8)))
    assert sum(results) == 1
    result = payments.check_payments(uid, attempt)
    assert result["activated"] is False
    assert result["payment"] == {"status": "succeeded", "plan": plan}
    assert db.get_report_credits(uid) == int(plan == "report")
    with db.get_conn() as c:
        assert c.execute("SELECT COUNT(*) FROM consultations").fetchone()[0] == int(plan.startswith("plus_") or plan == "consult")
        assert c.execute("SELECT COUNT(*) FROM app_events WHERE name='server_payment_succeeded'").fetchone()[0] == 1
    if payments.PLANS[plan][1]:
        assert abs(db.get_subscription(uid)["expires_at"] - int(datetime.now().timestamp()) - payments.PLANS[plan][1] * 86400) < 5


def test_atomic_rollback_when_grant_fails(setup_db, monkeypatch):
    uid, _ = setup_db
    pid, attempt = receipt(uid)
    monkeypatch.setattr(sales, "_event", Mock(side_effect=RuntimeError("synthetic write failure")))
    with pytest.raises(RuntimeError):
        payments._apply_status(pid, uid, "month", "succeeded")
    assert sales.find_attempt(uid, attempt)["status"] == "pending"
    assert not db.is_premium(uid)


@pytest.mark.parametrize("status", ["succeeded", "pending", "waiting_for_capture", "canceled"])
def test_return_before_webhook(setup_db, monkeypatch, status):
    uid, _ = setup_db
    pid, attempt = receipt(uid, "report")
    monkeypatch.setattr(requests, "get", Mock(return_value=Mock(status_code=200, json=lambda: {"status": status})))
    result = payments.check_payments(uid, attempt)
    assert result["payment"]["status"] == ("pending" if status == "waiting_for_capture" else status)
    payments.reconcile_one(pid)
    assert db.get_report_credits(uid) == int(status == "succeeded")


def test_foreign_attempt_and_legacy_link(setup_db):
    uid, other = setup_db
    pid, attempt = receipt(uid)
    with pytest.raises(LookupError):
        payments.check_payments(other, attempt)
    payments._apply_status(pid, uid, "month", "succeeded")
    assert payments.check_payments(uid)["payment"] is None
    assert payments.check_payments(uid)["premium"]


def test_provider_failure_preserves_pending(setup_db, monkeypatch):
    uid, _ = setup_db
    _, attempt = receipt(uid)
    monkeypatch.setattr(requests, "get", Mock(side_effect=requests.Timeout()))
    with pytest.raises(requests.Timeout):
        payments.check_payments(uid, attempt)
    assert sales.find_attempt(uid, attempt)["status"] == "pending"


def test_creation_binds_return_to_receipt(setup_db, monkeypatch):
    uid, _ = setup_db
    ctx = context()
    def create(user_id, plan, return_url, price, attribution, email=None):
        attempt = str(uuid4())
        sales.register_payment("provider-123", user_id, plan, attempt, price, attribution, provider="robokassa")
        return "https://auth.robokassa.ru/Merchant/Index.aspx?Shp_attempt=" + attempt
    monkeypatch.setattr("backend.robokassa.create_payment", create)
    url = payments.create_payment(uid, "report", "https://example.test/?payment=check", ctx)
    attempt = parse_qs(urlsplit(url).query)["Shp_attempt"][0]
    assert sales.find_attempt(uid, attempt)["payment_id"] == "provider-123"
    assert sales.payment_provider("provider-123") == "robokassa"


def test_funnel_order_duplicates_direct_and_repeats(setup_db):
    uid, _ = setup_db
    ctx = context()
    emit(ctx, "web_visit", "web_visit", "web_chart_success", "web_pricing_view", "web_signup_success")
    pid, _ = receipt(uid, context=ctx)
    payments._apply_status(pid, uid, "month", "succeeded")
    wrong_order = context()
    emit(wrong_order, "web_pricing_view", "web_visit", "web_chart_success")
    direct, _ = receipt(uid, context=wrong_order)
    payments._apply_status(direct, uid, "month", "succeeded")
    untracked, _ = receipt(uid, "report")
    payments._apply_status(untracked, uid, "report", "succeeded")
    result = sales.funnel()
    assert [s["sessions"] for s in result["steps"]] == [2, 2, 1, 1, 1]
    assert result["steps"][2]["conversion"] == 50
    assert result["signups"] == 1
    assert result["purchases"] == 3
    assert result["repeat_purchases"] == 2
    assert result["direct_purchases"] == 1
    assert result["unattributed_purchases"] == 1


def test_funnel_period_and_app_source(setup_db):
    uid, _ = setup_db
    ctx = context()
    emit(ctx, "web_visit", "web_chart_success", "web_pricing_view")
    pid, _ = receipt(uid, context=ctx)
    old = (datetime.now(timezone.utc)-timedelta(days=40)).isoformat()
    with db.get_conn() as c:
        c.execute("UPDATE app_events SET created_at=?", (old,))
    payments._apply_status(pid, uid, "month", "succeeded")
    app_ctx = {**context(), "source": "app"}
    emit(app_ctx, "web_visit", "web_chart_success", "web_pricing_view")
    pid2, _ = receipt(uid, context=app_ctx)
    payments._apply_status(pid2, uid, "month", "succeeded")
    result = sales.funnel(30)
    assert [s["sessions"] for s in result["steps"]] == [0, 0, 0, 0, 0]
    assert result["purchases"] == 2
    assert result["tracking_since"] == old


def test_api_auth_validation_and_private_props(setup_db):
    uid, other = setup_db
    client = TestClient(app)
    from backend import main
    main._rate_store.clear() if hasattr(main, "_rate_store") else None
    assert client.get("/api/admin/sales-funnel").status_code == 401
    # Override only authorization for this isolated test, then restore all prior overrides.
    saved = dict(app.dependency_overrides)
    try:
        app.dependency_overrides[main.current_user_id] = lambda: other
        app.dependency_overrides[main.require_admin] = lambda: uid
        _, attempt = receipt(uid)
        assert client.post("/api/billing/check", json={"attempt": attempt}).status_code == 404
        assert client.post("/api/billing/check", json={"attempt": "bad"}).status_code == 422
        assert client.get("/api/admin/sales-funnel?days=0").status_code == 422
        assert client.get("/api/admin/sales-funnel").status_code == 200
        ctx = context()
        body = {"device_id": ctx["device_id"], "events": [{"name": "web_visit", "props": {**ctx, "email": "private@example.test", "url": "?token=secret"}}]}
        assert client.post("/api/events", json=body).status_code == 200
        with db.get_conn() as c:
            props = c.execute("SELECT props FROM app_events WHERE name='web_visit'").fetchone()[0]
            assert "private" not in props and "token" not in props
        body["events"][0]["name"] = "server_payment_succeeded"
        assert client.post("/api/events", json=body).status_code == 422
    finally:
        app.dependency_overrides.clear()
        app.dependency_overrides.update(saved)

import json
from urllib.parse import parse_qs, unquote, urlsplit

from fastapi.testclient import TestClient

from backend import db, payments, robokassa, sales
from backend.main import app


def test_robokassa_checkout_signed_receipt_and_test_flag(tmp_path, monkeypatch):
    monkeypatch.setattr(db, "DB_PATH", tmp_path / "test.db")
    db.init_db()
    user = db.create_user("robokassa_user", "test-password")
    cfg = tmp_path / "robokassa.json"
    cfg.write_text(json.dumps({"merchant_login": "shop", "password1": "pass1", "password2": "pass2",
                               "hash_algorithm": "sha256", "is_test": True}), encoding="utf-8")
    monkeypatch.setattr(robokassa, "CONFIG_FILE", cfg)

    checkout = robokassa.create_payment(user["id"], "month", "https://astrosmap.ru/?payment=check", 329)
    query = parse_qs(urlsplit(checkout).query)
    fields = {k: values[0] for k, values in query.items()}
    receipt = json.loads(unquote(fields["Receipt"]))
    assert fields["Receipt"].startswith("%7B%22items%22%3A")
    assert fields["IsTest"] == "1"
    assert receipt["items"] == [{"name": "Подписка Премиум на месяц", "quantity": 1, "sum": 329,
                                 "payment_method": "full_payment", "payment_object": "service", "tax": "none"}]
    signature_base = ":".join((fields["MerchantLogin"], fields["OutSum"], fields["InvId"],
                               fields["Receipt"],
                               robokassa.quote(fields["SuccessUrl2"], safe=""),
                               fields["SuccessUrl2Method"], "pass1", f"Shp_attempt={fields['Shp_attempt']}"))
    assert fields["SuccessUrl2"].startswith("https://astrosmap.ru/?payment=check&attempt=")
    assert fields["SignatureValue"] == robokassa.ALGORITHMS["sha256"](signature_base.encode()).hexdigest()
    assert sales.payment_provider(fields["InvId"]) == "robokassa"
    plus = json.loads(robokassa._receipt("plus_month", 1829))["items"]
    assert [item["sum"] for item in plus] == [329, 1500]
    assert plus[1]["payment_method"] == "full_payment"


def test_consultation_receipt_uses_same_payment_method_as_other_purchases():
    for plan, price in (("consult", 3500), ("plus_month", 1829), ("plus_year", 3690)):
        items = json.loads(robokassa._receipt(plan, price))["items"]
        assert all(item["payment_method"] == "full_payment" for item in items)


def test_receipt_totals_match_all_approved_plan_prices():
    for plan, (price, _days) in payments.PLANS.items():
        receipt = json.loads(robokassa._receipt(plan, price))
        assert sum(item["sum"] for item in receipt["items"]) == price


def test_billing_create_uses_robokassa_with_current_plan_price(tmp_path, monkeypatch):
    monkeypatch.setattr(db, "DB_PATH", tmp_path / "test.db")
    db.init_db()
    user = db.create_user("robokassa_checkout", "test-password")
    calls = []

    def fake_checkout(user_id, plan, return_url, price, attribution=None, email=None):
        calls.append((user_id, plan, return_url, price, attribution, email))
        return "https://auth.robokassa.ru/test-checkout"

    monkeypatch.setattr(robokassa, "create_payment", fake_checkout)
    client = TestClient(app)
    response = client.post(
        "/api/billing/create", json={"plan": "month", "lang": "ru"},
        headers={"Authorization": f"Bearer {db.make_token(user['id'])}"},
    )
    assert response.status_code == 200
    assert response.json()["confirmation_url"] == "https://auth.robokassa.ru/test-checkout"
    assert calls and calls[0][0:2] == (user["id"], "month")
    assert calls[0][3] == payments.PLANS["month"][0] == 329


def test_robokassa_result_requires_signature_and_exact_amount(tmp_path, monkeypatch):
    monkeypatch.setattr(db, "DB_PATH", tmp_path / "test.db")
    db.init_db()
    user = db.create_user("robokassa_webhook", "test-password")
    cfg = tmp_path / "robokassa.json"
    cfg.write_text(json.dumps({"merchant_login": "shop", "password1": "pass1", "password2": "pass2",
                               "hash_algorithm": "md5", "is_test": True}), encoding="utf-8")
    monkeypatch.setattr(robokassa, "CONFIG_FILE", cfg)
    attempt = "ed56e72e-c2a2-4a4a-9fd2-e3ee4e94fb73"
    sales.register_payment("123456", user["id"], "report", attempt, 169, provider="robokassa")
    client = TestClient(app)

    params = {"OutSum": "169.00", "InvId": "123456", "Shp_attempt": attempt}
    params["SignatureValue"] = robokassa.ALGORITHMS["md5"](
        f"169.00:123456:pass2:Shp_attempt={attempt}".encode()
    ).hexdigest()
    response = client.post("/api/robokassa/result", data=params)
    assert response.status_code == 200
    assert response.text == "OK123456"
    assert db.get_report_credits(user["id"]) == 1
    assert client.post("/api/robokassa/result", data=params).text == "OK123456"

    bad = dict(params, OutSum="150.00")
    assert client.post("/api/robokassa/result", data=bad).status_code == 403
    bad_amount = dict(params, OutSum="150.00")
    bad_amount["SignatureValue"] = robokassa.ALGORITHMS["md5"](
        f"150.00:123456:pass2:Shp_attempt={attempt}".encode()
    ).hexdigest()
    assert client.post("/api/robokassa/result", data=bad_amount).status_code == 400
    bad_attempt = dict(params, Shp_attempt="wrong-attempt")
    bad_attempt["SignatureValue"] = robokassa.ALGORITHMS["md5"](
        b"169.00:123456:pass2:Shp_attempt=wrong-attempt"
    ).hexdigest()
    assert client.post("/api/robokassa/result", data=bad_attempt).status_code == 400

    duplicate = (
        f"OutSum=169.00&InvId=123456&InvId=123456&Shp_attempt={attempt}&SignatureValue=ignored"
    )
    assert client.post(
        "/api/robokassa/result", content=duplicate,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
    ).status_code == 400


def test_robokassa_live_result_accepts_six_decimal_amount(tmp_path, monkeypatch):
    monkeypatch.setattr(db, "DB_PATH", tmp_path / "test.db")
    db.init_db()
    user = db.create_user("robokassa_live_amount", "test-password")
    cfg = tmp_path / "robokassa.json"
    cfg.write_text(json.dumps({"merchant_login": "shop", "password1": "pass1", "password2": "pass2",
                               "hash_algorithm": "md5", "is_test": True}), encoding="utf-8")
    monkeypatch.setattr(robokassa, "CONFIG_FILE", cfg)
    attempt = "3a97a3c2-5f61-44b1-a8d2-123456789abc"
    sales.register_payment("987654", user["id"], "month", attempt, 329, provider="robokassa")
    client = TestClient(app)

    params = {"OutSum": "329.000000", "InvId": "987654", "Shp_attempt": attempt}
    params["SignatureValue"] = robokassa.ALGORITHMS["md5"](
        f"329.000000:987654:pass2:Shp_attempt={attempt}".encode()
    ).hexdigest()
    response = client.post("/api/robokassa/result", data=params)
    assert response.status_code == 200
    assert db.is_premium(user["id"])

    too_precise = dict(params, OutSum="329.0000001")
    assert not robokassa.verify_result(too_precise)


def test_robokassa_browser_return_waits_for_signed_resulturl(tmp_path, monkeypatch):
    monkeypatch.setattr(db, "DB_PATH", tmp_path / "test.db")
    db.init_db()
    user = db.create_user("robokassa_wait", "test-password")
    attempt = "8a5b0872-5721-41ee-8d06-ffae3c9cafae"
    sales.register_payment("654321", user["id"], "month", attempt, 329, provider="robokassa")

    def unexpected_yookassa_lookup(*_args, **_kwargs):
        raise AssertionError("Robokassa orders must not be queried through YooKassa")

    monkeypatch.setattr(payments.requests, "get", unexpected_yookassa_lookup)
    result = payments.check_payments(user["id"], attempt)
    assert result["payment"] == {"status": "pending", "plan": "month"}
    assert result["premium"] is False


def test_historical_payment_provider_defaults_to_yookassa(tmp_path, monkeypatch):
    monkeypatch.setattr(db, "DB_PATH", tmp_path / "test.db")
    db.init_db()
    user = db.create_user("legacy_payment", "test-password")
    sales.register_payment("legacy-id", user["id"], "report", "legacy-attempt", 169)
    assert sales.payment_provider("legacy-id") == "yookassa"

import json
from urllib.parse import parse_qs, urlsplit

from fastapi.testclient import TestClient

from backend import db, robokassa, sales
from backend.main import app


def test_robokassa_checkout_signed_receipt_and_test_flag(tmp_path, monkeypatch):
    monkeypatch.setattr(db, "DB_PATH", tmp_path / "test.db")
    db.init_db()
    user = db.create_user("robokassa_user", "test-password")
    cfg = tmp_path / "robokassa.json"
    cfg.write_text(json.dumps({"merchant_login": "shop", "password1": "pass1", "password2": "pass2",
                               "hash_algorithm": "sha256", "is_test": True}), encoding="utf-8")
    monkeypatch.setattr(robokassa, "CONFIG_FILE", cfg)

    checkout = robokassa.create_payment(user["id"], "month", "https://astrosmap.ru/?payment=check", 299)
    query = parse_qs(urlsplit(checkout).query)
    fields = {k: values[0] for k, values in query.items()}
    receipt = json.loads(fields["Receipt"])
    assert fields["IsTest"] == "1"
    assert receipt["items"] == [{"name": "Подписка Премиум на месяц", "quantity": 1, "sum": 299,
                                 "payment_method": "full_payment", "payment_object": "service", "tax": "none"}]
    signature_base = ":".join((fields["MerchantLogin"], fields["OutSum"], fields["InvId"],
                               robokassa.quote(fields["Receipt"], safe=""), fields["SuccessUrl2"],
                               fields["SuccessUrl2Method"], "pass1", f"Shp_attempt={fields['Shp_attempt']}"))
    assert fields["SignatureValue"] == robokassa.ALGORITHMS["sha256"](signature_base.encode()).hexdigest()
    assert sales.payment_provider(fields["InvId"]) == "robokassa"
    plus = json.loads(robokassa._receipt("plus_month", 1799))["items"]
    assert [item["sum"] for item in plus] == [299, 1500]
    assert plus[1]["payment_method"] == "full_prepayment"


def test_robokassa_result_requires_signature_and_exact_amount(tmp_path, monkeypatch):
    monkeypatch.setattr(db, "DB_PATH", tmp_path / "test.db")
    db.init_db()
    user = db.create_user("robokassa_webhook", "test-password")
    cfg = tmp_path / "robokassa.json"
    cfg.write_text(json.dumps({"merchant_login": "shop", "password1": "pass1", "password2": "pass2",
                               "hash_algorithm": "md5", "is_test": True}), encoding="utf-8")
    monkeypatch.setattr(robokassa, "CONFIG_FILE", cfg)
    attempt = "ed56e72e-c2a2-4a4a-9fd2-e3ee4e94fb73"
    sales.register_payment("123456", user["id"], "report", attempt, 149, provider="robokassa")
    client = TestClient(app)

    params = {"OutSum": "149.00", "InvId": "123456", "Shp_attempt": attempt}
    params["SignatureValue"] = robokassa.ALGORITHMS["md5"](
        f"149.00:123456:pass2:Shp_attempt={attempt}".encode()
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
        b"149.00:123456:pass2:Shp_attempt=wrong-attempt"
    ).hexdigest()
    assert client.post("/api/robokassa/result", data=bad_attempt).status_code == 400

import json
import time

from backend import db, emailer


def test_subscription_letters_ru_and_en_include_account_and_store_links():
    expiry = 1_800_000_000
    ru = emailer.subscription_letter("Анна", expiry, "activated", "ru")
    en = emailer.subscription_letter("Alex", expiry, "expiring", "en")

    assert "Подписка AstroSMap Premium активна" in ru[0]
    assert "Здравствуйте, Анна" in ru[1]
    assert "до " in ru[1]
    assert "Скачать APK с сайта" in ru[2]
    assert 'lang="ru"' in ru[2]
    for url in (
        "https://astrosmap.ru/astrosmap.apk",
        "https://www.rustore.ru/catalog/app/ru.astrosmap.app",
        "https://play.google.com/store/apps/details?id=ru.astrosmap.app",
    ):
        assert url in ru[1] and url in ru[2]
    assert "Your AstroSMap Premium access is ending soon" in en[0]
    assert "Hello, Alex" in en[1]
    assert "Google Play" in en[2] and "RuStore" in en[2] and "APK" in en[2]
    assert 'lang="en"' in en[2]


def test_smtp_transport_uses_config_and_sends_multipart_message(tmp_path, monkeypatch):
    config_path = tmp_path / "smtp.json"
    config_path.write_text(json.dumps({
        "host": "smtp.test.invalid", "port": 465, "ssl": True,
        "user": "sender@test.invalid", "password": "test-only",
    }), encoding="utf-8")
    monkeypatch.setattr(emailer, "CONFIG_PATH", config_path)
    sent = []

    class FakeSMTP:
        def __init__(self, host, port, **kwargs):
            assert (host, port) == ("smtp.test.invalid", 465)

        def __enter__(self):
            return self

        def __exit__(self, *_):
            return False

        def login(self, user, password):
            assert (user, password) == ("sender@test.invalid", "test-only")

        def send_message(self, message):
            sent.append(message)

    monkeypatch.setattr(emailer.smtplib, "SMTP_SSL", FakeSMTP)
    emailer.send("reader@test.invalid", "Subject", "Plain text", "<p>HTML</p>")

    assert len(sent) == 1
    assert sent[0]["To"] == "reader@test.invalid"
    assert sent[0].get_body(preferencelist=("plain",)).get_content().strip() == "Plain text"
    assert sent[0].get_body(preferencelist=("html",)).get_content().strip() == "<p>HTML</p>"


def test_smtp_is_not_configured_for_missing_or_invalid_credentials(tmp_path, monkeypatch):
    config_path = tmp_path / "smtp.json"
    monkeypatch.setattr(emailer, "CONFIG_PATH", config_path)
    assert not emailer.is_configured()
    config_path.write_text('{"host":"smtp.test.invalid","user":"sender"}', encoding="utf-8")
    assert not emailer.is_configured()
    config_path.write_text('{"host":"smtp.test.invalid","user":"sender","password":null}', encoding="utf-8")
    assert not emailer.is_configured()


def test_subscription_outbox_deduplicates_retries_and_completes_verified_users(tmp_path, monkeypatch):
    monkeypatch.setattr(db, "DB_PATH", tmp_path / "subscription-email.db")
    db.init_db()
    user = db.create_user("mail-test", "password123", "reader@test.invalid")
    db.mark_email_verified(user["id"])
    db.extend_subscription(user["id"], "month", 30, source="website")
    assert db.enqueue_active_subscription_email(user["id"])
    assert not db.enqueue_active_subscription_email(user["id"])

    now = int(time.time())
    job = db.claim_subscription_email_job(now)
    assert job and job["status"] == "sending" and job["attempts"] == 1
    db.finish_subscription_email_job(job["id"], success=False, now=now)
    assert db.claim_subscription_email_job(now + 100) is None
    job = db.claim_subscription_email_job(now + 200)
    assert job and job["attempts"] == 2
    db.finish_subscription_email_job(job["id"], success=True, now=now + 200)
    assert db.claim_subscription_email_job(now + 200) is None
    with db.get_conn() as conn:
        row = conn.execute("SELECT status, attempts FROM subscription_email_jobs").fetchone()
    assert tuple(row) == ("sent", 2)


def test_unverified_email_never_enters_subscription_outbox(tmp_path, monkeypatch):
    monkeypatch.setattr(db, "DB_PATH", tmp_path / "unverified.db")
    db.init_db()
    user = db.create_user("unverified", "password123", "reader@test.invalid")
    db.extend_subscription(user["id"], "month", 30, source="website")

    assert not db.enqueue_active_subscription_email(user["id"])
    with db.get_conn() as conn:
        assert conn.execute("SELECT COUNT(*) FROM subscription_email_jobs").fetchone()[0] == 0

"""Profile safety checks use a disposable database, never data/app.db."""
import concurrent.futures
import json
import sqlite3

import pytest
from fastapi.testclient import TestClient
from backend import db, main


@pytest.fixture
def isolated_profiles(tmp_path, monkeypatch):
    monkeypatch.setattr(db, "DATA_DIR", tmp_path)
    monkeypatch.setattr(db, "DB_PATH", tmp_path / "profiles-test.db")
    db.init_db()
    with db.get_conn() as c:
        c.executemany("INSERT INTO users(id,username,password_hash,created_at) VALUES (?,?,?,?)",
                      [(1, "owner-a", "unused", "2026-01-01"), (2, "owner-b", "unused", "2026-01-01")])
    return db


def test_retry_after_lost_response_returns_same_profile_at_quota(isolated_profiles):
    first = db.add_profile(1, "Original", {"year": 1990}, "stable-request", limit=1)
    again = db.add_profile(1, "Retry", {"year": 1991}, "stable-request", limit=1)
    assert first == again
    assert db.count_profiles(1) == 1
    with pytest.raises(ValueError, match="profile_limit"):
        db.add_profile(1, "Different", {}, "other-request", limit=1)


def test_same_client_identity_is_scoped_to_user(isolated_profiles):
    a = db.add_profile(1, "A", {}, "same-request", limit=1)
    b = db.add_profile(2, "B", {}, "same-request", limit=1)
    assert a["id"] != b["id"]
    assert [p["label"] for p in db.list_profiles(1)] == ["A"]


def test_parallel_retries_are_one_insert(isolated_profiles):
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        results = list(pool.map(lambda _: db.add_profile(1, "A", {}, "parallel-retry", limit=1), range(8)))
    assert len({p["id"] for p in results}) == 1
    assert db.count_profiles(1) == 1


def test_update_preserves_identity_note_primary_and_record_on_error(isolated_profiles):
    p = db.add_profile(1, "Original", {"year": 1990})
    db.set_profile_note(1, p["id"], "Synthetic note")
    db.set_primary_profile(1, p["id"])
    updated = db.update_profile(1, p["id"], "Changed", {"year": 2000})
    assert updated["id"] == p["id"]
    assert db.get_primary_profile(1)["id"] == p["id"]
    assert db.list_profiles(1)[0]["note"] == "Synthetic note"
    assert db.update_profile(2, p["id"], "Intruder", {}) is None
    with pytest.raises(TypeError):
        db.update_profile(1, p["id"], "Bad", {"not_json": object()})
    assert db.list_profiles(1)[0]["label"] == "Changed"
    assert db.count_profiles(1) == 1


def test_api_backward_compatibility_atomic_put_and_stable_me_id(isolated_profiles):
    uid = [1]
    previous = main.app.dependency_overrides.get(main.current_user_id)
    main.app.dependency_overrides[main.current_user_id] = lambda: uid[0]
    try:
        client = TestClient(main.app)
        assert client.get("/api/auth/me").json()["id"] == 1
        payload = {"label": "Legacy client", "data": {"year": 1990}}
        added = client.post("/api/profiles", json=payload)
        assert added.status_code == 200
        sid = added.json()["id"]
        changed = client.put(f"/api/profiles/{sid}", json={**payload, "label": "Updated"})
        assert changed.status_code == 200 and changed.json()["id"] == sid
        invalid = client.put(f"/api/profiles/{sid}", json={"label": "", "data": {}})
        assert invalid.status_code == 422
        assert client.get("/api/profiles").json()[0]["label"] == "Updated"
        uid[0] = 2
        assert client.put(f"/api/profiles/{sid}", json=payload).status_code == 404
        assert client.get("/api/profiles").json() == []
    finally:
        if previous is None:
            main.app.dependency_overrides.pop(main.current_user_id, None)
        else:
            main.app.dependency_overrides[main.current_user_id] = previous


def test_additive_schema_upgrade_preserves_old_profile(tmp_path, monkeypatch):
    monkeypatch.setattr(db, "DATA_DIR", tmp_path)
    monkeypatch.setattr(db, "DB_PATH", tmp_path / "legacy.db")
    with sqlite3.connect(db.DB_PATH) as c:
        c.execute("CREATE TABLE profiles(id INTEGER PRIMARY KEY, user_id INTEGER NOT NULL, label TEXT NOT NULL, data TEXT NOT NULL, created_at TEXT NOT NULL)")
        c.execute("INSERT INTO profiles VALUES (4, 1, 'Legacy', '{}', '2026-01-01')")
    db.init_db()
    with db.get_conn() as c:
        row = c.execute("SELECT * FROM profiles WHERE id=4").fetchone()
        assert row["label"] == "Legacy" and row["client_id"] is None
        assert "idx_profiles_client_id" in {r["name"] for r in c.execute("PRAGMA index_list(profiles)")}

def test_discard_after_lost_create_response_removes_profile_and_blocks_late_retry(isolated_profiles):
    created = db.add_profile(1, "Synthetic lost response", {"year": 1990}, "lost-response-key", limit=1)
    # The device never saw created.id, so cancellation uses its persisted client nonce.
    db.delete_profile_by_client(1, "lost-response-key")
    assert db.count_profiles(1) == 0
    with pytest.raises(db.ProfileDeletedError):
        db.add_profile(1, "Synthetic late retry", {"year": 1990}, "lost-response-key", limit=1)
    assert db.count_profiles(1) == 0
    db.delete_profile_by_client(1, "lost-response-key")  # Repeat remains idempotent.
    # The same random nonce on another account is independent.
    assert db.add_profile(2, "Other owner", {}, "lost-response-key", limit=1)["id"] != created["id"]


def test_cancel_before_delayed_post_never_creates_new_profile(isolated_profiles):
    db.delete_profile_by_client(1, "cancel-before-arrival")
    assert db.count_profiles(1) == 0
    with pytest.raises(db.ProfileDeletedError):
        db.add_profile(1, "Request arriving after deletion", {}, "cancel-before-arrival", limit=1)
    assert db.count_profiles(1) == 0


def test_id_delete_keeps_creation_nonce_but_no_birth_data(isolated_profiles):
    created = db.add_profile(1, "Synthetic", {"year": 1990}, "delete-by-id-key")
    assert not db.delete_profile(2, created["id"])
    assert db.delete_profile(1, created["id"])
    with pytest.raises(db.ProfileDeletedError):
        db.add_profile(1, "Late replay", {}, "delete-by-id-key")
    with db.get_conn() as c:
        row = dict(c.execute("SELECT * FROM profile_deletions WHERE user_id=1").fetchone())
        assert row == {"user_id": 1, "client_id": "delete-by-id-key"}


def test_cancel_and_create_concurrent_transactions_always_end_deleted(isolated_profiles):
    def create():
        try:
            db.add_profile(1, "Concurrent synthetic", {}, "concurrent-delete-key", limit=1)
        except db.ProfileDeletedError:
            pass
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        a = pool.submit(create)
        b = pool.submit(db.delete_profile_by_client, 1, "concurrent-delete-key")
        a.result(); b.result()
    assert db.count_profiles(1) == 0


def test_client_delete_endpoint_validation_and_replay_status(isolated_profiles):
    previous = main.app.dependency_overrides.get(main.current_user_id)
    main.app.dependency_overrides[main.current_user_id] = lambda: 1
    try:
        client = TestClient(main.app)
        assert client.delete("/api/profiles/by-client", params={"client_id": "short"}).status_code == 422
        assert client.delete("/api/profiles/by-client", params={"client_id": "request-cancelled-key"}).status_code == 200
        r = client.post("/api/profiles", json={"label": "Late request", "data": {}, "client_id": "request-cancelled-key"})
        assert r.status_code == 409
        en = client.post("/api/profiles?lang=en", json={"label": "Late request", "data": {}, "client_id": "request-cancelled-key"})
        assert en.status_code == 409
        assert en.json()["detail"] == "Chart creation was cancelled: the chart was deleted."
        assert client.get("/api/profiles").json() == []
    finally:
        if previous is None:
            main.app.dependency_overrides.pop(main.current_user_id, None)
        else:
            main.app.dependency_overrides[main.current_user_id] = previous

def test_client_discard_rate_limit_rejects_before_database_write(isolated_profiles, monkeypatch):
    limited = [False]
    checked = []
    recorded = []
    def rate_check(key, max_n, window):
        checked.append((key, max_n, window))
        return limited[0]
    monkeypatch.setattr(main, "_rate_limited", rate_check)
    monkeypatch.setattr(main, "_rate_record", recorded.append)
    previous = main.app.dependency_overrides.get(main.current_user_id)
    main.app.dependency_overrides[main.current_user_id] = lambda: 1
    try:
        client = TestClient(main.app)
        assert client.delete("/api/profiles/by-client", params={"client_id": "allowed-discard-key"}).status_code == 200
        limited[0] = True
        assert client.delete("/api/profiles/by-client", params={"client_id": "blocked-discard-key"}).status_code == 429
        assert checked == [("profile-discard:1", 120, 60)] * 2
        assert recorded == ["profile-discard:1"]
        with db.get_conn() as c:
            assert [r["client_id"] for r in c.execute("SELECT client_id FROM profile_deletions WHERE user_id=1")] == ["allowed-discard-key"]
    finally:
        if previous is None:
            main.app.dependency_overrides.pop(main.current_user_id, None)
        else:
            main.app.dependency_overrides[main.current_user_id] = previous


def test_client_discard_actual_limit_is_per_user_and_precedes_write(isolated_profiles, monkeypatch):
    from collections import OrderedDict
    monkeypatch.setattr(main, "_RATE", OrderedDict())
    uid = [1]
    previous = main.app.dependency_overrides.get(main.current_user_id)
    main.app.dependency_overrides[main.current_user_id] = lambda: uid[0]
    try:
        client = TestClient(main.app)
        for i in range(120):
            assert client.delete("/api/profiles/by-client", params={"client_id": f"limit-test-{i:03}"}).status_code == 200
        assert client.delete("/api/profiles/by-client", params={"client_id": "limit-blocked-key"}).status_code == 429
        with db.get_conn() as c:
            assert c.execute("SELECT COUNT(*) FROM profile_deletions WHERE user_id=1").fetchone()[0] == 120
            assert c.execute("SELECT 1 FROM profile_deletions WHERE client_id='limit-blocked-key'").fetchone() is None
        uid[0] = 2
        assert client.delete("/api/profiles/by-client", params={"client_id": "limit-other-user"}).status_code == 200
    finally:
        if previous is None:
            main.app.dependency_overrides.pop(main.current_user_id, None)
        else:
            main.app.dependency_overrides[main.current_user_id] = previous

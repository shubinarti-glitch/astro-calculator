# -*- coding: utf-8 -*-
"""Хранилище пользователей и сохранённых карт (SQLite, только стандартная библиотека)."""
from __future__ import annotations

import hashlib
import hmac
import json
import secrets
import sqlite3
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Optional

DATA_DIR = Path(__file__).resolve().parent.parent / "data"
DB_PATH = DATA_DIR / "app.db"
SECRET_FILE = DATA_DIR / "secret.key"

TOKEN_TTL_DAYS = 30
PBKDF2_ROUNDS = 120_000


def _load_secret() -> bytes:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    if SECRET_FILE.exists():
        return SECRET_FILE.read_bytes()
    s = secrets.token_bytes(32)
    SECRET_FILE.write_bytes(s)
    return s


_SECRET = _load_secret()


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def get_conn() -> sqlite3.Connection:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


def init_db() -> None:
    with get_conn() as c:
        c.execute(
            """CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT UNIQUE NOT NULL,
                password_hash TEXT NOT NULL,
                created_at TEXT NOT NULL,
                is_admin INTEGER NOT NULL DEFAULT 0
            )"""
        )
        # Миграции для существующих БД: добавить недостающие колонки.
        cols = [r["name"] for r in c.execute("PRAGMA table_info(users)").fetchall()]
        if "is_admin" not in cols:
            c.execute("ALTER TABLE users ADD COLUMN is_admin INTEGER NOT NULL DEFAULT 0")
        if "is_banned" not in cols:
            c.execute("ALTER TABLE users ADD COLUMN is_banned INTEGER NOT NULL DEFAULT 0")
        if "email" not in cols:
            c.execute("ALTER TABLE users ADD COLUMN email TEXT")
            c.execute("ALTER TABLE users ADD COLUMN email_verified INTEGER NOT NULL DEFAULT 0")
        c.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email ON users(email) WHERE email IS NOT NULL")
        c.execute(
            """CREATE TABLE IF NOT EXISTS profiles (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                label TEXT NOT NULL,
                data TEXT NOT NULL,
                created_at TEXT NOT NULL,
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
            )"""
        )
        pcols = [r["name"] for r in c.execute("PRAGMA table_info(profiles)").fetchall()]
        if "note" not in pcols:
            c.execute("ALTER TABLE profiles ADD COLUMN note TEXT NOT NULL DEFAULT ''")
        # Одноразовые токены писем (подтверждение email, сброс пароля) — храним хэш.
        c.execute(
            """CREATE TABLE IF NOT EXISTS email_tokens (
                token_hash TEXT PRIMARY KEY,
                user_id INTEGER NOT NULL,
                kind TEXT NOT NULL,
                exp INTEGER NOT NULL,
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
            )"""
        )
        # История расчётов: последние запуски пользователя для повтора в один клик.
        c.execute(
            """CREATE TABLE IF NOT EXISTS calc_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                kind TEXT NOT NULL,
                label TEXT NOT NULL,
                params TEXT NOT NULL,
                created_at TEXT NOT NULL,
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
            )"""
        )
        # Отозванные токены (для реального выхода) — храним подпись и срок.
        c.execute(
            """CREATE TABLE IF NOT EXISTS revoked_tokens (
                sig TEXT PRIMARY KEY,
                exp INTEGER NOT NULL
            )"""
        )
        # Подписки: одна строка на пользователя, продление сдвигает expires_at.
        c.execute(
            """CREATE TABLE IF NOT EXISTS subscriptions (
                user_id INTEGER PRIMARY KEY,
                plan TEXT NOT NULL,
                expires_at INTEGER NOT NULL,
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
            )"""
        )
        # Платежи ЮKassa: pending до подтверждения, succeeded после активации.
        c.execute(
            """CREATE TABLE IF NOT EXISTS payments (
                payment_id TEXT PRIMARY KEY,
                user_id INTEGER NOT NULL,
                plan TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'pending',
                created_at TEXT NOT NULL,
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
            )"""
        )
        # Оплаченные консультации астролога (тарифы «Премиум+»): одна строка = один кредит.
        c.execute(
            """CREATE TABLE IF NOT EXISTS consultations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                status TEXT NOT NULL DEFAULT 'available',
                created_at TEXT NOT NULL,
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
            )"""
        )
        # Счётчики использования функций: строка = (день, эндпоинт).
        c.execute(
            """CREATE TABLE IF NOT EXISTS usage_stats (
                date TEXT NOT NULL,
                endpoint TEXT NOT NULL,
                count INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (date, endpoint)
            )"""
        )
        # Аудит правок текстов админами: кто, что, когда.
        c.execute(
            """CREATE TABLE IF NOT EXISTS text_audit (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER,
                username TEXT,
                key TEXT NOT NULL,
                action TEXT NOT NULL,
                created_at TEXT NOT NULL
            )"""
        )


# --------------------------------------------------------------------------- #
#  Пароли
# --------------------------------------------------------------------------- #
def hash_password(password: str, salt: Optional[str] = None) -> str:
    salt = salt or secrets.token_hex(16)
    h = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), bytes.fromhex(salt), PBKDF2_ROUNDS).hex()
    return f"{salt}${h}"


def verify_password(password: str, stored: str) -> bool:
    try:
        salt, h = stored.split("$", 1)
    except ValueError:
        return False
    calc = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), bytes.fromhex(salt), PBKDF2_ROUNDS).hex()
    return hmac.compare_digest(h, calc)


# --------------------------------------------------------------------------- #
#  Токены (stateless, подписанные HMAC)
# --------------------------------------------------------------------------- #
def make_token(user_id: int) -> str:
    payload = f"{user_id}.{int(time.time()) + TOKEN_TTL_DAYS * 86400}"
    sig = hmac.new(_SECRET, payload.encode(), hashlib.sha256).hexdigest()
    return f"{payload}.{sig}"


def verify_token(token: str) -> Optional[int]:
    try:
        uid, exp, sig = token.split(".")
        payload = f"{uid}.{exp}"
        expected = hmac.new(_SECRET, payload.encode(), hashlib.sha256).hexdigest()
        if not hmac.compare_digest(sig, expected):
            return None
        if int(exp) < time.time():
            return None
        if _is_token_revoked(sig):
            return None
        return int(uid)
    except Exception:
        return None


def _is_token_revoked(sig: str) -> bool:
    with get_conn() as c:
        row = c.execute("SELECT 1 FROM revoked_tokens WHERE sig = ?", (sig,)).fetchone()
        return row is not None


def revoke_token(token: str) -> bool:
    """Отозвать токен (выход): сохранить его подпись до истечения срока."""
    try:
        uid, exp, sig = token.split(".")
        exp_i = int(exp)
    except (ValueError, AttributeError):
        return False
    # Принимаем только подлинные токены — иначе аноним может засорять таблицу
    # записями с далёким exp, которые никогда не вычистятся (DoS на БД).
    expected = hmac.new(_SECRET, f"{uid}.{exp}".encode(), hashlib.sha256).hexdigest()
    if not hmac.compare_digest(sig, expected):
        return False
    with get_conn() as c:
        now = int(time.time())
        c.execute("DELETE FROM revoked_tokens WHERE exp < ?", (now,))  # чистим истёкшие
        c.execute("INSERT OR IGNORE INTO revoked_tokens (sig, exp) VALUES (?, ?)", (sig, exp_i))
    return True


def add_text_audit(user_id: Optional[int], username: Optional[str], key: str, action: str) -> None:
    with get_conn() as c:
        c.execute(
            "INSERT INTO text_audit (user_id, username, key, action, created_at) VALUES (?, ?, ?, ?, ?)",
            (user_id, username, key, action, _now_iso()),
        )


def list_text_audit(limit: int = 100) -> list[dict]:
    with get_conn() as c:
        rows = c.execute(
            "SELECT user_id, username, key, action, created_at FROM text_audit ORDER BY id DESC LIMIT ?",
            (limit,),
        ).fetchall()
    return [dict(r) for r in rows]


# --------------------------------------------------------------------------- #
#  Пользователи
# --------------------------------------------------------------------------- #
class UserExistsError(Exception):
    pass


def create_user(username: str, password: str, email: Optional[str] = None) -> dict:
    username = username.strip()
    email = email.strip().lower() if email else None
    with get_conn() as c:
        existing = c.execute("SELECT id FROM users WHERE username = ?", (username,)).fetchone()
        if existing:
            raise UserExistsError("Пользователь с таким именем уже существует")
        if email and c.execute("SELECT id FROM users WHERE email = ?", (email,)).fetchone():
            raise UserExistsError("Эта почта уже привязана к другому аккаунту")
        # Первый зарегистрированный пользователь становится администратором.
        is_first = c.execute("SELECT COUNT(*) AS n FROM users").fetchone()["n"] == 0
        cur = c.execute(
            "INSERT INTO users (username, password_hash, created_at, is_admin, email) VALUES (?, ?, ?, ?, ?)",
            (username, hash_password(password), _now_iso(), 1 if is_first else 0, email),
        )
        return {"id": cur.lastrowid, "username": username, "is_admin": bool(is_first)}


def get_user_by_email(email: str) -> Optional[sqlite3.Row]:
    with get_conn() as c:
        return c.execute("SELECT * FROM users WHERE email = ?", (email.strip().lower(),)).fetchone()


def set_user_email(user_id: int, email: str) -> bool:
    """Привязать/сменить почту; сбрасывает флаг подтверждения. False — почта занята."""
    email = email.strip().lower()
    with get_conn() as c:
        taken = c.execute("SELECT id FROM users WHERE email = ? AND id != ?", (email, user_id)).fetchone()
        if taken:
            return False
        c.execute("UPDATE users SET email = ?, email_verified = 0 WHERE id = ?", (email, user_id))
        return True


def mark_email_verified(user_id: int) -> None:
    with get_conn() as c:
        c.execute("UPDATE users SET email_verified = 1 WHERE id = ?", (user_id,))


def set_password(user_id: int, new_password: str) -> None:
    """Установить пароль без проверки старого (после сброса по ссылке из письма)."""
    with get_conn() as c:
        c.execute("UPDATE users SET password_hash = ? WHERE id = ?", (hash_password(new_password), user_id))


# --------------------------------------------------------------------------- #
#  Одноразовые токены писем (verify / reset)
# --------------------------------------------------------------------------- #
def _hash_email_token(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


def create_email_token(user_id: int, kind: str, ttl_seconds: int) -> str:
    token = secrets.token_urlsafe(32)
    now = int(time.time())
    with get_conn() as c:
        c.execute("DELETE FROM email_tokens WHERE exp < ?", (now,))  # чистим истёкшие
        c.execute("DELETE FROM email_tokens WHERE user_id = ? AND kind = ?", (user_id, kind))  # один активный
        c.execute("INSERT INTO email_tokens (token_hash, user_id, kind, exp) VALUES (?, ?, ?, ?)",
                  (_hash_email_token(token), user_id, kind, now + ttl_seconds))
    return token


def consume_email_token(token: str, kind: str) -> Optional[int]:
    """Проверить и погасить токен. Возвращает user_id или None."""
    h = _hash_email_token(token)
    with get_conn() as c:
        row = c.execute("SELECT user_id, exp FROM email_tokens WHERE token_hash = ? AND kind = ?",
                        (h, kind)).fetchone()
        if not row:
            return None
        c.execute("DELETE FROM email_tokens WHERE token_hash = ?", (h,))
        if row["exp"] < time.time():
            return None
        return row["user_id"]


def get_user_by_username(username: str) -> Optional[sqlite3.Row]:
    with get_conn() as c:
        return c.execute("SELECT * FROM users WHERE username = ?", (username.strip(),)).fetchone()


def get_user_by_id(user_id: int) -> Optional[sqlite3.Row]:
    with get_conn() as c:
        return c.execute("SELECT * FROM users WHERE id = ?", (user_id,)).fetchone()


def change_password(user_id: int, old_password: str, new_password: str) -> bool:
    """Сменить пароль, если старый верен. Выданные ранее токены остаются
    действительны до истечения срока (stateless), отзывается только предъявленный."""
    u = get_user_by_id(user_id)
    if not u or not verify_password(old_password, u["password_hash"]):
        return False
    with get_conn() as c:
        c.execute("UPDATE users SET password_hash = ? WHERE id = ?",
                  (hash_password(new_password), user_id))
    return True


def is_banned(user_id: int) -> bool:
    u = get_user_by_id(user_id)
    return bool(u and u["is_banned"])


# --------------------------------------------------------------------------- #
#  Сохранённые карты
# --------------------------------------------------------------------------- #
def add_profile(user_id: int, label: str, data: dict) -> dict:
    with get_conn() as c:
        cur = c.execute(
            "INSERT INTO profiles (user_id, label, data, created_at) VALUES (?, ?, ?, ?)",
            (user_id, label, json.dumps(data, ensure_ascii=False), _now_iso()),
        )
        return {"id": cur.lastrowid, "label": label, "data": data}


def list_profiles(user_id: int) -> list[dict]:
    with get_conn() as c:
        rows = c.execute(
            "SELECT id, label, data, note, created_at FROM profiles WHERE user_id = ? ORDER BY created_at DESC",
            (user_id,),
        ).fetchall()
    return [
        {"id": r["id"], "label": r["label"], "data": json.loads(r["data"]),
         "note": r["note"], "created_at": r["created_at"]}
        for r in rows
    ]


def delete_profile(user_id: int, profile_id: int) -> bool:
    with get_conn() as c:
        cur = c.execute("DELETE FROM profiles WHERE id = ? AND user_id = ?", (profile_id, user_id))
        return cur.rowcount > 0


def set_profile_note(user_id: int, profile_id: int, note: str) -> bool:
    with get_conn() as c:
        cur = c.execute("UPDATE profiles SET note = ? WHERE id = ? AND user_id = ?",
                        (note, profile_id, user_id))
        return cur.rowcount > 0


# --------------------------------------------------------------------------- #
#  История расчётов
# --------------------------------------------------------------------------- #
HISTORY_KEEP = 20


def add_history(user_id: int, kind: str, label: str, params: dict) -> None:
    with get_conn() as c:
        c.execute("INSERT INTO calc_history (user_id, kind, label, params, created_at) VALUES (?, ?, ?, ?, ?)",
                  (user_id, kind, label, json.dumps(params, ensure_ascii=False), _now_iso()))
        c.execute(
            "DELETE FROM calc_history WHERE user_id = ? AND id NOT IN "
            "(SELECT id FROM calc_history WHERE user_id = ? ORDER BY id DESC LIMIT ?)",
            (user_id, user_id, HISTORY_KEEP),
        )


def list_history(user_id: int) -> list[dict]:
    with get_conn() as c:
        rows = c.execute(
            "SELECT id, kind, label, params, created_at FROM calc_history WHERE user_id = ? ORDER BY id DESC",
            (user_id,),
        ).fetchall()
    return [{"id": r["id"], "kind": r["kind"], "label": r["label"],
             "params": json.loads(r["params"]), "created_at": r["created_at"]} for r in rows]


def list_user_payments(user_id: int) -> list[dict]:
    with get_conn() as c:
        rows = c.execute(
            "SELECT payment_id, plan, status, created_at FROM payments WHERE user_id = ? ORDER BY created_at DESC LIMIT 50",
            (user_id,),
        ).fetchall()
    return [dict(r) for r in rows]


# --------------------------------------------------------------------------- #
#  Подписки и платежи
# --------------------------------------------------------------------------- #
def get_subscription(user_id: int) -> Optional[dict]:
    with get_conn() as c:
        r = c.execute("SELECT plan, expires_at FROM subscriptions WHERE user_id = ?", (user_id,)).fetchone()
    return {"plan": r["plan"], "expires_at": r["expires_at"]} if r else None


def is_premium(user_id: int) -> bool:
    sub = get_subscription(user_id)
    return bool(sub and sub["expires_at"] > time.time())


def extend_subscription(user_id: int, plan: str, days: int) -> dict:
    """Активировать/продлить подписку: срок добавляется к текущему, если он не истёк."""
    now = int(time.time())
    with get_conn() as c:
        r = c.execute("SELECT expires_at FROM subscriptions WHERE user_id = ?", (user_id,)).fetchone()
        base = max(now, r["expires_at"]) if r else now
        expires = base + days * 86400
        c.execute(
            "INSERT INTO subscriptions (user_id, plan, expires_at) VALUES (?, ?, ?) "
            "ON CONFLICT(user_id) DO UPDATE SET plan = excluded.plan, expires_at = excluded.expires_at",
            (user_id, plan, expires),
        )
    return {"plan": plan, "expires_at": expires}


def add_consultation(user_id: int) -> None:
    with get_conn() as c:
        c.execute("INSERT INTO consultations (user_id, created_at) VALUES (?, ?)", (user_id, _now_iso()))


def has_consultation(user_id: int) -> bool:
    with get_conn() as c:
        r = c.execute("SELECT 1 FROM consultations WHERE user_id = ? AND status = 'available' LIMIT 1",
                      (user_id,)).fetchone()
    return r is not None


def add_payment(payment_id: str, user_id: int, plan: str) -> None:
    with get_conn() as c:
        c.execute(
            "INSERT INTO payments (payment_id, user_id, plan, created_at) VALUES (?, ?, ?, ?)",
            (payment_id, user_id, plan, _now_iso()),
        )


def pending_payments(user_id: int) -> list[dict]:
    with get_conn() as c:
        rows = c.execute(
            "SELECT payment_id, plan FROM payments WHERE user_id = ? AND status = 'pending' ORDER BY created_at DESC LIMIT 5",
            (user_id,),
        ).fetchall()
    return [dict(r) for r in rows]


def set_payment_status(payment_id: str, status: str) -> None:
    with get_conn() as c:
        c.execute("UPDATE payments SET status = ? WHERE payment_id = ?", (status, payment_id))


# --------------------------------------------------------------------------- #
#  Статистика использования функций
# --------------------------------------------------------------------------- #
def record_usage(endpoint: str) -> None:
    day = datetime.now(timezone.utc).date().isoformat()
    with get_conn() as c:
        c.execute(
            "INSERT INTO usage_stats (date, endpoint, count) VALUES (?, ?, 1) "
            "ON CONFLICT(date, endpoint) DO UPDATE SET count = count + 1",
            (day, endpoint),
        )


def usage_summary(days: int = 30) -> list[dict]:
    since = (datetime.now(timezone.utc).date() - timedelta(days=days)).isoformat()
    with get_conn() as c:
        rows = c.execute(
            """SELECT endpoint,
                      SUM(CASE WHEN date >= ? THEN count ELSE 0 END) AS recent,
                      SUM(count) AS total
               FROM usage_stats GROUP BY endpoint ORDER BY total DESC""",
            (since,),
        ).fetchall()
    return [dict(r) for r in rows]


# --------------------------------------------------------------------------- #
#  Администрирование
# --------------------------------------------------------------------------- #
def is_admin(user_id: int) -> bool:
    u = get_user_by_id(user_id)
    return bool(u and u["is_admin"])


def admin_stats() -> dict:
    with get_conn() as c:
        users = c.execute("SELECT COUNT(*) AS n FROM users").fetchone()["n"]
        profiles = c.execute("SELECT COUNT(*) AS n FROM profiles").fetchone()["n"]
    return {"users": users, "profiles": profiles}


def list_all_users() -> list[dict]:
    with get_conn() as c:
        rows = c.execute(
            """SELECT u.id, u.username, u.created_at, u.is_admin, u.is_banned,
                      (SELECT COUNT(*) FROM profiles p WHERE p.user_id = u.id) AS charts,
                      (SELECT expires_at FROM subscriptions s WHERE s.user_id = u.id) AS premium_until
               FROM users u ORDER BY u.id"""
        ).fetchall()
    now = time.time()
    return [
        {"id": r["id"], "username": r["username"], "created_at": r["created_at"],
         "is_admin": bool(r["is_admin"]), "is_banned": bool(r["is_banned"]),
         "charts": r["charts"],
         "premium_until": r["premium_until"] if r["premium_until"] and r["premium_until"] > now else None}
        for r in rows
    ]


def admin_set_premium(user_id: int, days: int) -> bool:
    """days > 0 — продлить подписку; days == 0 — снять."""
    if not get_user_by_id(user_id):
        return False
    with get_conn() as c:
        if days == 0:
            c.execute("DELETE FROM subscriptions WHERE user_id = ?", (user_id,))
            return True
    extend_subscription(user_id, "admin", days)
    return True


def admin_set_banned(user_id: int, banned: bool) -> bool:
    with get_conn() as c:
        u = c.execute("SELECT is_admin FROM users WHERE id = ?", (user_id,)).fetchone()
        if not u or u["is_admin"]:  # админа забанить нельзя
            return False
        c.execute("UPDATE users SET is_banned = ? WHERE id = ?", (1 if banned else 0, user_id))
        return True


def list_payments(limit: int = 200) -> dict:
    with get_conn() as c:
        rows = c.execute(
            """SELECT p.payment_id, p.user_id, u.username, p.plan, p.status, p.created_at
               FROM payments p LEFT JOIN users u ON u.id = p.user_id
               ORDER BY p.created_at DESC LIMIT ?""",
            (limit,),
        ).fetchall()
    return {"items": [dict(r) for r in rows]}


def admin_delete_user(user_id: int) -> bool:
    with get_conn() as c:
        # нельзя удалить администратора
        u = c.execute("SELECT is_admin FROM users WHERE id = ?", (user_id,)).fetchone()
        if not u or u["is_admin"]:
            return False
        cur = c.execute("DELETE FROM users WHERE id = ?", (user_id,))
        return cur.rowcount > 0

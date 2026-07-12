# -*- coding: utf-8 -*-
"""Отправка писем (подтверждение email, сброс пароля) через SMTP.

Настройки — data/smtp.json:
{
  "host": "smtp.yandex.ru", "port": 465, "ssl": true,
  "user": "noreply@astrosmap.ru", "password": "...",
  "from": "Астрокалькулятор <noreply@astrosmap.ru>"
}
Пока файла нет — сервис «не настроен»: письма не уходят, но регистрация работает.
"""
from __future__ import annotations

import json
import smtplib
import ssl
from email.message import EmailMessage
from pathlib import Path

CONFIG_PATH = Path(__file__).resolve().parent.parent / "data" / "smtp.json"


class NotConfiguredError(Exception):
    pass


def _config() -> dict:
    if not CONFIG_PATH.exists():
        raise NotConfiguredError("Почтовый сервис не настроен (data/smtp.json)")
    return json.loads(CONFIG_PATH.read_text(encoding="utf-8"))


def is_configured() -> bool:
    return CONFIG_PATH.exists()


def send(to: str, subject: str, body: str) -> None:
    cfg = _config()
    msg = EmailMessage()
    msg["From"] = cfg.get("from") or cfg["user"]
    msg["To"] = to
    msg["Subject"] = subject
    msg.set_content(body)
    port = int(cfg.get("port", 465))
    if cfg.get("ssl", True):
        with smtplib.SMTP_SSL(cfg["host"], port, context=ssl.create_default_context(), timeout=15) as s:
            s.login(cfg["user"], cfg["password"])
            s.send_message(msg)
    else:
        with smtplib.SMTP(cfg["host"], port, timeout=15) as s:
            s.starttls(context=ssl.create_default_context())
            s.login(cfg["user"], cfg["password"])
            s.send_message(msg)


# ---------------------------------------------------------------------------
#  Тексты писем
# ---------------------------------------------------------------------------
def verify_letter(link: str, lang: str = "ru") -> tuple[str, str]:
    if lang == "en":
        return ("Confirm your email — Astrocalculator",
                f"Hello!\n\nTo confirm your email on astrosmap.ru, open this link:\n{link}\n\n"
                "The link is valid for 24 hours. If you didn't register, just ignore this message.")
    return ("Подтвердите почту — Астрокалькулятор",
            f"Здравствуйте!\n\nЧтобы подтвердить почту на astrosmap.ru, откройте ссылку:\n{link}\n\n"
            "Ссылка действует 24 часа. Если вы не регистрировались — просто удалите это письмо.")


def digest_letter(person: str, events: list[dict], unsub_link: str, lang: str = "ru") -> tuple[str, str]:
    """Еженедельный дайджест: person — имя основного человека, events — список {date, title, text}."""
    if not events:
        body_events = ("На этой неделе крупных транзитов нет — спокойный фон, "
                       "хорошее время для рутины и накопленных дел.")
    else:
        lines = []
        for e in events:
            lines.append(f"• {e['date']} — {e['title']}\n  {e['text']}")
        body_events = "\n\n".join(lines)
    subject = f"Ваш астропрогноз на неделю — {person}"
    body = (f"Здравствуйте!\n\nКлючевые транзиты недели для натальной карты «{person}»:\n\n"
            f"{body_events}\n\n"
            "Подробный прогноз с точными датами — на astrosmap.ru, вкладка «Прогноз».\n\n"
            "———\n"
            f"Чтобы отписаться от еженедельных писем: {unsub_link}")
    return subject, body


def reset_letter(link: str, lang: str = "ru") -> tuple[str, str]:
    if lang == "en":
        return ("Password reset — Astrocalculator",
                f"Hello!\n\nTo set a new password on astrosmap.ru, open this link:\n{link}\n\n"
                "The link is valid for 1 hour and works once. If you didn't request a reset, ignore this message.")
    return ("Сброс пароля — Астрокалькулятор",
            f"Здравствуйте!\n\nЧтобы задать новый пароль на astrosmap.ru, откройте ссылку:\n{link}\n\n"
            "Ссылка действует 1 час и срабатывает один раз. Если вы не запрашивали сброс — удалите это письмо.")

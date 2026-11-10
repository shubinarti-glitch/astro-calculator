"""Outbox worker for transactional subscription notices."""
from __future__ import annotations

import asyncio
import json
import logging
import time

from . import db, emailer

logger = logging.getLogger("astro.subscription_emails")


async def run() -> None:
    next_expiry_scan = 0
    while True:
        now = int(time.time())
        if now >= next_expiry_scan:
            try:
                db.queue_expiring_subscription_emails(now, within_days=3)
                next_expiry_scan = now + 86400
            except Exception:
                logger.exception("Не удалось поставить письма о скором окончании подписки в очередь")
                next_expiry_scan = now + 300

        if emailer.is_configured():
            try:
                job = db.claim_subscription_email_job(now)
            except Exception:
                logger.exception("Не удалось получить письмо о подписке из очереди")
                await asyncio.sleep(30)
                continue
            if job:
                try:
                    user = db.get_user_by_id(job["user_id"])
                    if not user or not user["email"] or not user["email_verified"] or user["is_banned"]:
                        db.finish_subscription_email_job(job["id"], success=True, now=now)
                        continue
                    payload = json.loads(job["payload"])
                    subscription = db.get_subscription(job["user_id"])
                    if not subscription or subscription["source"] == "admin" or subscription["expires_at"] <= now:
                        db.finish_subscription_email_job(job["id"], success=True, now=now)
                        continue
                    if job["kind"] == "expiring":
                        expiry = int(payload["expires_at"])
                        if subscription["expires_at"] != expiry or expiry > now + 3 * 86400:
                            db.finish_subscription_email_job(job["id"], success=True, now=now)
                            continue
                    else:
                        payload["expires_at"] = subscription["expires_at"]
                    subject, body, html = emailer.subscription_letter(
                        user["username"], payload["expires_at"], job["kind"],
                        user["preferred_lang"] or "ru",
                    )
                    await asyncio.to_thread(emailer.send, user["email"], subject, body, html)
                    db.finish_subscription_email_job(job["id"], success=True)
                except Exception:
                    logger.exception("Не удалось отправить письмо о подписке для user_id=%s", job["user_id"])
                    try:
                        db.finish_subscription_email_job(job["id"], success=False)
                    except Exception:
                        logger.exception("Не удалось вернуть письмо о подписке в очередь")
                continue
        await asyncio.sleep(30)

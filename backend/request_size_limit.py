"""Bound HTTP request bodies even when the client omits Content-Length."""
from __future__ import annotations

from collections import deque

from starlette.requests import Request
from starlette.responses import JSONResponse


class RequestSizeLimitMiddleware:
    def __init__(self, app, max_bytes: int):
        self.app = app
        self.max_bytes = max_bytes

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        request = Request(scope)
        content_length = request.headers.get("content-length", "")
        if content_length.isdigit() and int(content_length) > self.max_bytes:
            await self._too_large(scope, receive, send, request)
            return

        buffered = deque()
        total = 0
        while True:
            message = await receive()
            if message["type"] == "http.disconnect":
                buffered.append(message)
                break
            if message["type"] != "http.request":
                buffered.append(message)
                continue
            total += len(message.get("body", b""))
            if total > self.max_bytes:
                await self._too_large(scope, receive, send, request)
                return
            buffered.append(message)
            if not message.get("more_body", False):
                break

        async def replay_receive():
            if buffered:
                return buffered.popleft()
            return await receive()

        await self.app(scope, replay_receive, send)

    async def _too_large(self, scope, receive, send, request):
        from . import api_language

        detail = api_language.translate("Слишком большой запрос", api_language.language(request))
        response = JSONResponse(
            status_code=413,
            content={"detail": detail},
            headers={"Connection": "close"},
        )
        await response(scope, receive, send)

import asyncio

from backend.request_size_limit import RequestSizeLimitMiddleware


def _scope(query_string=b""):
    return {
        "type": "http",
        "asgi": {"version": "3.0"},
        "http_version": "1.1",
        "method": "POST",
        "scheme": "http",
        "path": "/api/test",
        "raw_path": b"/api/test",
        "query_string": query_string,
        "root_path": "",
        "headers": [],
        "client": ("127.0.0.1", 12345),
        "server": ("testserver", 80),
    }


def _run_request(app, chunks, query_string=b""):
    messages = [
        {"type": "http.request", "body": chunk, "more_body": index < len(chunks) - 1}
        for index, chunk in enumerate(chunks)
    ]
    sent = []

    async def receive():
        return messages.pop(0) if messages else {"type": "http.disconnect"}

    async def send(message):
        sent.append(message)

    asyncio.run(app(_scope(query_string), receive, send))
    return sent


def test_chunked_body_without_content_length_is_rejected_over_limit():
    async def endpoint(scope, receive, send):
        raise AssertionError("Oversized request reached the application")

    sent = _run_request(
        RequestSizeLimitMiddleware(endpoint, max_bytes=10),
        [b"123456", b"78901"],
        query_string=b"lang=en",
    )

    assert sent[0]["status"] == 413
    assert b"request is too large" in sent[1]["body"].lower()


def test_chunked_body_under_limit_reaches_the_application_intact():
    async def endpoint(scope, receive, send):
        chunks = []
        while True:
            message = await receive()
            if message["type"] != "http.request":
                break
            chunks.append(message["body"])
            if not message.get("more_body", False):
                break
        body = b"".join(chunks)
        await send({"type": "http.response.start", "status": 200, "headers": []})
        await send({"type": "http.response.body", "body": body})

    sent = _run_request(RequestSizeLimitMiddleware(endpoint, max_bytes=10), [b"1234", b"567"])

    assert sent[0]["status"] == 200
    assert sent[1]["body"] == b"1234567"

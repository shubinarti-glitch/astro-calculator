from backend import main


def test_free_user_cannot_request_paid_modes(monkeypatch):
    monkeypatch.setattr(main.db, "get_access_state", lambda _uid: {"premium": False, "entitlements": []})
    assert main._effective_interpretation_mode("detailed", 1) == "brief"
    assert main._effective_interpretation_mode("technical", 1) == "brief"


def test_premium_and_professional_modes(monkeypatch):
    monkeypatch.setattr(
        main.db,
        "get_access_state",
        lambda uid: {"premium": True, "entitlements": ["professional_tools"] if uid == 2 else []},
    )
    assert main._effective_interpretation_mode(None, 1) == "detailed"
    assert main._effective_interpretation_mode("technical", 1) == "detailed"
    assert main._effective_interpretation_mode("technical", 2) == "technical"


def test_brief_report_is_reduced_without_mutating_source():
    source = {
        "summary": "word " * 200,
        "sphere_forecast": [{"text": "sphere " * 100} for _ in range(4)],
        "events": [{"text": "event " * 100} for _ in range(5)],
        "profection": {"full": "paid"},
        "progressed_moon": {"full": "paid"},
    }
    result = main._brief_report(source, "forecast")

    assert result["access_mode"] == "brief"
    assert len(result["sphere_forecast"]) == 2
    assert len(result["events"]) == 3
    assert "profection" not in result
    assert "progressed_moon" not in result
    assert len(source["sphere_forecast"]) == 4
    assert "profection" in source

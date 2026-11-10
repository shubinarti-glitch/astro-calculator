import copy

from backend import main


def test_brief_natal_limits_both_rendering_modes_without_changing_calculations():
    import copy
    planet = {
        "name": "Sun", "longitude": 123.456, "house_num": 6,
        "interp_sign": "sign " * 200, "interp_house": "house " * 200,
        "interp_plain": "plain " * 200,
        "interp_full": [{"label": "Reading", "text": "full " * 200}] * 3,
        "interp_full_plain": [{"label": "Reading", "text": "plain full " * 200}] * 3,
    }
    source = {"planets": [planet]}
    original = copy.deepcopy(source)
    result = main._brief_report(source, "natal")["planets"][0]
    for key in ("interp_sign", "interp_house", "interp_plain"):
        assert len(result[key]) <= 261
    for key in ("interp_full", "interp_full_plain"):
        assert len(result[key]) == 1
        assert len(result[key][0]["text"]) <= 261
    assert result["longitude"] == planet["longitude"]
    assert result["house_num"] == planet["house_num"]
    assert source == original


def test_paid_natal_retains_complete_alternative_text(monkeypatch):
    monkeypatch.setattr(main.db, "get_access_state", lambda uid: {"premium": True})
    source = {"planets": [{"interp_full_plain": [{"text": "word " * 500}]}]}
    result = main._protect_interpretations(source, "natal", "detailed", 1)
    assert result["planets"] == source["planets"]


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


def test_brief_transit_shows_authored_preview_and_removes_full_overview():
    source = {
        "aspects": [
            {
                "p1": "Mars", "p2": "Jupiter", "aspect": "square",
                "interp": (
                    "Суть транзита. Общая вводная повторяется. "
                    "\n\nЭнергия периода. Натальный Марс входит в поле Юпитера, и запас инициативы растёт. "
                    "Подробное описание влияния и его длительности. "
                    "\n\nПсихология. Полный психологический раздел. "
                    "\n\nРекомендации. Разделите дело на три посильных шага. Дополнительный платный совет."
                ),
            }
            for index in range(4)
        ],
        "overview": {
            "headline": "Полный обзор",
            "key": [{"interp": "Полная платная трактовка из ключевого обзора."}],
            "groups": [{"items": [{"interp": "Полная платная трактовка из группы."}]}],
        },
    }
    original = copy.deepcopy(source)

    result = main._brief_report(source, "transit")

    assert result["access_mode"] == "brief"
    assert len(result["aspects"]) == 3
    assert "overview" not in result
    for aspect in result["aspects"]:
        assert "Полная платная трактовка" not in aspect["interp"]
        assert "Суть транзита." not in aspect["interp"]
        assert len(aspect["interp"]) <= 220
        assert aspect["interp"].rstrip().endswith((".", "!", "?"))
    assert "Марс входит в поле Юпитера" in result["aspects"][0]["interp"]
    assert "Совет: Разделите дело" in result["aspects"][0]["interp"]
    assert source == original


def test_paid_transit_retains_complete_overview(monkeypatch):
    monkeypatch.setattr(main.db, "get_access_state", lambda uid: {"premium": True})
    source = {
        "aspects": [{"interp": "Полная трактовка"}],
        "overview": {"key": [{"interp": "Полная трактовка в обзоре"}]},
    }

    result = main._protect_interpretations(source, "transit", "detailed", 1)

    assert result["overview"] == source["overview"]


def test_brief_transit_preview_is_coherent_in_english():
    source = {
        "aspects": [{
            "p1": "Venus", "p2": "Neptune", "aspect": "square",
            "interp": (
                "Transit overview. Generic framing that used to fill the free limit. "
                "\n\nEnergy of the period. Natal Venus enters Neptune's field, refining taste and boundaries. "
                "The detailed reading continues here. "
                "\n\nPsychology. Full psychological section. "
                "\n\nRecommendations. Keep promises measured and check shared expenses. The full advice continues."
            ),
        }],
        "overview": {"key": [{"interp": "Full paid English overview."}]},
    }

    result = main._brief_report(source, "transit")
    preview = result["aspects"][0]["interp"]

    assert "Natal Venus enters Neptune's field" in preview
    assert "Advice: Keep promises measured" in preview
    assert "Full paid" not in preview
    assert len(preview) <= 220
    assert preview.rstrip().endswith((".", "!", "?"))

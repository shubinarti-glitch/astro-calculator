from backend import astrology
from backend import interpretations as I


NATAL = {
    "name": "Regression",
    "year": 1990,
    "month": 5,
    "day": 17,
    "hour": 12,
    "minute": 30,
    "lat": 55.7558,
    "lng": 37.6173,
    "city": "Moscow",
    "tz_str": "Europe/Moscow",
    "houses_system": "P",
}


def _point(points, name):
    return next(point for point in points if point["name"] == name)


def _angular_distance(first, second):
    distance = abs((first - second) % 360.0)
    return min(distance, 360.0 - distance)


def test_progression_interpretation_is_distinct_from_transit_in_both_languages():
    ru = I.interpret_progression("Moon", "trine", "Sun", "ru")
    en = I.interpret_progression("Moon", "trine", "Sun", "en")

    assert "Прогрессивная" in ru
    assert "транзит" not in ru.lower()
    assert "дня" not in ru.lower()
    assert "Progressed" in en
    assert "transit" not in en.lower()
    assert "accents of the day" not in en.lower()


def test_deep_transit_uses_distinct_aspect_dynamics_and_phase(monkeypatch):
    pair = {
        "energy": "Энергия пары раскрывается в конкретной жизненной теме.",
        "psychology": "Внутренний процесс становится заметнее и требует внимания.",
        "relationships": "В отношениях меняются привычные способы обмена и границы.",
        "realization": "В делах возникает возможность пересобрать практический подход.",
        "risks": "При перегрузке возможны поспешность и попытка избежать перемен.",
        "advice": "Полезно сверять решения с фактами и оставлять время на адаптацию.",
    }
    monkeypatch.setitem(I.AUTHORED_TRANSIT, "transit|Saturn|Sun", pair)

    square = I.interpret_transit(
        "Saturn", "square", "Sun", "ru", orbit=0.75, movement="сходящийся",
    )
    opposition = I.interpret_transit(
        "Saturn", "opposition", "Sun", "ru", orbit=2.0, movement="расходящийся",
    )

    assert "Суть транзита." in square
    assert "Квадрат создаёт трение" in square
    assert "Энергия нарастает" in square
    assert "Оппозиция проявляет тему через людей" in opposition
    assert "Пик уже пройден" in opposition
    assert square != opposition


def test_fast_and_english_transits_keep_full_fallback(monkeypatch):
    monkeypatch.setattr(I, "AUTHORED_TRANSIT", {})
    fast = I.interpret_transit("Mercury", "trine", "Sun", "ru")
    english = I.interpret_transit("Saturn", "square", "Sun", "en")

    assert "Суть транзита." in fast
    assert "Меркурианская тема" in fast
    assert "Transit overview." in english
    assert "Recommendations." in english


def test_english_planet_descriptions_use_natural_distinct_work_copy():
    planets = ("Sun", "Moon", "Mercury", "Venus", "Mars", "Jupiter", "Saturn", "Uranus", "Neptune", "Pluto")
    openings = []
    for planet in planets:
        work = next(block["text"] for block in I.interpret_sign_full(planet, "Ari", "en") if block["label"] == "Work and vocation")
        openings.append(work.split(".", 1)[0])
        assert not work.startswith("In work"), (planet, work)
    assert len(set(openings)) == len(planets)

    mercury_love = next(block["text"] for block in I.interpret_sign_full("Mercury", "Lib", "en") if block["label"] == "Love and closeness")
    uranus_love = next(block["text"] for block in I.interpret_sign_full("Uranus", "Lib", "en") if block["label"] == "Love and closeness")
    neptune_work = next(block["text"] for block in I.interpret_sign_full("Neptune", "Pis", "en") if block["label"] == "Work and vocation")
    mars_work = next(block["text"] for block in I.interpret_sign_full("Mars", "Ari", "en") if block["label"] == "Work and vocation")
    assert "half a word" not in mercury_love
    assert "without having to explain yourself at length" in mercury_love
    assert "frames and templates" not in uranus_love
    assert "rigid expectations" in uranus_love
    assert "healing practices" not in neptune_work
    assert "contemplative work" in neptune_work
    assert "you suit work demanding" not in mars_work


def test_selected_english_natal_pilot_keeps_practical_context_and_caveats():
    readings = [
        I.authored_sign("Sun", "Ari", "en"),
        I.authored_sign("Moon", "Can", "en"),
        I.authored_sign("Mercury", "Lib", "en"),
        I.authored_sign("Uranus", "Lib", "en"),
        I.authored_house("Sun", 1, "en"),
        I.authored_house("Moon", 4, "en"),
        I.authored_house("Mercury", 7, "en"),
        I.authored_house("Venus", 10, "en"),
        I.interpret_aspect("Sun", "conjunction", "Moon", "en"),
        I.interpret_aspect("Moon", "sextile", "Mars", "en"),
        I.interpret_aspect("Mercury", "square", "Neptune", "en"),
        I.interpret_aspect("Jupiter", "trine", "Saturn", "en"),
    ]
    assert all(len(text) >= 650 for text in readings)
    assert all(not any("а" <= char.lower() <= "я" or char.lower() == "ё" for char in text) for text in readings)
    assert "generational" in readings[3].lower()
    assert "house and aspects" in readings[3].lower()
    assert "not guaranteed" in readings[8].lower()


def test_deep_transits_cover_fast_movers_and_quintile_without_authored_json(monkeypatch):
    monkeypatch.setattr(I, "AUTHORED_TRANSIT", {})

    for moving in ("Sun", "Moon", "Mercury", "Venus"):
        text = I.interpret_transit(moving, "quintile", "Chiron", "ru", orbit=0.4)
        assert "Суть транзита." in text
        assert "Квинтиль раскрывает творческую связь" in text
        assert "Рекомендации." in text

    english = I.interpret_transit("Venus", "quintile", "Chiron", "en")
    assert "creative aspect" in english


def test_transit_target_aliases_normalize_nodes_chiron_and_lilith(monkeypatch):
    monkeypatch.setattr(I, "AUTHORED_TRANSIT", {})
    targets = (
        "Mean_North_Lunar_Node", "True_North_Lunar_Node",
        "Mean_South_Lunar_Node", "True_South_Lunar_Node",
        "Chiron", "Mean_Lilith", "True_Lilith",
    )
    texts = [I.interpret_transit("Venus", "trine", target, "ru") for target in targets]

    assert all("Суть транзита." in text for text in texts)
    assert texts[0] == texts[1]
    assert texts[2] == texts[3]
    assert texts[5] == texts[6]


def test_moving_angles_use_specialized_non_planetary_fallback():
    text = I.interpret_transit(
        "Ascendant", "square", "Sun", "ru", orbit=0.2, movement="сходящийся",
    )

    assert "Транзитный угол карты" in text
    assert "не действие транзитной планеты" in text
    assert "Суть транзита." not in text


def test_all_transit_points_and_aspects_have_russian_interpretations():
    points = (
        "Sun", "Moon", "Mercury", "Venus", "Mars", "Jupiter", "Saturn",
        "Uranus", "Neptune", "Pluto", "Chiron", "Mean_Lilith",
        "True_North_Lunar_Node", "True_South_Lunar_Node",
        "Ascendant", "Descendant", "Medium_Coeli", "Imum_Coeli",
    )
    aspects = ("conjunction", "sextile", "square", "trine", "opposition", "quintile")

    missing = [
        (moving, aspect, target)
        for moving in points
        for target in points
        for aspect in aspects
        if not I.interpret_transit(moving, aspect, target, "ru", orbit=0.5)
    ]

    assert missing == []


def test_chiron_angle_aspects_have_distinct_authored_text_in_both_languages():
    pairs = [
        ("sextile", "Ascendant"),
        ("sextile", "Medium_Coeli"),
        ("trine", "Descendant"),
        ("trine", "Imum_Coeli"),
    ]
    for lang in ("ru", "en"):
        texts = [I.interpret_aspect("Chiron", aspect, angle, lang) for aspect, angle in pairs]
        assert all(texts)
        assert len(set(texts)) == len(pairs)
        if lang == "ru":
            assert all("привнося глубинная рана" not in text.lower() for text in texts)
        else:
            assert all("bringing deep wound" not in text.lower() for text in texts)


def test_russian_transit_chart_svg_localizes_library_fallback_labels(monkeypatch):
    class FakeChartDrawer:
        def __init__(self, chart_data, chart_language, theme):
            self.chart_language = chart_language

        def generate_svg_string(self):
            return "<svg><text>Transit Point</text><text>Natal House</text></svg>"

    monkeypatch.setattr(astrology, "ChartDrawer", FakeChartDrawer)

    ru = astrology._render_chart_svgs(object(), "ru")
    en = astrology._render_chart_svgs(object(), "en")

    assert "Транзитная точка" in ru["svg"]
    assert "Натальный дом" in ru["svg"]
    assert "Transit Point" in en["svg"]
    assert "Natal House" in en["svg"]


def test_direction_interpretation_is_separate_in_both_languages():
    ru = I.interpret_direction("Sun", "square", "Moon", "ru")
    en = I.interpret_direction("Sun", "square", "Moon", "en")

    assert "Дирекционная" in ru and "солнечной дуге" in ru
    assert "транзит" not in ru.lower()
    assert "Solar-arc directed" in en
    assert "transit" not in en.lower()


def test_progression_report_uses_progression_and_direction_texts():
    report = astrology.progression_report(
        dict(NATAL, lang="ru"),
        {"year": 2026, "month": 6, "day": 1, "hour": 12, "minute": 0},
        with_svg=False,
    )

    progression_texts = [a.get("interp", "") for a in report["aspects"] if a.get("interp")]
    direction_texts = [a.get("interp", "") for a in report["directed_aspects"] if a.get("interp")]
    assert progression_texts
    assert all("транзит" not in text.lower() for text in progression_texts)
    assert any("Прогрессивная" in text for text in progression_texts)
    assert direction_texts
    assert all("Дирекционная" in text for text in direction_texts)


def test_return_report_excludes_natal_portrait_but_keeps_android_contract():
    required = {"theme", "planets", "houses", "aspects", "big_three", "period_start", "period_end"}
    forbidden = {"profile", "psych", "spheres", "deep", "essentials"}

    for return_type, month in (("Solar", None), ("Lunar", 6)):
        report = astrology.return_report(
            dict(NATAL, lang="ru"),
            year=2026,
            month=month,
            return_type=return_type,
            with_svg=False,
        )
        assert required <= report.keys()
        assert forbidden.isdisjoint(report)
        assert report["planets"] and report["houses"] and report["aspects"]
        assert set(report["big_three"]) == {"sun", "moon", "asc"}
        assert {"overlay", "tone", "focus", "mood", "lord"} <= report["theme"].keys()


def test_solar_and_lunar_returns_match_the_natal_luminary_longitude():
    """A return is valid only when its defining luminary reaches natal longitude."""
    natal = astrology.natal_report(dict(NATAL, lang="en"), with_svg=False)
    natal_sun = _point(natal["planets"], "Sun")["abs_pos"]
    natal_moon = _point(natal["planets"], "Moon")["abs_pos"]

    solar = astrology.return_report(
        dict(NATAL, lang="en"), year=2026, return_type="Solar", with_svg=False,
    )
    lunar = astrology.return_report(
        dict(NATAL, lang="en"), year=2026, month=6,
        return_type="Lunar", with_svg=False,
    )

    assert _angular_distance(_point(solar["planets"], "Sun")["abs_pos"], natal_sun) < 0.001
    assert _angular_distance(_point(lunar["planets"], "Moon")["abs_pos"], natal_moon) < 0.001
    assert solar["period_start"].startswith("2026-05-")
    assert lunar["period_start"].startswith("2026-06-")


def test_progression_uses_day_for_year_and_solar_arc_for_every_directed_point():
    target = {"year": 2026, "month": 6, "day": 1, "hour": 12, "minute": 0}
    report = astrology.progression_report(
        dict(NATAL, lang="en"), target, with_svg=False,
    )
    natal = astrology.natal_report(dict(NATAL, lang="en"), with_svg=False)

    # 36 elapsed years must move the secondary chart only about 36 ephemeris days.
    assert 36.0 < report["elapsed_years"] < 36.1
    assert report["prog_meta"]["local_datetime"].startswith("1990-06-22T")

    natal_sun = _point(natal["planets"], "Sun")["abs_pos"]
    progressed_sun = _point(report["prog_planets"], "Sun")["abs_pos"]
    arc = report["solar_arc"]["value"]
    assert abs(_angular_distance(progressed_sun, natal_sun) - arc) < 0.01

    natal_positions = {
        point["name"]: point["abs_pos"]
        for point in natal["planets"] + natal["angles"]
    }
    for directed in report["directed"]:
        natal_position = natal_positions.get(directed["name"])
        if natal_position is not None:
            expected = (natal_position + arc) % 360.0
            assert _angular_distance(directed["abs_pos"], expected) < 0.011

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

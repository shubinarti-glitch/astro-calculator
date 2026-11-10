from datetime import datetime
from types import SimpleNamespace

import pytest

from backend import astrology, vedic


NATAL = {
    "name": "Midnight", "year": 1990, "month": 5, "day": 15,
    "hour": 0, "minute": 30, "lat": 55.7558, "lng": 37.6173,
    "tz_str": "Europe/Moscow", "city": "Moscow",
}


def test_progression_preserves_midnight_birth_hour():
    report = astrology.progression_report(
        NATAL, {"year": 2030, "month": 5, "day": 15, "hour": 12, "minute": 0}, with_svg=False,
    )
    from pytz import timezone
    birth = timezone("Europe/Moscow").localize(datetime(1990, 5, 15, 0, 30)).astimezone(timezone("UTC"))
    target = timezone("Europe/Moscow").localize(datetime(2030, 5, 15, 12, 0)).astimezone(timezone("UTC"))
    expected = (birth + (target - birth) / astrology.TROPICAL_YEAR_DAYS).astimezone(timezone("Europe/Moscow")).replace(tzinfo=None)
    assert report["prog_meta"]["local_datetime"].startswith(expected.strftime("%Y-%m-%dT%H:%M"))


def test_progression_rejects_target_before_birth():
    with pytest.raises(ValueError, match="раньше рождения"):
        astrology.progression_report(
            NATAL, {"year": 1990, "month": 5, "day": 14, "hour": 23, "minute": 59}, with_svg=False,
        )


def test_progression_uses_birth_timezone_for_dst_transitions():
    natal = {**NATAL, "year": 2020, "month": 3, "day": 28, "hour": 1, "minute": 30,
             "lat": 51.5074, "lng": -0.1278, "tz_str": "Europe/London", "city": "London"}
    with pytest.raises(ValueError, match="неоднозначно или не существует"):
        astrology.progression_report(
            natal, {"year": 2020, "month": 3, "day": 29, "hour": 1, "minute": 30}, with_svg=False,
        )


def test_panchang_local_noon_is_not_silent_utc_fallback():
    kr = vedic._jd_for_local_noon(2026, 8, 28, "Asia/Krasnoyarsk")
    utc = vedic._jd_for_local_noon(2026, 8, 28, "UTC")
    assert (utc - kr) * 24 == pytest.approx(7, abs=1e-6)
    with pytest.raises(ValueError, match="Неизвестный часовой пояс"):
        vedic._jd_for_local_noon(2026, 8, 28, "Bad/Zone")


def test_transit_passes_keep_multiple_local_minima():
    def moment(day, orb):
        return SimpleNamespace(model_dump=lambda: {
            "date": f"2026-01-{day:02d}T12:00:00",
            "aspects": [{
                "p1_name": "Saturn", "aspect": "square", "p2_name": "Sun", "orbit": orb,
            }],
        })

    moments = SimpleNamespace(transits=[
        moment(1, 2.0), moment(2, 0.1), moment(3, 2.0),
        moment(4, 0.2), moment(5, 2.0), moment(6, 0.1), moment(7, 2.0),
    ])
    passes = astrology._transit_pass_minima(moments, lambda _: True)
    assert [p[1]["date"][:10] for p in passes] == ["2026-01-02", "2026-01-04", "2026-01-06"]


def test_calendar_refines_and_filters_events_by_local_calendar_days(monkeypatch):
    from datetime import datetime

    class FakeEphemerisFactory:
        def __init__(self, **_kwargs):
            pass

        def get_ephemeris_data_as_astrological_subjects(self):
            return [SimpleNamespace(
                year=2026, month=11, day=1,
                moon=SimpleNamespace(sign="Sco"),
                lunar_phase=SimpleNamespace(moon_phase_name="New Moon", moon_emoji="🌑"),
            )]

    class FakeTransitRange:
        def get_transit_moments(self):
            aspects = [
                ("Sun", "square", "Moon"),
                ("Mars", "trine", "Venus"),
                ("Venus", "opposition", "Saturn"),
                ("Saturn", "sextile", "Sun"),
            ]
            return SimpleNamespace(transits=[{
                "date": "2026-11-15T12:00:00",
                "aspects": [
                    {"p1_name": p1, "aspect": aspect, "p2_name": p2, "orbit": 0.2}
                    for p1, aspect, p2 in aspects
                ],
            }])

    bounds_seen = []

    def refine(_natal, _loc, _tz, key, info, *, bounds=None, refinement_cache=None, targeted_pair=False):
        bounds_seen.append(bounds)
        dates = {
            "Sun": "2026-10-31T13:00:00+00:00",    # Oct 31 in Berlin: outside November
            "Mars": "2026-10-31T23:30:00+00:00",   # Nov 1 in Berlin: inside November
            "Venus": "2026-12-01T05:00:00+00:00",  # Dec 1 in Berlin: outside November
            "Saturn": "2026-11-30T22:30:00+00:00", # Nov 30 in Berlin: inside November
        }
        return {"date": dates[key[0]], "orb": 0.2, "asp": info["asp"]}

    monkeypatch.setattr(astrology, "build_subject", lambda **_kwargs: object())
    monkeypatch.setattr(astrology, "_meta", lambda _natal: {})
    monkeypatch.setattr(astrology, "EphemerisDataFactory", FakeEphemerisFactory)
    monkeypatch.setattr(astrology, "TransitsTimeRangeFactory", lambda *_args: FakeTransitRange())
    monkeypatch.setattr(astrology, "_refine_transit_pass", refine)

    report = astrology.transit_calendar_report(
        {**NATAL, "lang": "ru"},
        {"year": 2026, "month": 11, "day": 1},
        {"year": 2026, "month": 11, "day": 30},
        {"lat": 48.4, "lng": 9.99, "tz_str": "Europe/Berlin", "city": "Ulm"},
    )

    assert len(bounds_seen) == 4
    expected_bounds = (datetime(2026, 11, 1), datetime(2026, 11, 30, 23, 59, 59, 999999))
    assert all(bound == expected_bounds for bound in bounds_seen)
    assert [(event["p1"], event["date"]) for event in report["events"]] == [
        ("Mars", "2026-11-01"), ("Saturn", "2026-11-30"),
    ]

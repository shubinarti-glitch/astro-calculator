from datetime import datetime

from backend import astrology as A


def test_targeted_calendar_refinement_matches_full_aspects_and_shared_window_cache():
    natal = A.build_subject(
        name="Albert Einstein", year=1879, month=3, day=14, hour=11, minute=30,
        lat=48.4011, lng=9.9876, tz_str="Europe/Berlin", city="Ulm, Germany",
        nation="GB", houses_system="P", zodiac_type="Tropic",
    )
    location = {"lat": 48.4011, "lng": 9.9876}
    bounds = (datetime(2026, 11, 1), datetime(2026, 11, 30, 23, 59, 59, 999999))
    candidate = {"date": "2026-11-01T12:00:00", "orb": 1.0}
    keys = [
        ("Sun", "trine", "Ascendant"),
        ("Sun", "sextile", "Descendant"),
    ]
    full_cache, targeted_cache = {}, {}

    for key in keys:
        full = A._refine_transit_pass(
            natal, location, "Europe/Berlin", key, candidate,
            bounds=bounds, refinement_cache=full_cache,
        )
        targeted = A._refine_transit_pass(
            natal, location, "Europe/Berlin", key, candidate,
            bounds=bounds, refinement_cache=targeted_cache, targeted_pair=True,
        )
        assert targeted == full

    # Two aspect keys share their coarse ephemeris window; both results must survive.
    shared_results = [
        results
        for window in targeted_cache.values()
        for results in [window["results"]]
        if all(key in results for key in keys)
    ]
    assert shared_results


def test_targeted_calendar_refinement_bounds_ephemeris_window_cache(monkeypatch):
    natal = A.build_subject(
        name="Albert Einstein", year=1879, month=3, day=14, hour=11, minute=30,
        lat=48.4011, lng=9.9876, tz_str="Europe/Berlin", city="Ulm, Germany",
        nation="GB", houses_system="P", zodiac_type="Tropic",
    )
    location = {"lat": 48.4011, "lng": 9.9876}
    bounds = (datetime(2026, 11, 1), datetime(2026, 11, 30, 23, 59, 59, 999999))
    key = ("Sun", "trine", "Ascendant")
    full_cache, targeted_cache = {}, {}
    monkeypatch.setattr(A, "_MAX_CALENDAR_REFINEMENT_WINDOWS", 4)

    for day in (1, 5, 9, 13, 17):
        candidate = {"date": f"2026-11-{day:02d}T12:00:00", "orb": 1.0}
        full = A._refine_transit_pass(
            natal, location, "Europe/Berlin", key, candidate,
            bounds=bounds, refinement_cache=full_cache,
        )
        targeted = A._refine_transit_pass(
            natal, location, "Europe/Berlin", key, candidate,
            bounds=bounds, refinement_cache=targeted_cache, targeted_pair=True,
        )
        assert targeted == full
        assert len(targeted_cache) <= 4

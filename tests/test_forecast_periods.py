from datetime import datetime
from types import SimpleNamespace

import pytest
from backend import astrology as A


@pytest.fixture
def forecast_setup(monkeypatch):
    calls = []
    class Ephemeris:
        def __init__(self, **kwargs):
            calls.append(kwargs)
        def get_ephemeris_data_as_astrological_subjects(self):
            return []
    monkeypatch.setattr(A, "build_subject", lambda **kwargs: SimpleNamespace())
    monkeypatch.setattr(A, "_meta", lambda model: {})
    monkeypatch.setattr(A, "_annual_profection", lambda *args: {"house_num": 1, "sign_ru": "Aries"})
    monkeypatch.setattr(A, "_progressed_moon", lambda *args: {"sign": "Ari"})
    monkeypatch.setattr(A, "_editorial_text", lambda key: "Synthetic ")
    monkeypatch.setattr(A, "EphemerisDataFactory", Ephemeris)
    monkeypatch.setattr(A, "TransitsTimeRangeFactory", lambda *args: SimpleNamespace(get_transit_moments=lambda: []))
    monkeypatch.setattr(A, "_transit_pass_minima", lambda *args: [])
    monkeypatch.setattr(A, "_forecast_by_sphere", lambda *args: [])
    return calls


def natal():
    return dict(name="Synthetic QA", year=1990, month=1, day=1, hour=12, minute=0,
                lat=55.75, lng=37.62, tz_str="Europe/Moscow", lang="en")


@pytest.mark.parametrize("day", [18, 19])
def test_forecast_accepts_a_single_calendar_day(forecast_setup, day):
    date = dict(year=2026, month=10, day=day)
    result = A.forecast_report(natal(), date, date)
    assert result["start"] == result["end"] == f"2026-10-{day}"
    assert result["summary"]
    assert len(forecast_setup) == 1


def test_forecast_rejects_reversed_dates(forecast_setup):
    with pytest.raises(ValueError):
        A.forecast_report(natal(), dict(year=2026, month=10, day=19), dict(year=2026, month=10, day=18))
    assert not forecast_setup


@pytest.mark.parametrize("source_date, expected_end", [
    ("2026-10-01T12:00:00", datetime(2026, 10, 2, 6)),
    ("2026-09-30T21:00:00+00:00", datetime(2026, 10, 1, 18)),
])
def test_refinement_keeps_boundary_events_inside_requested_days(monkeypatch, source_date, expected_end):
    windows = []
    bounds = (datetime(2026, 10, 1), datetime(2026, 10, 31, 23, 59, 59, 999999))
    key = ("Saturn", "square", "Sun")
    class Ephemeris:
        def __init__(self, **kwargs):
            self.start = kwargs["start_datetime"]
            self.end = kwargs["end_datetime"]
            windows.append((self.start, self.end))
        def get_ephemeris_data_as_astrological_subjects(self):
            return [self.start, self.end]
    def moments(model, times):
        return SimpleNamespace(get_transit_moments=lambda: SimpleNamespace(transits=[{
            "date": time.isoformat(), "aspects": [{"p1_name": key[0], "aspect": key[1], "p2_name": key[2],
            "orbit": (time - datetime(2026, 9, 30, 20)).total_seconds() / 86400}]
        } for time in times]))
    monkeypatch.setattr(A, "EphemerisDataFactory", Ephemeris)
    monkeypatch.setattr(A, "TransitsTimeRangeFactory", moments)
    result = A._refine_transit_pass(None, {"lat": 55.75, "lng": 37.62}, "Europe/Moscow", key,
                                  {"date": source_date, "orb": 1}, bounds=bounds)
    assert windows
    assert windows[0][1] == expected_end
    assert all(bounds[0] <= start <= end <= bounds[1] for start, end in windows)
    assert bounds[0] <= datetime.fromisoformat(result["date"]) <= bounds[1]


@pytest.mark.parametrize("value,tz,expected", [
    ("2026-09-30T21:00:00+00:00", "Europe/Moscow", "2026-10-01"),
    ("2026-10-01T02:00:00+00:00", "America/New_York", "2026-09-30"),
    ("2026-10-01T12:00:00", "Europe/Moscow", "2026-10-01"),
])
def test_forecast_dates_use_the_requested_local_day(value, tz, expected):
    assert A._forecast_local_datetime(value, tz).date().isoformat() == expected


def test_report_preserves_exact_utc_time_but_displays_local_date(forecast_setup, monkeypatch):
    info = {"date": "2026-09-30T21:00:00+00:00", "orb": 0.1}
    monkeypatch.setattr(A, "_transit_pass_minima", lambda *args: [(("Saturn", "square", "Sun"), info)])
    monkeypatch.setattr(A, "_refine_transit_pass", lambda *args, **kwargs: info)
    monkeypatch.setattr(A, "_forecast_sphere_key", lambda *args: "career")
    monkeypatch.setattr(A.I, "transit_forecast_parts", lambda *args: {"interpretation": "Synthetic meaning", "advice": "Synthetic action"})
    period = dict(year=2026, month=10, day=1)
    event = A.forecast_report(natal(), period, period)["events"][0]
    assert event["date"] == "2026-10-01"
    assert event["exact_datetime"] == info["date"]

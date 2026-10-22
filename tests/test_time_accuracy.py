from datetime import datetime, timedelta, timezone
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
    target = datetime(2030, 5, 15, 12, 0)
    natal = astrology.build_subject(**NATAL)
    birth_utc = datetime.fromisoformat(natal.iso_formatted_utc_datetime)
    target_utc = (target - timedelta(hours=3)).replace(tzinfo=timezone.utc)
    expected_years = (target_utc - birth_utc).total_seconds() / (astrology.TROPICAL_YEAR_DAYS * 86400)
    expected_progressed = (birth_utc + timedelta(days=expected_years)).astimezone(astrology._timezone("Europe/Moscow"))
    assert report["elapsed_years"] == pytest.approx(expected_years, abs=0.01)
    assert report["prog_meta"]["local_datetime"].startswith(expected_progressed.strftime("%Y-%m-%dT%H:%M"))


def test_progression_rejects_dates_before_birth_and_dst_gaps():
    with pytest.raises(ValueError, match="раньше даты рождения"):
        astrology.progression_report(NATAL, {"year": 1980, "month": 5, "day": 15}, with_svg=False)

    new_york = astrology.build_subject(**dict(NATAL, tz_str="America/New_York"))
    with pytest.raises(ValueError, match="однозначное местное время"):
        astrology._progression_moment(new_york, datetime(2026, 3, 8, 2, 30))


def test_progression_at_birth_has_zero_elapsed_years():
    report = astrology.progression_report(
        NATAL, {"year": 1990, "month": 5, "day": 15, "hour": 0, "minute": 30}, with_svg=False,
    )
    assert report["elapsed_years"] == 0


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

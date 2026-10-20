import re

from backend import interpretations as I
from backend import transit_english as TE


def test_english_transit_corpus_is_complete():
    assert I.AUTHORED_TRANSIT, "Russian source corpus must be present for release verification"
    assert set(I.AUTHORED_TRANSIT_EN) == set(I.AUTHORED_TRANSIT)
    for key, record in I.AUTHORED_TRANSIT_EN.items():
        assert set(record) == set(TE.FIELDS), key
        for field, value in record.items():
            assert value.strip() and not re.search("[а-яА-ЯёЁ]", value), (key, field)


def test_neptune_to_five_natal_points_uses_distinct_energy_and_risk_openings():
    targets = ("Medium_Coeli", "Pluto", "Saturn", "Sun", "Venus")
    for corpus in (I.AUTHORED_TRANSIT, I.AUTHORED_TRANSIT_EN):
        for field in ("energy", "risks"):
            openings = [
                tuple(re.findall(r"[\w’'-]+", corpus[f"transit|Neptune|{target}"][field].lower())[:6])
                for target in targets
            ]
            assert len(set(openings)) == len(targets), (field, openings)


def test_mars_uranus_risks_warn_about_behavior_without_predicting_injury():
    keys = ("transit|Mars|Uranus", "transit|Uranus|Mars")
    for corpus in (I.AUTHORED_TRANSIT, I.AUTHORED_TRANSIT_EN):
        for key in keys:
            risk = corpus[key]["risks"].lower()
            assert "accident" not in risk
            assert "injur" not in risk
            assert ("безопасност" in risk) if corpus is I.AUTHORED_TRANSIT else ("safety" in risk)


def test_every_authored_translation_is_used_without_truncation():
    aliases = {"North_Node": "True_North_Lunar_Node", "South_Node": "True_South_Lunar_Node", "Lilith": "Mean_Lilith"}
    for key, record in I.AUTHORED_TRANSIT_EN.items():
        _, moving, target = key.split("|")
        text = I.interpret_transit(aliases.get(moving, moving), "square", aliases.get(target, target), "en")
        for value in record.values():
            assert value in text, key


def test_english_aspect_phase_and_angle():
    square = I.interpret_transit("Saturn", "square", "Sun", "en", 0.5, "applying")
    opposition = I.interpret_transit("Saturn", "opposition", "Sun", "en", 2, "separating")
    assert "A square creates friction" in square and "energy is building" in square
    assert "An opposition" in opposition and "peak has passed" in opposition
    angle = I.interpret_transit("Ascendant", "trine", "Moon", "en", 0.2, "сходящийся")
    assert "not a planetary transit" in angle and "energy is building" in angle
    assert not re.search("[а-яА-ЯёЁ]", angle)


def test_all_supported_english_transits_have_text():
    points = list(I._TRANSIT_DEEP_NAME) + list(I._TRANSIT_ANGLES)
    for moving in points:
        for target in points:
            for aspect in TE.ASPECTS:
                text = I.interpret_transit(moving, aspect, target, "en", 0.5)
                assert text and not re.search("[а-яА-ЯёЁ]", text), (moving, aspect, target)

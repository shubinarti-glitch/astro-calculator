from types import SimpleNamespace

from backend import astrology, constants
from backend import interpretations as I


def test_current_transit_guidance_covers_all_public_planets_in_both_languages():
    planets = ["Sun", "Moon", "Mercury", "Venus", "Mars", "Jupiter", "Saturn", "Uranus", "Neptune", "Pluto"]
    for lang in ("ru", "en"):
        texts = [I.current_transit_guidance(planet, lang) for planet in planets]
        assert all(len(text) >= 150 for text in texts)
        assert len(set(texts)) == len(planets)
    assert I.current_transit_guidance("not-a-planet", "ru") == ""


def test_current_transit_api_items_include_planet_specific_guidance(monkeypatch):
    attrs = constants.PLANET_ORDER[:10]
    names = ["Sun", "Moon", "Mercury", "Venus", "Mars", "Jupiter", "Saturn", "Uranus", "Neptune", "Pluto"]
    subject = SimpleNamespace()
    for idx, (attr, name) in enumerate(zip(attrs, names)):
        setattr(subject, attr, SimpleNamespace(model_dump=lambda idx=idx, name=name: {
            "name": name,
            "sign": "Leo",
            "abs_pos": 120.0 + idx,
            "retrograde": False,
        }))

    monkeypatch.setattr(astrology, "build_subject", lambda **_: subject)
    monkeypatch.setattr(astrology, "_sign_change", lambda *args: None)

    result = astrology.current_transits("ru")

    assert len(result) == 10
    assert all(item["guidance"] for item in result)
    assert len({item["guidance"] for item in result}) == 10
    assert all(item["meaning"] and item["sign_ru"] for item in result)

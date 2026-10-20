import json
from pathlib import Path


GLOSSARY = Path(__file__).resolve().parents[1] / "data" / "editorial" / "glossary-v1.json"


def test_progressions_and_directions_are_explained_as_distinct_methods():
    glossary = json.loads(GLOSSARY.read_text(encoding="utf-8"))
    ru = next(text for title, text in glossary["ru"] if title == "Прогрессии и дирекции")
    en = next(text for title, text in glossary["en"] if title == "Progressions / directions")

    assert "во вторичных прогрессиях" in ru.lower()
    assert "дирекции используют отдельную шкалу" in ru.lower()
    assert "day after birth" in en.lower()
    assert "directions use a separate scale" in en.lower()
    assert len(glossary["ru"]) == len(glossary["en"])

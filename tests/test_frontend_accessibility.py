from html.parser import HTMLParser
from pathlib import Path


FRONTEND = Path(__file__).resolve().parents[1] / "frontend"


class MarkupAudit(HTMLParser):
    def __init__(self):
        super().__init__()
        self.ids = set()
        self.dialogs = []
        self.password_toggles = []

    def handle_starttag(self, tag, attrs):
        attributes = dict(attrs)
        if "id" in attributes:
            self.ids.add(attributes["id"])
        classes = set(attributes.get("class", "").split())
        if "modal-overlay" in classes:
            self.dialogs.append(attributes)
        if tag == "button" and "pw-eye" in classes:
            self.password_toggles.append(attributes)


def test_every_static_modal_has_dialog_semantics_and_a_name():
    parser = MarkupAudit()
    parser.feed((FRONTEND / "index.html").read_text(encoding="utf-8"))

    assert len(parser.dialogs) == 10
    for dialog in parser.dialogs:
        assert dialog.get("role") == "dialog"
        assert dialog.get("aria-modal") == "true"
        assert dialog.get("aria-labelledby") in parser.ids


def test_password_visibility_controls_are_keyboard_focusable():
    parser = MarkupAudit()
    parser.feed((FRONTEND / "index.html").read_text(encoding="utf-8"))

    assert len(parser.password_toggles) == 4
    assert all(control.get("tabindex") != "-1" for control in parser.password_toggles)


def test_modal_layout_can_scroll_on_short_viewports():
    stylesheet = (FRONTEND / "css" / "style.css").read_text(encoding="utf-8")

    assert ".modal-overlay {" in stylesheet
    assert "overflow-y: auto;" in stylesheet
    assert "max-height: calc(100dvh - 32px);" in stylesheet
    assert "@media (max-height: 480px)" in stylesheet

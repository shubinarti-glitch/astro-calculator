"""English transit copy; authored translations stay in the private data layer."""
import json
from pathlib import Path

FIELDS = ("energy", "psychology", "relationships", "realization", "risks", "advice")
ASPECTS = {
    "conjunction": "A conjunction brings both functions into a single flow: the theme becomes prominent and calls for direct involvement. The energy is concentrated, making a conscious choice of expression especially important.",
    "sextile": "A sextile offers an opportunity that opens through initiative, a conversation or a concrete step. The support of this period does not work automatically: notice the opening and put it into practice.",
    "square": "A square creates friction between familiar ways of living and the new demands of the period. Obstacles reveal a weak point, while accumulated tension calls for action, discipline and a change in how you apply your skills.",
    "trine": "A trine provides natural support and lets both functions work together without intense inner conflict. Ease becomes a result when you develop the available resources rather than simply enjoying favourable conditions.",
    "opposition": "An opposition brings the theme into focus through other people and external circumstances, confronting you with two poles of the same task. Rather than choosing one extreme, it calls for agreement, boundaries and reclaiming a quality you have noticed in someone else.",
    "quintile": "A quintile reveals a creative aspect linking two functions and encourages an unconventional way of expressing them. This is not passive good fortune, but an ability that becomes apparent through interest, experimentation and practice.",
}
TIMING = {
    "Sun": "The solar emphasis is brief, usually lasting a few days around the exact aspect.",
    "Moon": "The lunar emphasis is fleeting and is usually noticeable for a few hours or one to two days.",
    "Mercury": "The Mercurial theme usually unfolds over a few days and may repeat during retrograde motion.",
    "Venus": "The Venusian emphasis usually lasts a few days or weeks and may return during a retrograde loop.",
    "Mars": "The influence of Mars usually develops quickly and is felt for a few days to several weeks.",
    "Jupiter": "The Jupiterian theme develops over months and may return during a retrograde passage.",
    "Saturn": "The Saturnian process lasts for months and often unfolds in several waves, establishing a new level of responsibility.",
    "Uranus": "Uranus works over an extended period and in waves: an external event may happen quickly, but the restructuring takes months.",
    "Neptune": "The Neptunian background develops slowly, sometimes remaining noticeable for more than a year and repeating during a retrograde loop.",
    "Pluto": "Plutonian restructuring belongs to long cycles and unfolds in stages rather than through a single event.",
    "Chiron": "The theme of Chiron develops gradually and may bring you back to the same vulnerability several times, each time opening a new way of working with it.",
    "North_Node": "The nodal emphasis marks a stage in a cycle, connecting your present choices with the direction of further development.",
    "South_Node": "The nodal emphasis brings accumulated experience and recurring patterns to the surface, helping you distinguish useful skills from exhausted habits.",
    "Lilith": "The emphasis of Lilith calls for separating strong emotional reactions from facts and engaging consciously with the shadow theme.",
}
ROLES = {
    "North_Node": "the direction of growth and the challenges of new experience",
    "South_Node": "accumulated experience, familiar patterns and the past",
    "Lilith": "repressed desires, shadow passions and the impulse to rebel",
}


def load_authored(directory=None):
    directory = directory or Path(__file__).resolve().parent.parent / "data" / "transit_en"
    result = {}
    for path in sorted(Path(directory).glob("part*.json")):
        raw = json.loads(path.read_text(encoding="utf-8"))
        for key, value in raw.items():
            if key in result:
                raise ValueError(f"Duplicate English transit key: {key}")
            if not isinstance(value, dict) or any(
                not isinstance(value.get(field), str) or not value[field].strip()
                for field in FIELDS
            ):
                raise ValueError(f"Incomplete English transit: {key}")
            result[key] = value
    return result


def phase(orbit, movement):
    if orbit is None:
        return "The exact orb, speed and retrograde repetitions clarify the strength and stage of this influence."
    proximity = "close to exact" if orbit <= 1 else "within its effective orb"
    motion = (movement or "").lower()
    if "расход" in motion or "separ" in motion:
        direction = "The peak has passed; now it is more important to reflect on the consequences and consolidate the result."
    elif "сход" in motion or "app" in motion:
        direction = "The energy is building, so it is worth preparing the main decisions now."
    else:
        direction = "The current phase is clarified by speed and any possible retrograde repetition."
    return f"With an orb of {orbit:.2f}°, the aspect is {proximity}. {direction}"


def generic_pair(source, focus):
    return {
        "energy": f"Energy connected with {source} activates the area of {focus}, making events in this sphere more noticeable.",
        "psychology": f"Internally, it becomes more important to reconcile the impulse of the period with the way {focus} is expressed.",
        "relationships": "Interactions show where honest exchange, clear boundaries and respect for different responses are needed.",
        "realization": "In practical matters, turn the emerging impulse into one observable action and assess its result.",
        "risks": "Difficulty arises when temporary intensity is mistaken for a final decision, or when you act without checking the circumstances.",
        "advice": "Observe the recurring pattern, relate it to the orb and choose an action appropriate to the real situation.",
    }


def render(pair, moving, aspect, orbit, movement):
    return (
        f"Transit overview. {ASPECTS[aspect]} {TIMING.get(moving, '')}\n\n"
        f"Energy of the period. {pair['energy']} {phase(orbit, movement)}\n\n"
        f"Psychology. {pair['psychology']}\n\n"
        f"Relationships. {pair['relationships']}\n\n"
        f"Practical expression. {pair['realization']}\n\n"
        f"Challenging expressions. {pair['risks']}\n\n"
        f"Recommendations. {pair['advice']}"
    )

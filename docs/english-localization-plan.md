# Full English localization — approved 2026-09-07

Scope: all public website copy, complete authored interpretations (including
234 transit records × six sections), SEO and standalone pages, UI errors,
print/PDF reports. Preserve Russian copy, calculation logic, existing changes,
content override keys, privacy and escaping protections.

Parallel ownership: four translators own `data/transit_en/part0..3.json`;
frontend worker owns app.js/i18n.js/index.html; pages worker owns seo.py and
standalone HTML. Parent owns transit integration, backend gaps and tests.
English content under data remains private and is not added to public Git.

Acceptance: complete key/section coverage, no Cyrillic in English authored
fields, full six-section transit output with aspect/phase differences,
RU regressions unchanged, dictionary parity and JavaScript syntax, isolated
backend tests and real local API/browser checks. Native APK UI is outside this
website release; shared API translations are included.

No deployment or production writes without separate release authorization.

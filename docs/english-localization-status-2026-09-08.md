# English localization — 2026-09-08

Localization and editorial PDF export verified locally. Ready for the authorized
release; deployment outcome is recorded separately after live verification.

## Content

- 234 authored transit records, 1,404 translated sections, in private
  `data/transit_en/part0.json` through `part3.json`.
- Full English transit rendering, distinct aspect dynamics, timing and phase;
  moving angles retain their separate interpretation.
- Main-interface localization and accessible labels, language-aware links and
  API requests; existing print/PDF copy checked.
- 240 English placement pages and catalog, including all 20 custom SEO answers,
  titles and descriptions and the related/featured sections.
- English privacy and terms pages and about-page metadata/accessibility.
- Weekly digest and unsubscribe page, API errors and geocoding language.
- Google Play button and QR link retained from the preceding request.

## Verification

- Full isolated-database suite: **412 passed**, one dependency deprecation warning.
- Final transit/SEO recheck: **274 passed**.
- All 234 transit keys and six fields match the Russian source; no Cyrillic;
  rendering tests require every translated field to appear without truncation.
- English natal, progression, return, transit, synastry, forecast and calendar
  reports checked for Russian strings; English Panchang checked separately.
- Main page and example natal calculation verified in a local browser.
- Agents verified language switches, standalone pages and print text helpers.
- JavaScript syntax and diff whitespace checks passed.
- Editorial PDF builder: 18 Node tests passed, including real calculation schemas,
  tool-specific routing, escaping, returned-metadata cover and internal-field removal.
- Browser PDF review found and corrected duplicate natal prose and narrow tables.
  Full Russian export exposed a canvas-size limit (blank pages). Replaced the giant
  canvas with sequential bounded-page rendering and progress indication.
  Full English export: 26 pages, 13.2 MB, visually reviewed; no sliced text lines
  in the inspected page breaks. Russian bounded export: 59 nonblank pages, followed
  by lossless long-paragraph splitting and heading groups to improve pagination.
  Final Russian re-export: 51 pages, 30.9 MB, every page checked for blank output;
  contact sheet, cover and a previously problematic page visually reviewed.
  Long reports may take several minutes; progress is displayed on the PDF button.
- A consistent SQLite backup (integrity check: ok) and code/data archive exist at
  `/opt/astro/release_backups/en-20260908-pdf/` on production.

## Boundaries and release notes

- Actual paid PDF download and authenticated UI flows have not received a full
  end-to-end browser check in this localization task. No purchases or mailings
  were made. Tests use temporary SQLite databases, not production data.
- Native browser date/time controls use the browser/OS language.
- Standalone legal/about pages localize with JavaScript; their initial raw HTML
  metadata remains Russian. English placement SEO pages render server-side.
- Native APK interface/resources are not part of this website release.
- The private `data/` directory is gitignored. Deployment MUST explicitly include
  all four English transit JSON files, without exposing private data in Git or
  replacing production DB, secrets, overrides or unrelated content.
- Existing unrelated local changes, including the premium redesign, are retained.
  User authorized this localization/PDF release. Deploy only the exact release
  commit, not unrelated intervening social-media commits in local main.

# AstroSMap 1.7.5: approved scope and acceptance

User approval: 18 October 2026. All audit items accepted except item 10:
the recurring bottom navigation animation must remain enabled.

Preserve established product concepts: the animated pet is a defining feature, not an obstruction to remove. Preserve its artwork, movement, dragging, sleep, reactions and page-specific explanations. Improve surrounding UI without replacing or reducing these functions.

Implementation and verification are separate. A checkbox is marked only after
the final implementation and its relevant checks pass.

- [x] 1. Structured forecast interpretation, practical advice and dates.
- [x] 2. Preserve the original animated, draggable pet on every screen, including reports. Tapping explains the current page; sleep, reactions and preferences remain intact. User correction supersedes the compact help proposal.
- [x] 3. Technical forecast reads API `orb`.
- [x] 4. Missing chart has an explicit recovery screen.
- [x] 5. Latest period wins; previous requests cannot overwrite state/history.
- [ ] 6. Personal forecast is discoverable at the top of Tools.
- [x] 7. Calculated chart opens forecast through a safe save operation.
- [x] 8. Period overview uses actual returned events and advice.
- [x] 9. Saved navigation label fits: Карты / Cards.
- [x] 11. Exact-period cached forecast works offline with timestamp and retry;
  account boundaries and entitlements remain enforced.
- [x] 12. Instrumented Compose smoke tests.
- [ ] 13. Core journey, both store variants, large text and offline checks.
- [x] 14. Release cannot fall back to a debug signing certificate.

## Release acceptance

Run Android unit tests for standard and Google Play, astrocore tests, lint and
instrumented tests on standard debug only. Exercise chart calculation, saving,
opening a forecast, switching dates, cached fallback and recovery. Inspect final
screens with large text. Verify Google Play purchase gates using variant tests
and AAB inspection, without generating a Google Play APK.

Build standard release APK and Google Play release AAB. Check package identity,
version, signing certificate, known private prose scan and archive hashes.
Preserve mapping files. Prepare release notes and a narrowly scoped commit.
Production publication is a separate step after the concrete release is reviewed.

Unrelated in-progress website sales changes are outside this release.
Authored interpretations remain in private runtime data, outside public sources.

## Verification in progress

- Final app unit tests: standard 47/47, Google Play 47/47.
- Engine: 6/6 tests passed.
- Component UI: 9/9 passed on Pixel 9 / Android 16 after freeing Gradle memory.
- Standard debug Lint: 0 errors (113 pre-existing/advisory warnings, 3 hints).
- Export protection: 20 passed, 1 platform-dependent skip.
- Missing release credentials: isolated negative validation passed.
- Real local API: all 3 brief forecast events include interpretation and advice.
- Final journey, offline, regenerated release packages and final source commit: pending.

## Visual refinement: preserve identity, improve reading

Observed on the actual forecast screen: default Material cards introduced grey surfaces foreign to AstroSMap; technical metadata competed with interpretation; overview preceded the page heading; dense paragraphs had insufficient separation.

Use the existing AstroPanel in both themes. Keep the original animated draggable pet and recurring navigation cue. Place the report heading before its overview. Use bodyLarge for interpretations and advice, separate metadata from reading text, and distinguish practical advice with a subtle existing gold tint. Do not replace the global theme, fonts or artwork.

References: Android content structure https://developer.android.com/design/ui/mobile/guides/layout-and-content/content-structure and Material type hierarchy https://m3.material.io/styles/typography/applying-type . These support layout decisions; they do not establish user preference or conversion improvement.

Verify on the installed debug app using a synthetic chart and local API, inspect final screenshots, and repeat the large-font and pet interaction checks. Production publication remains outside this verification.

Visual refinement verification: standard debug build succeeded; MainActivityForecastJourneyTest plus ForecastContentUiTest passed all 6 tests. Synthetic chart created and saved without duplicates, rapid period selection checked, large-font interpretation/advice and draggable animated pet hint checked. Inspected refined-forecast-overview.png and refined-forecast-advice.png from the installed emulator. Native panels and original pet visible. The overview capture displayed cached-data notice; this is not proof of a fresh monthly response on that capture. Remaining release, offline and both-theme acceptance is not declared complete.

Final acceptance update (19 October): standard and Google Play unit suites each pass 47 tests. The separate offline/component/city suite passes 7; preconfigured 840x1600 display with font scale 200% passes 1; preconfigured light theme passes 1. Screenshots inspected for the actual synthetic chart in both configurations. Live resizing during instrumentation produced a harness wait, so display configurations are deliberately set before Activity launch. No runtime resize result is claimed.

Visual inspection found white status icons on the light background. MainActivity now synchronizes system icon brightness with the actual Compose theme, retaining light icons on the branded dark splash. A focused Activity assertion and screenshot verify this correction before release.

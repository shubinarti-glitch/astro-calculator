## Public source update completed — 22 October 2026

The separate public repository https://github.com/shubinarti-glitch/astro-calculator-source
is updated to1.7.6 (versionCode14), commit73d367361a38e41f0478f50af4a563cfe0d77a1d.
Normal push, remote-main SHA and unauthenticated GitHub HTTP were verified. The public
index has342files;339canonical code/build/license blobs match the accepted release source.
Private data, assets, signing credentials and user records are excluded. A4889-string
scan found only previously public technical time-validation messages and HTML/XML/CSS
scaffolding; no editorial interpretation match. Exporter tests20passed,1Windows skip;
Python syntax compilation passed. The public checkout was not rebuilt with substitute
private runtime/build resources; README and SOURCE-RELEASE.md state that boundary.
This supersedes earlier notes that the separate repository was still on1.7.4/pending.

---

## Production release verified — 22 October 2026

Server backend and website APK1.7.6 (14) are published. The owner explicitly asked
agent to perform the migration, overriding the repository's manual-only rule for this
release. Backup verified first; additive migration preserved existing profile rows and
passed integrity_check. All three backend file hashes match the tested staged package;
astro is active, new profile PUT/cancellation routes are exposed, no traceback/HTTP500
was found in the journal since restart. A synthetic daily forecast returned dated
interpretations and advice. Existing production website changes were preserved.

Public download https://astrosmap.ru/astrosmap.apk returned14913074bytes,
SHA256 ac534f9e5f57a1bdca949fa132c339ad47ddb16185f54e1112acb0ae78f99075,
exactly matching the accepted signed standard APK. GooglePlay remains AAB-only;
billing/external-purchase links are disabled in that flavor. Store uploads are the owner's.
Code integrated onto current origin/main and pushed normally at57437a1; its Android
product sources match the tested build. Additional merged-server regressions38passed,
1Windows skip. Four unrelated original working-tree diffs remain untouched.
The separate protected-source repository/export was not updated: its runtime-package
and completeness review is still pending. No claim of standalone source completeness.
Physical-device FPS/battery, TalkBack and actual Doze delivery remain unverified.

---

Historical implementation and acceptance log:

# AstroSMap 1.7.6 — approved improvement work

User approved implementation and subagents on 21 October 2026, following the audit
in .codex-artifacts/audit-2026-10-21/AUDIT-AND-PLAN.md.

Preserve branded splash, cosmic colors, original draggable pet artwork/animation,
sleep/reactions/page hints, and recurring bottom navigation cue. Private editorial
content stays runtime-only. Public source and APK/AAB must not embed it.

## Acceptance status

**Local edit acceptance passed; production publication is not performed.**
The savedId route correction now preserves Save for an edited preview. Actual
client Save sent PUT 200 to the isolated local server, retained the original row
id/client nonce and did not duplicate or change account B's row. New packages,
current dual-variant units and lint passed. The new signed APK edit/save/restart
and native chart check passed on Android16/16KiB. Production owner/schema migration and specific publication approval
remain prerequisites; physical-device limitations and incomplete public-source
export readiness remain explicit below.


| Approved item | Implementation | Evidence / remaining acceptance |
|---|---|---|
| Account-separated charts; explicit guest import | Implemented | Scoped DAO/session guards and regression coverage; Android 15 Room acceptance and synthetic full journey passed; signed code13→14 upgrade preserved the synthetic card; real debug A→B→A isolation passed (own card visible, other account absent); actual local edited-card PUT 200 passed with same id/client nonce, one A row and unchanged B row. |
| Atomic profile update; retry-safe creation | Implemented | SavedId route fix verified: actual debug Save→PUT200 on isolated local server, same original id/client nonce, edited A row unique and B unchanged (GET200). No production server change. |
| Backup exclusions and chart recovery | Implemented | Rules reviewed; signed code13→14 upgrade preserved synthetic birth/card data; backup restore on another device is not established. |
| Forecast city timezone, midnight/month/DST | Implemented | Targeted date/calendar regressions and latest-period full journey passed; physical timezone behavior and final signed-release acceptance remain outstanding. |
| Classified report errors, bounded access cache, latest city query | Implemented | Targeted regressions included in the resumed unit pass; latest offline acceptance passed; final session-change release acceptance pending. |
| Interpretation/advice first, localized dates, report navigation | Implemented | Android 15 components/200% font, IME and timeout-corrected full forecast flow passed; signed Android 15 chart/forecast interactions confirmed; Android 16 signed native chart rendered on 16 KiB pages; rebuilt-package acceptance pending. |
| Interactive planet selection, accessible text, reset | Implemented | Android 15 chart components and create/save/forecast journey passed; signed Android 15 chart/forecast interactions confirmed; Android 16 signed native chart rendered on 16 KiB pages; rebuilt-package acceptance pending. |
| Advice opens correct diary date without replacing notes | Implemented | Android 15 journal components, IME and advice-to-diary save journey passed; signed diary advice/date/add/save returned Saved; saved advice/source restored after force-stop and restart on the signed Android 15 candidate. |
| Notification invitation after useful interaction; refusal respected | Implemented | Eligibility/refusal logic reviewed; signed event reminder permission Allow/schedule/cancel interaction passed; broader refusal/reinstall/device matrix is not established. |
| Original pet with page explanations/contextual action | Implemented | Android 15 component acceptance passed pet drag/hint; signed pet context tap opened the diary successfully; Android 16 signed native chart/16 KiB check passed; rebuilt-package acceptance pending. |
| Light-theme contrast without palette redesign | Implemented | Theme tokens reviewed and 200% font components passed; light and 200% font configuration runs passed; physical device and Android 16 signed native chart rendered on 16 KiB pages; rebuilt-package acceptance pending. |
| Preserve splash; measure startup/frames before optimization | Splash preserved | Emulator measurements/development diagnostics do not establish physical-device startup/frame/battery performance. Physical baseline remains unavailable. |
| Comparison explains changed themes and runtime action | Implemented; Android 15 accepted | Separate compatible day/week/month baseline selection across different windows, strict cache matching retained, localized baseline range and actual themes/advice. Exact current units and 7/7 forecast component checks passed; clean cross-month journey and reviewed clean private comparison captures passed; Android 16 signed native chart/16 KiB check passed; rebuilt-package acceptance pending. |
| Advice retains source; prose-free save analytics | Implemented | Local source heading and successful-save-only event; targeted source/dedup tests and Android 15 diary-save journey passed; signed diary save confirmed; saved advice/source restored after force-stop and restart on the signed Android 15 candidate. |
| Bounded persistent analytics, retries and delivery limits | Implemented | Queue/policy regression coverage; best-effort delivery explicitly documented, not exactly-once or guaranteed background delivery. |
| Explicit event reminder with date/city/cancellation | Implemented | Owner/generation/permission guards and targeted coverage; signed Nov 1/Moscow timezone/time/permission/schedule/cancel flow passed; actual background firing/physical Doze delivery not established. |
| Selected-advice sharing | Conditionally deferred | Authorial-content export rights/scope are unconfirmed. Existing chart-wheel export does not authorize advice export. No new private-text sharing exposed. |
| Reviewable screens before publication | Debug evidence available | Five final-* private captures exist; root reviewed forecast meaning/screens as readable personal runtime content. root reviewed clean accepted debug/signed private captures; user visual approval remains distinct from engineering review. |
| Unit/API/UI regression across variants/configurations | Partial verified | Backend/export, resumed dual-variant units, Android 15 IME and component passes confirmed below. Exact comparison build, fresh dual-variant units, forecast components 7/7, clean network journey and reviewed clean comparison captures passed. Offline, light/font, signed Android 15 upgrade and reminder interactions passed; Android 16 native/16 KiB and signed diary restart persistence passed; edit-fix regression and rebuilt package signature/privacy checks passed; new signed APK quick check remains in progress. TalkBack/physical-device checks unavailable. |
| Signed standard APK and Google Play AAB, privacy/signature/hash | New packages built and checked | Edit-fix packages built; current signatures/privacy hashes verified and listed below. Previous-candidate Android 15 upgrade/Android 16 checks remain separate; new signed APK quick check is in progress. |
| Accurate release report | In progress | This matrix distinguishes code, actual evidence and pending acceptance; publication is not claimed. |

## Current verification evidence and pending acceptance

- `comparison-frozen-debug-build`: **successful in 2 min 4 sec**.
- `StandardDebug-comparison-fresh-unit-junit.log` and
  `GoogleplayDebug-comparison-fresh-unit-junit.log`: **81/81 each**, from actual
  current test classes. Classpaths were regenerated through
  `PrintUnitTestClasspath`; manual runs using cached classpaths reported 78 tests
  because they loaded old ASM classes, and are not the current comparison evidence.
- `final-comparison-content-android15.log`: **7/7 passed**, including localized
  baseline dates, changed runtime themes/actions, pet behavior and 200% font.
- `final-comparison-full-journey-android15.log`: updated real **THIS_MONTH →
  NEXT_MONTH** comparison journey **passed, 1/1 in 178.886 seconds**.
  This is functional acceptance. Root inspected private
  `comparison-forecast-comparison.png` and found a **System UI ANR modal** covering
  the view: that capture does **not** count as new comparison visual acceptance.
- Previous `timeout-fix-clean-full-journey-android15.log`: **1/1 passed in
  130.504 seconds**, covering create/save, latest period, interpretation/advice
  and diary save, but **before cross-month comparison was added to the journey**.
  It remains valid previous evidence, not acceptance of the new cross-month path.
- Five existing `final-*` private captures remain available; root reviewed the
  earlier personal runtime forecast screens. Final cross-month/release capture
  acceptance is still required.
- Earlier retained acceptance: `resumed-ime-clean-android15.log` **3/3 passed**;
  `resumed-components-android15.log` **24/24 passed** (Room 8, City 2, Chart 2,
  Forecast 6, Report 4, Journal 2), including pet drag/hint and 200% font components.
  The newer forecast run contains 7 tests and overlaps that earlier forecast subset:
  **25 distinct component tests have coverage across these runs**, not a single
  fresh 25-test execution. The current 7-test run is the comparison-specific evidence.
- The first IME run was obstructed by a **System UI ANR modal**, not an established
  AstroSMap defect. The earlier full-report failure was traced to a successful
  HTTP 200 monthly forecast taking **14.2 seconds**, beyond the old 10-second read
  timeout; the timeout-corrected full journey has now passed.
- **Environment limitation:** GMS, messaging and Safety Hub were temporarily
  disabled only on the synthetic QA176 device to control memory after System UI
  startup ANRs. Restore those packages after QA. This run does not establish
  Google-service/billing compatibility or performance on a physical device.
- `acceptance-offline-ready-ime-android15.log`: **1/1 passed in 26.245 seconds**.
  The harness now waits for the IME to be actually visible before typing, retains
  its five-second hidden-IME assertion, and scrolls to the top before the cached
  banner assertion. Earlier ANR-obstructed or ambiguous-matcher attempts are not
  counted as successful offline acceptance.
- The Saved screen now clears focus/hides the keyboard before opening a card;
  this was confirmed through physical native interaction. This documentation
  records the existing change; it does not introduce further source edits.
- `acceptance-light` and `acceptance-font200` have now passed; exact results are recorded in the latest evidence below.
- User explicitly authorized shutting down the separate Pixel 9 emulator;
  `emulator-5554` was stopped with its data preserved and Android Studio left open.
  The owned QA176 device then booted cleanly without a System UI ANR.
- Final package build: **successful in 21 min 52 sec**. Latest language build:
  **successful in 11 min 50 sec**. Both lint variants: **0 errors**; standard
  **119 warnings / 3 hints**, Google Play **121 warnings / 3 hints**. Warnings
  are not represented as a clean lint result.
- Language split warning removed by `bundle.language.enableSplit = false`;
  AAB language-targeting metadata reports `LANGUAGE negate=true`.
- Packaged standard APK: **14,913,074 bytes**, SHA-256 prefix `2b17669e…`.
  Packaged Google Play AAB: **17,840,524 bytes**, SHA-256 prefix `0c121b…`.
  Agent verified signatures and **0 matches against 352 known private strings**
  in both packages. These are partial hash references; the release manifest is
  the source for full hashes. Known-string scans are not universal secrecy proofs.
- Cold-restart diary persistence, Android 16 native/16 KiB, edit/PUT correction and rebuilt package checks have passed locally; new signed APK quick check remains in progress. Completed light/font, upgrade and reminder results are recorded below. Source verification after final documentation,
  commit, push and production publication are still outstanding; no production,
  push or commit has been performed.
- **Latest clean configuration acceptance:** `acceptance-light-android15.log`
  **1/1, 21.067 s**; font200 **1/1, 23.351 s**. Network-state evidence records
  active default network **none**, supporting the offline configuration scenario.
- `acceptance-clean-full-journey-android15.log`: actual network end-to-end
  **1/1, 52.619 s**, with `runRealNetworkE2e=true`. The earlier wrong-flag run
  (**4.74 s**) was **skipped**, archived as
  `acceptance-skipped-full-journey-wrong-flag.log`; it is not a passing E2E result.
  New retained synthetic fixture suffix: `9af315f8`.
- Accepted private forecast/offline/light/small/comparison/advice/journal/chart
  captures were pulled and root inspected them **without a system modal**.
  This supersedes the obstructed comparison capture for engineering visual review.
- Signed APK installed with `install -r`: real **1.7.5 code13 → 1.7.6 code14**
  preserved `QAUpgrade176` card/birth data (`acceptance-upgrade-from.txt`,
  `acceptance-upgrade-to.txt`, `release-upgrade-after.png`).
- Signed app Russian locale selected through Account; real production guest
  forecasts loaded for October then November. Baseline dates, changed runtime
  meanings and advice were privately captured in `release-comparison-russian`,
  `release-comparison-meaning`, `release-comparison-advice`; TOC Key Days jump worked.
- Signed reminder UI: November 1 / Moscow / selected time → confirmation → Android
  permission Allow → saved feedback and cancel control → cancellation restored Add.
  Evidence: `release-reminder-dialog`, `release-reminder-scheduled`,
  `release-reminder-cancelled`. Actual future background firing is not established.
- Signed pet context tap → Open diary worked. Advice CTA for November 1 → Add to
  note → Save returned Saved (`release-diary-saved`). **Saved advice and source restored after process restart on Android 15; Android 16 native chart rendered with page size 16384.**
- Source/privacy verification after final documents and commit remain pending.
  Comprehensive unit/API coverage and synthetic manual acceptance do not establish
  all real-account operations: A→B→A visibility isolation and later isolated-local edit/PUT acceptance passed as recorded below. No production/push/commit has occurred.
- Frozen backend/export regressions remain **44 passed, 1 skipped**; the skipped
  check is not counted as passing.
- `resumed-public-source-prose-scan.json`: **335 selected files / 352 known private
  strings / 0 matches**, with its manifest predating the timeout correction and completed comparison changes. Final
  source/package verification remains required; this known-string scan does not
  establish absence of every possible sensitive string.
- Source export remains `ready_to_build/publish = false`: runtime-private assets
  are intentionally excluded. No full public-source build completeness or
  publication authorization is claimed.
- No physical-device startup/frame/battery baseline, Doze guarantee, fresh
  Android Vitals, TalkBack pass, billing/service compatibility or retention
  measurement is claimed.

Deployment/push/publication of this new release require specific permission after
review. No production changes are part of the current implementation step.
Existing unrelated backend/astrology.py and tests/test_api.py,
test_predictive_interpretations.py, test_transit_english.py changes are preserved.


## Signed-device/client isolation evidence and resolved local edit blocker

- Private `accepted-device-checks.json` binds previous signed acceptance to APK
  SHA-256 `2b17669eec6ef00755e3bbe5e0b56c7e9f600013338e739868b0d260a2f8ca06`.
  Android 15 advice/source survived force-stop and restart (`release-diary-restored.png`).
  Temporary QA package disabling has been restored.
- Android 16: signed APK installed, native wheel rendered, **page size 16384**
  (`release-android16-chart.png`). The initial system UI ANR was dismissed;
  this native compatibility check is not a physical-device performance claim.
- Real debug client **A → B → A isolation passed**:
  `acceptance-client-b.json` confirms B's own card visible and A's absent;
  `acceptance-client-a-again.json` confirms A restored and B absent.
  Captures: `release-client-a-cards.png`, `release-client-b-cards.png`,
  `release-client-a-again.png`. The older `in progress` marker in device-check JSON
  predates these separate client results and must not override them.
- **Resolved local blocker:** savedId now derives from the route id.
  Actual editing retains Save and the local client PUT acceptance passed; exact
  new build/unit/device evidence is recorded in the final section below.
  Older candidate checks are not substitutes for new-package acceptance.
- Physical FPS/battery, Doze delivery and TalkBack remain unverified.
  Server-owner schema migration remains the owner's manual production prerequisite;
  no agent bypass through initialization is authorized. New release push/deploy/
  publication still needs specific approval after successful final acceptance.


## Final edit-fix evidence and release boundary

- `edit-fix-packages-build.log`: **SUCCESS, 14 min 03 sec**.
- Current standard APK SHA-256:
  `ac534f9e5f57a1bdca949fa132c339ad47ddb16185f54e1112acb0ae78f99075`.
- Current Google Play AAB SHA-256:
  `3b2d34ad3c6a2ee86e9df691ad58bbfde58559c380cc4ed378a5a5314f65c489`.
  Earlier hashes elsewhere in this chronological evidence are superseded.
- `edit-fix-tests-lint-retry`: **SUCCESS, 8 min 54 sec**.
  Fresh `edit-fix-StandardDebug-unit-junit` and Google Play counterpart:
  **81/81 each**. Lint **0 errors**, standard **119 warnings / 3 hints**,
  Google Play **121 warnings / 3 hints**; warnings are not suppressed as success.
- `edit-fix-journey-aot-android16.log`: **1/1 in 38.483 seconds**. Actual guest
  Save and Open forecast worked; all rows/text caches were unchanged before Save,
  then the same id/clientId was retained without duplication. The first pre-AOT
  attempt failed with instrumentation startup ANR; diagnostics were retained and
  that attempt is **not accepted** as a passing run.
- Actual debug account-A edit UI Save → **HTTP PUT 200**.
  `acceptance-client-atomic-update.json`: original id/client nonce retained,
  edited A name one row, B unchanged one row, GET200.
  **This server is isolated local QA, not production.**
- Final known-prose scans: APK **286 entries**, AAB **296 entries**, each checked
  against **352 known private strings**, **0 hits**. Source dry run:
  **336 selected files, 0 hits**. Known-string scan limits remain; the source
  export is **not publication-ready**, and dry run is not publication.
- New signed APK quick check is currently in progress under root.
  Production schema/owner migration remains the owner's manual prerequisite;
  specific release publication approval is still required. No production/push
  has occurred; root will handle the scoped commit after final documentation.
- Physical FPS/battery, TalkBack, actual future reminder firing/Doze and production
  account migration are not established by emulator/local-server acceptance.

### Final signed-candidate check

New APK ac534f9e installed with existing data on Android16/16KiB. Existing synthetic
card renamed through Edit→Calculate→Save; QARelease176Edited remained after force-stop
and restart, one card shown, native wheel rendered and original pet present. Private
evidence: edit-fix-signed-restart-ui.json, release-edit-fix-signed-chart.png.
Production deployment, owner schema migration and public-source readiness remain pending.

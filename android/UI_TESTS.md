# Android UI smoke checks

Run on the standard debug variant only:

```powershell
.\gradlew.bat :app:connectedStandardDebugAndroidTest
```

The suite covers report retry behavior, server payload rendering, store purchase gating,
and interpretation mode reachability at 200% font size. All payloads are synthetic.
Do not build a Google Play APK. Google Play policy is additionally checked by the
Googleplay unit test task and release AAB inspection.

Before distribution, manually complete: create natal chart → save → reopen → open
personal forecast → switch periods and interpretation modes → verify interpretation,
advice and dates → go offline and retry. Repeat with system font 200%, small screen,
and both themes. Inspect Google Play AAB for hidden purchase UI; standard APK must
retain the expected website entry points. Record device, version and result in the
release report. Component smoke tests do not replace this end-to-end acceptance.

On Windows checkouts with Cyrillic paths, use the direct JUnit runner if Gradle's
worker reports ClassNotFoundException despite successful test compilation:

```powershell
.\run-app-tests.ps1
```

It compiles both flavor unit-test sources, obtains each exact Gradle test classpath,
and runs JUnitCore directly. Logs are written to the repository's
`.codex-artifacts/qa-1.7.5` directory. No Google Play APK is built.

The real MainActivity journey is opt-in and requires the local API on port8000:

```powershell
.\gradlew.bat :app:connectedStandardDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=ru.astrosmap.app.ui.MainActivityForecastJourneyTest -Pandroid.testInstrumentationRunnerArguments.networkE2e=true
```

It enters the actual birth form/date dialog, searches the city asset, calculates,
saves, reads a real server forecast and queues rapid period selections. It checks
one persisted chart and cleans only its uniquely named synthetic QA record.

For offline acceptance, retain the synthetic record with `retainQa=true`, then run
`ForecastOfflineJourneyTest` with `qaName` equal to the exact `QA_CHART_NAME` marker
printed by the real journey. This test reads only that record, checks cached and
uncached periods without a network connection, font scale 200% and a small display.
It restores network, font and display settings in `finally`. Screenshots stay in
the debug application's private `files/qa-evidence` folder. Afterwards run
`RetainedQaCleanupTest` with `cleanupQa` equal to the same exact name.

`CityStoreAssetTest` verifies concurrent first readers and recovery from empty or
truncated city assets using isolated test cache folders, not the user's charts.

The assistant regression test keeps animations enabled, drags the original pet on a forecast screen and taps it to verify the contextual explanation. The pet must never be replaced by a help button.

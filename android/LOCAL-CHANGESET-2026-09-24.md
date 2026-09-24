# Состав локальных изменений AstroSMap — 24 сентября 2026

Список для проверки перед возможным коммитом; git add, commit и публикация не выполнялись. Посторонние untracked-файлы, магазинные материалы и архивы не включены.

## Изменённые отслеживаемые файлы

- `AGENTS.md`
- `android/EDITORIAL-CONTENT.md`
- `android/app/build.gradle.kts`
- `android/app/src/main/java/ru/astrosmap/app/AstroApp.kt`
- `android/app/src/main/java/ru/astrosmap/app/MainActivity.kt`
- `android/app/src/main/java/ru/astrosmap/app/data/DailyNotify.kt`
- `android/app/src/main/java/ru/astrosmap/app/data/api/AstroApi.kt`
- `android/app/src/main/java/ru/astrosmap/app/ui/account/AccountScreen.kt`
- `android/app/src/main/java/ru/astrosmap/app/ui/account/AccountViewModel.kt`
- `android/app/src/main/java/ru/astrosmap/app/ui/account/FeedbackButton.kt`
- `android/app/src/main/java/ru/astrosmap/app/ui/tarot/TarotDeck.kt`
- `android/app/src/main/java/ru/astrosmap/app/ui/tools/ForecastScreen.kt`
- `android/app/src/main/java/ru/astrosmap/app/ui/tools/LunarTexts.kt`
- `android/app/src/main/java/ru/astrosmap/app/widget/WidgetUpdateWorker.kt`
- `android/app/src/main/res/values-en/strings.xml`
- `android/app/src/main/res/values/strings.xml`
- `android/app/src/test/kotlin/ru/astrosmap/app/ui/AndroidEditorialTest.kt`
- `android/astrocore/src/main/kotlin/ru/astrosmap/app/astro/AstroEngine.kt`
- `backend/main.py`
- `frontend/index.html`
- `frontend/js/app.js`
- `scripts/build_public_source.py`

## Новые файлы реализации и проверок

- `android/app/src/main/java/ru/astrosmap/app/data/CrashDiagnostics.kt`
- `android/app/src/main/java/ru/astrosmap/app/data/CrashSummary.kt`
- `android/app/src/main/java/ru/astrosmap/app/data/PendingCrashStore.kt`
- `android/app/src/main/java/ru/astrosmap/app/data/api/MobileReportRequest.kt`
- `android/app/src/main/java/ru/astrosmap/app/editorial/RemoteEditorial.kt`
- `android/app/src/main/java/ru/astrosmap/app/ui/account/FeedbackViewModel.kt`
- `android/app/src/main/res/values-en/crash_diagnostics.xml`
- `android/app/src/main/res/values-en/forecast_details.xml`
- `android/app/src/main/res/values-en/reports.xml`
- `android/app/src/main/res/values/crash_diagnostics.xml`
- `android/app/src/main/res/values/forecast_details.xml`
- `android/app/src/main/res/values/reports.xml`
- `android/app/src/test/kotlin/ru/astrosmap/app/data/CrashSummaryTest.kt`
- `android/app/src/test/kotlin/ru/astrosmap/app/data/PendingCrashStoreTest.kt`
- `android/app/src/test/kotlin/ru/astrosmap/app/data/RegisterRequestTest.kt`
- `android/astrocore/src/test/kotlin/ru/astrosmap/app/astro/PackagedEphemerisTest.kt`
- `android/scripts/check-editorial-apk.ps1`
- `backend/mobile_editorial.py`
- `backend/mobile_reports.py`
- `scripts/qa_mobile_local.py`
- `tests/test_mobile_editorial.py`
- `tests/test_mobile_reports.py`

## Отчёты

- `android/API-EDITORIAL-MIGRATION-2026-09-24.md`
- `android/APK-AUDIT-2026-09-24.md`
- `android/BUGFIX-PROGRESS-2026-09-23.md`
- `android/CRASH-DIAGNOSTICS-2026-09-23.md`
- `android/MOBILE-REPORTS-2026-09-23.md`
- `android/LOCAL-VERIFICATION-2026-09-24.md`
- `android/LOCAL-CHANGESET-2026-09-24.md`

Дополнение подготовки 1.7.4 (12): android/app/build.gradle.kts (номер версии), TarotScreen.kt (ограничение внешней ссылки Google Play), frontend/mobile-privacy.html, frontend/privacy.html, frontend/index.html, frontend/js/i18n.js, frontend/js/standalone-i18n.js; документы RELEASE.md, GOOGLE-PLAY-CURRENT-DECISION.md, RELEASE-PREP-2026-09-24.md, docs/PUBLIC-SOURCE-EXPORT.md. Итог и SHA приведены в RELEASE-PREP-2026-09-24.md.

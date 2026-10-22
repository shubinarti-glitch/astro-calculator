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

# Проверенная локальная версия 1.7.6 (14) — 22 октября 2026

Код и тесты закоммичены: b67787fc18b9d3891786d1b8fe13c00b96cb19bc (78 файлов).
Чистый экспорт именно этого коммита проверен: 336 выбранных файлов, 352 известные
длинные приватные строки, 0 совпадений. Export ready_to_build/publish остаются false;
не смешивать source privacy pass с полнотой публичного исходного пакета.
QA emulator/API остановлены с сохранением данных, IME preference восстановлена.

Исправление edited-draft savedId завершено и проверено. Текущий APK ac534f9e…,
AAB3b2d34ad…; точные SHA256 и mapping в android/store/releases/1.7.6.
Build14m03s; test/lint build8m54s; fresh units81/81 каждого варианта; lint0errors.
Android16 AOT instrumentation:1/1 за38.483s, explicitSave И openForecast,
original row/cache unchanged before save, sameid/clientId, no duplicates.
Real debug account A edit→Save→PUT200: one original profile/id/nonce, B unchanged.
New signed APK edit→Save→force-stop/restart: QARelease176Edited persisted,
nativewheel16KiB rendered; original pet preserved. First debug instrumentation startup
ANR is recorded as failed attempt; no claim of physical-device performance.
Earlier Android15 signed13→14 upgrade, forecast/advice/comparison, diaryrestart,
light/font/offline/reminder/pet checks retained as evidence of those paths.
Current proof: .codex-artifacts/qa-1.7.6/final-release-candidate-packaging.json,
accepted-device-checks.json, acceptance-client-atomic-update.json.
Narrow own scope78paths in COMMIT-FILES.json; four unrelated diffs must remain untouched.
Source dryrun336files/352strings0hits; export NOT ready_to_build/ready_to_publish
(private runtime packages and completeness review remain). APK/AAB scan0hits too.
Production not changed. Specific1.7.6 push/deploy/publication approval is still required
by AGENTS.md; DB migration is performed manually by owner, private SERVER-PROFILE-SCHEMA.md.
No Play APK built. PhysicalFPS/battery/Doze/TalkBack not established; advice export deferred
pending rights. Below is historical log; old pending flags/hashes are superseded above.

# Актуальная точка восстановления — 22 октября 2026

Разработка активна. Релиз 1.7.6 (14) не опубликован; своего коммита пока нет.
Исправлена причина потери редактирования: preview route id=0 больше не считается
сохранённым только из-за существующего entity.id. Новая APK/AAB сборка успешна
(edit-fix-packages-build.log, 14m03s), подписи/версия/16KiB ZIP/privacy checks прошли.
Текущий APK ac534f9e…, AAB 3b2d34ad…; точные хеши в store/releases/1.7.6/SHA256SUMS.txt.
Регрессия SavedChartEditJourneyTest покрывает explicit Save и openForecast, неизменность
исходной записи/кэша до Save и сохранение id/clientId без дубликата. Первый compile
теста выявил лишний import assertDoesNotExist (метод класса, не extension); исправлен.
Повтор test APK/unit compile/lint выполняется: edit-fix-tests-lint-retry.log.
Нужно запустить fresh units, этот Android journey, фактический PUT с аккаунтом A,
проверить signed APK, обновить итоговые доказательства и сделать узкий коммит 78 путей.
Предыдущие signed проверки действительно подтвердили Android15 diary cold-restart,
Android16 native chart на16KiB и debug account A→B→A isolation; они не заменяют
проверку нового edit исправления. Эмуляторы сейчас остановлены на время Gradle.
Pixel9 остановлен с прямого разрешения пользователя, Android Studio не закрыта.
Данные и секреты не публиковались; четыре посторонних diff сохранены (hashcheck).
Production/push требуют конкретного разрешения на1.7.6; production DB schema —
ручное действие владельца, см. приватный SERVER-PROFILE-SCHEMA.md и AGENTS.md.

Ниже история предыдущих этапов; устаревшие pending/hash не являются текущим статусом.

# Точка восстановления разработки 1.7.6

**Актуальный статус: читать последний раздел «Обновление проверок — 22 октября». Более ранние разделы сохранены как история.**

21 октября 2026. Пользователь попросил паузу и затем написал «продолжаем».
Все исходники сохранены в рабочем дереве; коммит/публикация этого релиза ещё не выполнялись.

## Текущий статус после возобновления

Дополнительно реализованы исходные пункты аудита: очередь обезличенной аналитики
(200 событий / 64 КиБ / 7 дней; возможен дубль при потерянном HTTP-ответе),
сравнение реальных тем, источник совета в дневнике, отменяемое напоминание события.
Ошибки расчёта показывают повтор/закрытие. Все основные запросы карты и отчётов
прибиты к исходному токену; cancellation не превращается в offline. Язык включён
в scope истории прогноза. После freeze оба unit-прогона успешны: StandardDebug78/78 и GoogleplayDebug78/78
(21 класс). Общий debug/test APK и lint ещё завершаются.

Android15 startup ANR сохранён в android15-startup-anr.txt: main в ART ReadAtOffset/
ExtractToMemory/OpenDexFilesFromOat до кода приложения. Контрольный старый release
открылся за5611мс; новый debug после cmd package compile -m speed -f — за5343мс.
Это разные режимы сборки, не доказательство ускорения. Два vendor-пакета (Search,
Play Store) отключены ТОЛЬКО в AstroSMap_QA_176 для разгрузки. Production не менялся.
Полный сценарий дошёл до расчёта, затем тест выбрал одноимённую строку вместо чипа;
селектор исправлен на SemanticsProperties.Selected. Добавлен путь до сохранения
совета в дневнике и screenshot journal-advice.png; нужен повтор после нового APK.

Экспорт выбранного авторского совета не включён: исходный аудит обусловил его
подтверждением прав. Существующий экспорт колеса сохранён.

## Согласованный объём

План: IMPROVEMENTS-1.7.6.md; исходный аудит:
../.codex-artifacts/audit-2026-10-21/AUDIT-AND-PLAN.md.
Сохранить заставку, космическую палитру, исходные рисунки/анимации/drag питомца
и бесконечную подсказку нижнего меню. Авторские тексты — только приватный runtime.
Версия в Gradle: 1.7.6, code 14. Google Play только AAB; standard — APK.

## Реализовано, требует финальной приёмки

- Карты разделены по подтверждённому /me.id; Room v3→v4 сохраняет старые записи
  как гостевые, не загружает автоматически. Выборочный импорт и дедупликация.
- PUT профиля без delete/create, идемпотентный POST client_id, CAS-обновление
  чистых локальных карт; сохранение правок при сетевых сбоях и смене аккаунта.
- Смена сессии обновляет граф навигации, скрывает прежние отчёты, материалы и
  личный текст виджетов; результаты фоновых задач проверяются по токену/владельцу.
- Даты по городу прогноза, классификация ошибок, отзыв кэша при /me401/403.
  Гостевой preview сохранён (без токена /me не вызывается).
- Трактовка/совет перед раскрываемым основанием, локализованные даты, оглавление,
  возврат к началу и переход совета в дневник.
- Интерактивная планета: выделение связей, доступный список и трактовка runtime.
- Дневник: отдельные черновики по датам, снимок на момент save, защита устаревших
  чтений и токена; длинный текст не обрезается. Старые username-записи копируются
  только после явного подтверждения на устройстве, не автоматически.
- Уведомления предлагаются после просмотра карты при возврате на Сегодня;
  отказ не вызывает второй диалог. Питомцу добавлены подсказки/действия,
  его графика/движение/сон/drag сохранены. Пузырь ограничен экраном и прокручивается.
- Контраст зелёного адаптирован к теме. Backup исключает сессию/кэш/идентификатор;
  карты и настройки питомца сохраняются. Заставка не переделывалась.

## Доказательства

Логи: ../.codex-artifacts/qa-1.7.6/.
- Standard и Google Play unit: 78/78 каждый, включая последние session/reminder/analytics fixes.
  Логи StandardDebug-unit-junit.log и GoogleplayDebug-unit-junit.log.
- server-and-export-tests.log: 38 passed, 1 Windows skip (профили, экспорт, reports).
- chart-ownership-android15.log: 6/6 Room/миграция/аккаунты/гостевой импорт.
- components-ui-android15.log: 16/16 (Journal2, City2, ChartSelection2,
  ForecastContent6, ReportUiSmoke4). Прогон завершился перед остановкой эмулятора.
- debug-build-lint.log: successful, 0 errors / 116 warnings / 3 hints.
- access-fix-build.log: successful compile + APK/test APK после узкого исправления.
- acceptance-debug-build.log: финальный debug/test APK + lint успешны;
  включены chart_created и /me denial fix. Lint: 0 errors, 116 warnings, 3 hints.
- MainActivityForecastJourneyTest изменён для локализованных дат и снимка карты;
  нужно выполнить полный реальный сценарий с локальным API.

## Незакрыто и следующие действия

1. Финальный debug/test APK, оба unit-прогона и lint завершены успешно.
2. Запустить полный сценарий create/save/chart/forecast/diary с изолированным API;
   получить актуальные снимки трёх экранов, проверить обе темы/крупный шрифт/offline.
3. Android16 ps16k QA дал ANR startup state=empty (без Java stack AstroSMap).
   На хосте было около700MB свободно. После отдельного Android15 запуска тесты
   прошли. Android16 не считать проверенным: сравнить старый release APK и новый
   в одинаковых условиях, либо получить trace. Не объявлять среду единственной причиной.
4. Подготовить signed standard APK + Google Play AAB, подписи/версии/hash,
   source/private prose scan, финальный lint. Измерения реального слабого телефона
   пока недоступны; эмулятор не заменяет батарею/FPS физического устройства.
5. Сверить чеклист, сохранить отдельный коммит только своей работы. Не включать
   чужие backend/astrology.py, tests/test_api.py, test_predictive_interpretations.py,
   test_transit_english.py: они были изменены ДО этой задачи.
6. Показать экраны/релиз пользователю. Новый production/push не разрешён этим
   этапом. До распространения APK нужен совместимый сервер (/me.id, PUT, client_id).
   Изменение production-схемы БД подчиняется правилам AGENTS.md; без согласования не выполнять.

## Изолированные среды

- API: .codex-artifacts/qa-1.7.6/local_api.py, bind127.0.0.1:8000,
  собственная isolated-data/app.db, не data/app.db. На паузе остановлен.
  Сначала проверить порт: не останавливать чужой процесс.
- Старый QA Android16: ANDROID_AVD_HOME=
  C:/Users/archi/AppData/Local/Temp/astrosmap-release-175-6yo0jimi,
  avd AstroSMap_QA_175. Системный образ android36/google_apis_playstore_ps16k.
- Новый QA Android15: ANDROID_AVD_HOME=
  C:/Users/archi/AppData/Local/Temp/astrosmap-qa176-android15,
  avd AstroSMap_QA_176, port5556,720x1280,density320,memory1536.
  Создан только из конфигурации и установленного образа, пользовательские images
  не копировались. На паузе остановлен; данные синтетические.
- Проверять `adb -s emulator-5556 emu avd name` перед действиями. Не трогать
  личные эмуляторы/БД. Gradle и эмулятор запускать последовательно из-за памяти.
- JDK: C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot.
- Субагенты data_safety_v176, forecast_reliability_v176, interactive_chart_v176
  завершили код и review; они доступны для адресных followup, не запускают сборки.

Дополнительные предложения аудита (экспорт карточки совета/напоминание о выбранном
событии/устойчивая очередь аналитики) ещё не реализованы. Не выдавать их за готовые
и не подменять ими незакрытую приёмку основного пути.

## Последняя приёмка

- Signed build successful11m37: standardAPKsha1110b93b676a0755a680b49e9d662f28d084ee278a3c80d3429ec66354655b8a; AABsha698d25da51e3e72699bca5ecd431fd3624079d58359e46b8b7c067ecd3eb04fd.
- Candidatefolder android/store/releases/1.7.6 создан (пакеты/mapping/хэши/черновикnotes), не опубликован.
- APK apksigner valid, samecert1.7.5; zipalign16Kvalid. AABbundletoolvalidate/package/code/versionvalid; known352stringsscan nohits. JarFile signaturesvalid; selfsigned/manifestorderwarnings есть ивстаром1.7.5AABcontrol.
- Last main change: EventReminderButton marks DailyNotify promptshown before explicitpermissionrequest; refusal cannot trigger later automaticoffer. Final-input-build.log successful4m40, signedpackagesUPTODATE, debugAPKrebuilt, lint0errors120warnings3hints.
- HOST GPU NVIDIA on AstroSMap_QA_176: mainUIworkswithoutSystemUIANR. Star/darkpalette preserved. MainActivityfulltest reachesforecast then timeoutatjournalbutton; manualrealUI Cards→exactQAchart→Viewforecast→Keydays→Saveadvice→Addtonote→Saveentry PASSED; SavedandOct1historyrowvisible. So integrationworks. Test now adds performScrollTo/assertDisplayed beforebuttonclick plus failurecapture; needs testAPKrecompile.
- ForecastOfflineJourneyTest changed agent: normalUI Cards→exactsyntheticname→chart→forecast, no forbiddennotificationdeepLink. Needs testAPKrecompile.
- CurrentlatestretainedQAname QA-forecast-cec08539-9972-4a40-97ce-759b6d5ddda4. Screens private artifacts: chart-interactive.png(actualdarknormal), release-journal-advice.png, release-journal-saved.png. diagnostic-systemui-anr.png rejectedoldsoftwareGPUcapture.
- Next: real signedAPK1.7.5→1.7.6 QAupgrade scenario; finish automation/full22componentregression/offline/light200font/RU; Android16releasecontrol; docs+owncodecommit. No production/push.

- Manual signed1.7.5(code13) actualsyntheticcard QAUpgrade176 createdandpersisted, visibleinCards; screenshot release-upgrade-before.png. НЕобновлятьстарымиAPKs/неделатьwipe: этоfixtureдляreal13→14upgrade. QAAVDостановлендляIMEfixbuild.
- Newactualbug: oldform keyboardcoverscitysuggestions/Calculate (release-old-form-keyboard.png). ChartFormmainunchangedin1.7.6, needsIMEfix beforefinalrelease. Interactiveagentimplementingnarrowinsets/bringIntoView/hidekeyboard, realkeyboardtest; candidatefoldercurrentlyпредыдущийкандидат, хэши изменятсяпослеэтогоfix!
# Пауза по просьбе пользователя — 21 октября, 18:56

Этот раздел актуальнее старых записей ниже. Продолжить только после команды пользователя.

- Все изменения сохранены на диске; коммитов, push, production и публикации 1.7.6 нет.
- Питомец, исходная заставка, палитра и повторяющаяся анимация меню сохранены.
- Финальный IME fix готов: consumeWindowInsets в корне; imePadding и bringIntoView в форме/городе прогноза/дневнике; закрытие клавиатуры после выбора; latest-query guard в форме. Новый ImeEditorJourneyTest содержит 3 проверки настоящей клавиатуры, включая физическое нажатие. Ещё НЕ запускались.
- Исправлена гонка delete во время POST: owned tombstone до закрытия экрана; app-scope requestSync; сохранение serverId при позднем ack; серверная отмена по client_id с защитой от позднего POST. Повторный импорт после удаления получает новый nonce, повторные нажатия не создают копии. ChartOwnershipTest теперь 8 тестов; последние 2 ещё НЕ запускались на Android.
- Новый DELETE /api/profiles/by-client ограничен 120 запросами/мин на uid. Production prerequisites: .codex-artifacts/qa-1.7.6/SERVER-PROFILE-SCHEMA.md. Кроме profiles.client_id/index нужна profile_deletions. В production схему выполняет владелец вручную, агент не обходит это запуском init_db.
- Фактический финальный backend/export прогон: frozen-server-and-export-tests.log — 44 passed, 1 Windows symlink skip, 1 deprecation warning. Новые profile safety tests: 12/12.
- Android unit: 78/78 у обоих вариантов в final-ime-unit-run.log, ДО последнего import fix. Финальные классы скомпилированы, но прямой JUnit после заморозки ещё НЕ запускался. Classpath-файлы прежние подходят: зависимости не менялись.
- Первый final-ime-build остановился lint-ошибкой LocalContextConfigurationRead в EventReminderButton. Исправлено через LocalConfiguration.current.locales[0], подавлений нет. Затем PowerShell расколол незакавыченный -Pkotlin.compiler.execution.strategy; исправлено кавычками.
- Последняя сборка frozen-final-build.log была остановлена по просьбе пользователя на lintStandardDebug. Compile обоих unit-вариантов, standard debug APK и test APK успели пройти. Полный signed build НЕ подтверждён. Пакеты в android/store/releases/1.7.6 пока УСТАРЕЛИ, README помечен: не распространять.
- При возобновлении: JDK21; Gradle --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8'. Это избегает параллельных Kotlin/Gradle heap по 3GB. Не запускать AVD вместе со сборкой. Команда: compileStandardDebugUnitTestKotlin, compileGoogleplayDebugUnitTestKotlin, assembleStandardDebug, assembleStandardDebugAndroidTest, lintStandardDebug, assembleStandardRelease, bundleGoogleplayRelease, lintGoogleplayRelease. GP APK запрещён, только AAB.
- После сборки запустить прямой JUnit для обоих вариантов (run-app-tests.ps1 или существующие classpath-файлы), затем остановить свои Gradle-процессы и запустить собственный Android15 AVD с -gpu host. Выполнить IME3, компоненты24 (Room8 + прежние16), full MainActivity journey, offline/light/200%, реальное обновление signed1.7.5→1.7.6. При повторном journal timeout получить journey-last-state.png и различить загрузку/ошибку/дневник без draft/оставшийся forecast; не менять main вслепую.
- Сохранён fixture подписанной 1.7.5: QAUpgrade176 в ru.astrosmap.app на AstroSMap_QA_176. Не wipe/downgrade! Точная debug-карта для offline: QA-forecast-cec08539-9972-4a40-97ce-759b6d5ddda4 (новый успешный full-test создаст другой UUID).
- AVD на паузе не запущен. Изолированный API (launcher37700/server56052) и наш Gradle wrapper48052/daemon58116 остановлены по проверенной command line. Следующий запуск сначала проверяет порт8000 и принадлежность AVD; не трогать чужие процессы/базы.
- Source scan: 335 выбранных исходников, 352 известных длинных приватных строк, совпадений нет. frozen-source-export.json создан ДО последнего backend rate-limit изменения: обновить манифест перед окончательным отчётом. ready_to_build/publish=false из-за исключённых приватных runtime/assets — не утверждать полноту публичной сборки. Финальные APK/AAB ещё пересканировать/проверить подпись, bundletool, zipalign, mappings и хэши.
- IMPROVEMENTS-1.7.6.md содержит актуальную матрицу: реализовано / предыдущие доказательства / ожидание финальной приёмки / физическое устройство. Обновить после реальных запусков. Sharing авторского совета условно отложен до проверки прав; физические FPS/батарея/Doze/TalkBack/Vitals не подтверждены.
- После приёмки: обновить release folder и документацию, закоммитить только свои файлы; 4 чужих WIP оставить нетронутыми: backend/astrology.py, tests/test_api.py, tests/test_predictive_interpretations.py, tests/test_transit_english.py. Не git add . или весь android: много чужих untracked артефактов.
- Конкретный новый production/push/release1.7.6 не согласован: сначала показать проверенный кандидат, затем согласовать по AGENTS.md. Публикация магазинов остаётся у владельца.


## Возобновление после паузы — 21 октября, 20:15

- resumed-debug-build.log успешен; StandardDebug-resumed-unit-junit.log и GoogleplayDebug-resumed-unit-junit.log по78/78 уже после import/delete fix.
- API изолирован: launcher54684/server26496, resumed-api-*.log. Перед остановкой проверить command line local_api.py и PID/порт.
- Android15 hostGPU boot оставил системный ANR-modal System UI. Первый resumed-ime-android15.log 3fail из-за блокирующего системного окна, не appproof. Скрин ime-diagnostic.png. Modal закрыт через nativehelper Close app, нормальныйUI подтверждён.
- Затем resumed-ime-clean-android15.log: 3/3 PASSED с реальной клавиатурой. resumed-components-android15.log: 24/24 PASSED (Room8, City2, Chart2, Forecast6, Report4, Journal2). Питомец drag/hint и крупный шрифт покрыты компонентами.
- Full resumed-full-journey-android15.log FAIL настроке90 (нет Readyforecast), screenshotfull-current.png показал Offline. Новая syntheticкарта QA-forecast-95a82f21-db4d-4d1b-8a46-53b26613e232 сохранена. Не является финальным E2Epass.
- Причина доказана hostprobe: реальный POST /api/forecast для Jan1 1990/Moscow, October1–31 2026 вернул200/14229bytes за14.2s. OkHttp был без timeoutнастроек (defaultread10s), /me одновременно401за506ms — сервер жив, не пропажа сети.
- Узкий NetModule fix: readTimeout60s, callTimeout90s, connecttimeout default10s сохранён. Никаких UI/editorialизменений. По состоянию этой записи timeout-fix-debug-build.log ещё выполняется, нужен повторный unit/fullE2E/offline и финальныйsignedbuild послеfix. Не выдавать старые APK/AABкакновые.
- AVD176 остановлен командой emu kill перед этой сборкой; сохраняет signed1.7.5 QAUpgrade176 fixture. Не wipe/downgrade. Запуск hostGPU, дождатьсяboot + проверитьотсутствиеSystemUI modal передтестами. settings show_ime_with_hard_keyboard=1 ужеесть.
- Source335/352scan0hits refreshed resumed-source-export.json/resumed-public-source-prose-scan.json до2линийtimeoutfix; ready_to_build/publishfalse. Передfinalобновитьманифестсновойsourcehash; смысловойprivacyновыхстроктолькоUI/техкоммент.
- Никаких commits/push/deploy/publish. Все 4 чужих WIP сохранены.

## Актуально — 21 октября, 21:01, работа продолжается

- timeout-fix-debug-build.log BUILD SUCCESSFUL23m1; StandardDebug-timeout-fix-unit-junit.log и GoogleplayDebug-timeout-fix-unit-junit.log78/78каждый (финальныйNetModule read60/call90). Lint0errors120warnings3hints.
- Во время fullretest emulator снова испытывал system/hostmemorypressure (644MBfree, kernel72%CPU; NoComposeHierarchy ещёдоUI). Это не принято за appbug. QA176 ONLY temporarily pm disable-user com.google.android.gms, com.google.android.apps.messaging, com.google.android.apps.safetyhub. Список qa-memory-control.txt; после функциональныхпроверок ВОССТАНОВИТЬ pm enable эти3пакета. Старые search/vendingdisabledизпредыдущейQAнастройкинепереопределять. Не заявлятьтестGoogleServices/billing.
- Затем чистый full actualAPI pipeline PASSED: timeout-fix-clean-full-journey-android15.log OK1test130.504s. Cardcreated/saved, latestperiodwins, forecastinterpretation/advice, diaryadd+save. NewretainedQAname QA-forecast-a123e80c-0556-479e-8869-76a7c29bb0f5.
- Пять реальныхскриншотов вытянутыdebugfiles/qa-evidence вprivatefinal-*.png (chart-interactive,forecast-overview,forecast-advice,journal-advice,journey-last-state). Forecastscreensrootпросмотрены: разные содержательныесерверныетрактовки/советы, космическаяпалитра/питомецсохранены. ПриватныйтекстнеэкспортироватьвGit.
- OfflineIT final-offline-android15.log FAIL: selectorname matchedsearchinput+card. TEST-ONLYfixed ForecastOfflineJourneyTest: hasText(name) and !hasSetTextAction, cardmatcherwait, searchperformTextReplacement; Readyheaderprefix tools_forecast+" · " guardbeforeLazyColumnscroll; smallscreenalsoassertfontScale>=1.9. НетновыхизмененийmainпослеNetModulefix.
- Текущая сборка final-signed-and-offline-test-build.log ещё выполняется (session12260): assembleStandardDebugAndroidTest +assembleStandardRelease +bundleGoogleplayRelease +lintGoogleplayRelease, maxworkers1/inprocess/Xmx2048. В21:00:minifyStandardReleaseWithR8. AVD176остановлен, API54684/26496ещёработает. Некоторыеtest-onlyправкиуспелисделатьПОСЛЕпервойassembleAndroidTesttask: послеsignedобязателен ЕЩЁassembleStandardDebugAndroidTest+проверкалинтаеслинужно, затемсвойGradle--stopпередAVD.
- Next: finishsignedbuild; refreshAndroidTestAPK; launchsameQA176hostGPU, bootcheck+closeobservedSystemUI modal ifany; installtestAPK(-r), mainAPKalreadycurrent; runOfflineIT#cachedForecastRecoversAfterUncachedPeriod qaNameexactabove. Then configuredwindowtestswithwifidataOFF: lightnightno/font1; smallnightyes/font2/wmsize840x1440, restorefont1/wmsizereset/nightyes/wifi+dataafter. Captureprivateforecast-offline/light/smallfiles. Не увеличиватьтаймауты/неослаблятьassertions.
- Need actualsignedupdate oldru.astrosmap.app1.7.5/code13withQAUpgrade176 fixture→new1.7.6/code14 no wipe, confirmcardopen; signedRU/diary/reminderUI smoke; Android16controlifpossible. Standarddebugpackageотдельный, не подменяетsignedupgrade.
- Verifyfinalpackagesapksigner/v2samecertde3cc0773bf247dbacbbd4511d516c1c890d034789cd6c7988b6335ba1f47a70,zipalign-P16,bundletoolvalidate+manifest,jarsigner(normalknownoldwarnings),352knownprivatestringscan. Refreshreleasefolder/APK+AAB+mappings+SHA256SUMS+README/CHANGES. CurrentcandidatefolderstillSTALE/doNOTdistribute.
- dataagentprepared .codex-artifacts/qa-1.7.6/COMMIT-FILES.json76pathallowlist +snapshothashes:41trackedAndroid+2backend+30newAndroidsrc/test/xml+2docs+newbackendtest. Nothingstaged/committed. Recomputehashesafterfinaldocupdates. preserved-wip-hashes.json records4unrelateddiffs; doNOTstage/alter them.
- Local .git/info/exclude now excludes /.codex-artifacts/ and /android/.codex-artifacts/ to preventaccidentalprivatecapturesgitadd. Sourceexportmanifestresumed335selected/352known0hits predatesNetModule2lines/testselectorpatches; refreshbeforefinal. No publicready/buildcompletenessclaim.
- DocsIMPROVEMENTSmatrixalreadyupdatedbyforecastagent uptoNetModulebuildpending, needupdatefinalactualE2Eandremainingevidence. No commit/push/deploy/publish1.7.6 yet. DeploymentrequiresconcretereleaseauthorizationandownermanualproductionDBmigration asAGENTS.
- Latest update 21:36: signed build final-signed-and-offline-test-build.log SUCCESS25m10, BUT packages obsolete after completing cross-period comparison. Verified old APK64ef90f3d5a5d472e3ffd23d78fb1c93605de6d29cd8e48e17fed4d1c0d7a4ca; final-apk-* evidence is tied to that hash, not future builds.
- Comparison now implemented via separate ForecastHistory.comparisonBaseline (same scope/chart, compatible day/week/month family, different-window priority; same-window fallback). Strict cache matching unchanged. Validates date-window shape/enum/payload; shows localized baseline range in existing card. Agent changed History/Screen,2resource files,ForecastReliabilityTest(+3unit=81total),ForecastContentUiTest(+1UI=25components).
- Root MainActivityForecastJourneyTest additionally loads THIS_MONTH then NEXT_MONTH, asserts localized baseline and captures forecast-comparison.png before original rapid chips/advice/journal scenario. Root changed validation to reject present numeric start/end fields and added numeric payload corruption case. ALL main/tests frozen now.
- First comparison-debug-build failed unresolved existing s/a extensions (definitions still in RemoteReport). Retry comparison-full-debug-build.log session46863 uses full nonincremental Kotlin/KSP, separate KotlinDaemon1536+Gradle1536,maxworkers1. Standard main/unit compilation passed; GPdebug compilation in progress. Flags: -Pkotlin.daemon.jvmargs=-Xmx1536m, -Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8, -Pkotlin.incremental=false, -Pksp.incremental=false (quote full flags in PowerShell).
- IMPORTANT root validation/unit numeric patch occurred AFTER Standard compilation in that run. After run finishes rerun compileStandardDebugUnitTestKotlin,compileGoogleplayDebugUnitTestKotlin,assembleStandardDebug,assembleStandardDebugAndroidTest with same flags to refresh final inputs, then directJUnit81each using existing classpaths/discovery. Avoid new source edits.
- Own Gradle8.13 PID36944(parent22072), KotlinCompileDaemon50696(parent36944) at21:26; verify before stopping to free memory for AVD. Foreign Gradle9.5 PID50264 must remain untouched. API54684/26496 isolated still running; AVD176 stopped.
- Need updated ForecastContentUiTest7, full MainIT1/newUUID, offline recovery and light/font200 width840 tests (offline has not passed yet), then signed rebuild/privacy/signature/hash refresh, real signed1.7.5 QAUpgrade176 upgrade without wipe, RU/reminder creation+cancel smoke, Android16 if possible, documentation + narrow76filecommit. Restore QA176 gms/messaging/safetyhub disabled packages after tests. No production/push/publication authorization for1.7.6.


## Обновление проверок — 22 октября

- Main/test исходники заморожены; эта запись меняет только статус.
- `final-comparison-full-journey-android15.log`: **OK, 1 тест, 178.886 с**,
  включая фактический переход THIS_MONTH → NEXT_MONTH и проверку базового периода.
- Актуальные units: **81/81 каждого варианта**; forecast content: **7/7**.
- **Визуальная приёмка нового сравнения НЕ засчитана:** root просмотрел приватный
  `comparison-forecast-comparison.png`, на снимке системное окно System UI ANR.
  Успешный функциональный тест не заменяет чистый снимок конечного экрана.
- `comparison-offline-android15.log`: ошибка ожидания cached prefix, строка 111.
  Снимок показывает System UI ANR на домашнем экране; это не доказательство
  корректной работы offline и не установленный баг кэша приложения.
  Наблюдаемое системное окно закрыто; чистый повтор
  `comparison-offline-clean-android15.log` **сейчас выполняется**.
- Центр продолжает offline/configuration и финальные подписанные пакеты.
  Новые signature/hash/privacy, installed release/upgrade, reminder, Android 16
  и commit остаются ожидающими соответствующих подтверждений.
- Дополнительно активен **чужой Android Studio Pixel 9, emulator-5554**.
  Не останавливать пользовательские процессы/эмуляторы и не считать его нашим QA.
- Ранее временно отключённые пакеты синтетического QA176 восстановить после QA;
  не заявлять проверку Google Services/billing или физического устройства.


### Актуализация финальной приёмки — 22 октября

- `acceptance-offline-ready-ime-android15.log`: **PASS, 1 тест, 26.245 с**.
  Перед вводом тест ждёт фактический IME visible, сохраняет проверку hidden
  на протяжении 5 с; перед cached banner прокручивает к началу.
- Уже внесённое скрытие клавиатуры/снятие фокуса SavedScreen.onOpen подтверждено
  физическим native-действием. Main/test остаются замороженными.
- Light и font200 выполняются: `acceptance-light`, `acceptance-font200`.
- Пользователь явно разрешил остановить Pixel 9: emulator-5554 остановлен,
  данные сохранены, Android Studio открыт. Наш QA176 загрузился без ANR.
- Финальная сборка пакетов успешна за **21:52**; последняя language-сборка за
  **11:50**. Lint: standard **0E/119W/3 hints**, GP **0E/121W/3 hints**.
- `bundle.language.enableSplit=false` устранил language warning;
  метаданные AAB: `LANGUAGE negate=true`.
- Standard APK **14 913 074 байта**, hash prefix **2b17669e…**;
  GP AAB **17 840 524 байта**, prefix **0c121b…**. Подписи и scan по 352 известным
  приватным строкам (0 совпадений) проверены агентом, пакеты подготовлены.
  Полные hash брать из release manifest, не из этих сокращённых ссылок.
- Ещё требуются фактические light/font результаты, installed release/upgrade,
  reminder, Android 16, source-check после финальных docs и commit.
  Production/push/commit пока не выполнялись.


### Чистая приёмка и подписанный upgrade — 22 октября

- Light **1/1, 21.067 с**; font200 **1/1, 23.351 с**. Default network none.
- `acceptance-clean-full-journey-android15.log`: **1/1, 52.619 с** с настоящим
  `runRealNetworkE2e=true`. Старый wrong-flag **4.74 с был skipped**, архив
  `acceptance-skipped-full-journey-wrong-flag.log`, не считать его успешным E2E.
- Новая retained QA-карта suffix9af315f8. Root просмотрел clean private captures
  forecast/offline/light/small/comparison/advice/journal/chart без системного окна.
- Подписанный install-r **code13→14** сохранил QAUpgrade176 и birth/card data;
  evidence acceptance-upgrade-from/to.txt и release-upgrade-after.png.
- RU через Account, реальный production guest прогноз октябрь→ноябрь, baseline
  dates/changed interpretation/advice и TOC Key Days проверены вручную.
- Напоминание: 1 ноября/Moscow/TZ/time → confirm → Android Allow → Saved/cancel →
  cancel вернул Add. Private release-reminder-dialog/scheduled/cancelled сохранены.
- Signed pet action открыл дневник. Advice01Nov → добавить → сохранить → Сохранено
  (release-diary-saved). **Cold restart persistence сейчас проверяется; Android16 далее.**
- Будущее фоновое срабатывание, Doze, физическое устройство, два реальных аккаунта
  end-to-end не подтверждены; unit/API и synthetic manual не подменяют эти проверки.
- После final docs нужны актуальные source/privacy checks и commit.
  Production/push/commit пока не выполнялись.

# Release blocker discovered during actual account acceptanceEditing QASync176A to QASync176AEdited and calculating left no Save action; local/server
profile stayed unchanged and no PUT was sent. Cause: draft route id=0 retained entity.id
and was wrongly treated as persisted. ChartViewViewModel now distinguishes route ID
from draft row ID. Regression test, rebuild and actual same-profile PUT acceptance pending.
Packages in store/releases/1.7.6 are BLOCKED until replaced and reverified.

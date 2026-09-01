# Передача дел: Android-приложение AstroSMap (APK)

Рабочая справка по сборке и выпуску Android-версии. Базовая редакция от 01.08.2026;
актуальный зафиксированный релиз — **1.7.2**. Общая сводка находится в
`../СОСТОЯНИЕ-ПРОЕКТА-2026-09-01.md`.
Дополняет `RELEASE.md` (в нём устарели размеры и «AAB для магазина» — см. ниже).

---

## 1. Что это

Нативное Android-приложение (`android/` внутри репозитория сайта), пакет **`ru.astrosmap.app`**.
Считает натальную карту и транзиты **офлайн** на устройстве (порт Swiss Ephemeris),
трактовки и премиум-техники тянет с API сайта. Название в лаунчере — **AstroSMap**,
подзаголовок — «Project Artemisa».

**Стек:** Kotlin 2.2.0 + Jetpack Compose (BOM 2025.07.00) + Hilt 2.57.1 + AGP 8.11.1,
Gradle 8.13 (wrapper), Retrofit, Room, WorkManager. minSdk 26, compileSdk/targetSdk 36.
Модули: `:app`, `:astrocore` (чистый Kotlin-движок, порт логики Kerykeion),
`:swisseph` (Java-порт Swiss Ephemeris 2.01).

---

## 2. Сборка

Java/Gradle НЕ в PATH. Перед каждой сборкой в PowerShell:

```powershell
$env:JAVA_HOME = "$env:ProgramFiles\Android\Android Studio\jbr"
```

Все команды — из папки `android/`.

**Два флейвора** (`store`-измерение), с версии 1.5.0:
- **`standard`** — RuStore / AppGallery / сайт. `SHOW_BILLING=true` (кнопки покупки премиума на сайте видны).
- **`googleplay`** — Google Play. `SHOW_BILLING=false` (весь увод на оплату скрыт — см. `GOOGLE-PLAY.md`).

```powershell
.\gradlew.bat :app:assembleStandardDebug      # отладочный APK (эмулятор/тесты)
.\gradlew.bat :app:assembleStandardRelease    # боевой APK для RuStore/AppGallery/сайта
.\gradlew.bat :app:bundleGoogleplayRelease    # AAB для Google Play (только там нужен AAB)
```

Артефакты релиза:
- `app/build/outputs/apk/standard/release/app-standard-release.apk` (~9 МБ) — RuStore/AppGallery/сайт
- `app/build/outputs/bundle/googleplayRelease/app-googleplay-release.aab` (~11.6 МБ) — Google Play

> ⚠️ Для RuStore/AppGallery/сайта — **APK** (флейвор `standard`), AAB им не нужен.
> **AAB нужен только Google Play** — и только из флейвора `googleplay` (без монетизации).

**Путь репозитория с кириллицей** (`…\Рабочий стол\…`) ломает часть тулинга.
Спасает `android.overridePathCheck=true` в `gradle.properties` — не удалять.

---

## 3. Подпись (боевой ключ)

- Ключ: `android/astrosmap-release.jks` (лежит ВНУТРИ `android/`), создан владельцем.
  **В `.gitignore`. Потеря ключа = невозможность обновлять приложение в магазинах.** Держать бэкап.
- Пароли и алиас — в `android/signing.properties` (тоже в `.gitignore`):
  `storeFile=astrosmap-release.jks`, `storePassword`, `keyAlias=astrosmap`, `keyPassword`.
- Если `signing.properties` нет — release подпишется debug-ключом (в магазин не примут).
- **Проверить подпись APK:**
  ```powershell
  & "<SDK>\build-tools\<ver>\apksigner.bat" verify --print-certs app-release.apk
  ```
  Боевой сертификат — SHA-256 `de3cc07…` (CN=?? — поля DN пустые, это норма для self-signed).

---

## 4. Выпуск новой версии — по шагам

1. Поднять в `app/build.gradle.kts`: `versionCode +1` (сейчас **6**),
   `versionName` (сейчас **1.5.0**). versionCode для магазина должен быть уникальным и растущим.
2. Обновить «ЧТО НОВОГО» в `android/store/description.txt`.
3. Собрать release APK (см. §2), проверить подпись и версию:
   ```powershell
   & "<SDK>\build-tools\<ver>\aapt2.exe" dump badging app-release.apk | Select-String versionCode,versionName
   ```
4. Прогнать на эмуляторе (см. §5).
5. Залить на сайт (см. §6) и/или в магазины вручную.

---

## 5. Эмулятор и скриншоты

- AVD: **Pixel_7**. Старт: `emulator -avd Pixel_7 -no-snapshot-save`.
- Установка: `adb install -r app-release.apk` (или debug).
- Сброс данных для перепроверки лимитов/онбординга: `adb shell pm clear ru.astrosmap.app`.
- Язык интерфейса: `adb shell cmd locale set-app-locales ru.astrosmap.app --user current --locales ru-RU`.
- **Скриншоты только так:** `adb shell screencap -p /sdcard/s.png` → `adb pull /sdcard/s.png …`.
  ⚠️ **Нельзя** `adb ... > file.png` в PowerShell — редирект добавляет BOM и портит PNG.

---

## 6. Заливка APK на сайт (прод)

APK раздаётся статикой с сервера, публичный адрес — **https://astrosmap.ru/astrosmap.apk**.
Файл на сервере: `/opt/astro/frontend/astrosmap.apk`. Рестарт НЕ нужен (статика).

```bash
# из корня репозитория, ключ ~/.ssh/astro_key
APK=android/app/build/outputs/apk/standard/release/app-standard-release.apk
ssh -i ~/.ssh/astro_key root@77.222.42.168 "cp -f /opt/astro/frontend/astrosmap.apk /opt/astro/frontend/astrosmap.apk.bak"
scp -i ~/.ssh/astro_key -O "$APK" root@77.222.42.168:/opt/astro/frontend/astrosmap.apk
```

**Обязательно сверить sha256** (локальный ↔ то, что реально отдаёт HTTPS — мимо кэша):
```bash
sha256sum "$APK"
curl -s https://astrosmap.ru/astrosmap.apk | sha256sum
curl -sI https://astrosmap.ru/astrosmap.apk | grep -iE "Content-Length|Last-Modified"
```
Откат: на сервере лежит `astrosmap.apk.bak` (предыдущая версия).

> Деплой формально — «зона человека» (см. CLAUDE.md). Заливку APK делаю по явной просьбе;
> git-push и БД на проде не трогаю.

---

## 7. Магазины (публикация — вручную)

- **RuStore:** https://console.rustore.ru — принимает APK. Категория «Образ жизни». Реквизиты — самозанятый Шубин А.И.
- **AppGallery (Huawei):** карточка `C118378621`. Тоже APK. Домены Huawei бывают недоступны из рабочей сети.
- **Возрастной рейтинг — 18+** (единое значение везде: карточки, описание, соглашение, политика). Закрыто.
- Материковый Китай из региона распространения AppGallery убран. Закрыто.
- Google Play — низкий приоритет (выплаты РФ приостановлены, барьер 12 тестеров×14 дней, риск по политике оплат мимо стора).
- Ссылки на карточки и правовое — в `android/store/description.txt`.

---

## 8. Грабли (проверено кровью)

- **Зависание на заставке из-за шрифта.** Вшитый Cormorant TTF (инстансы из variable-font)
  Android не рендерил → текст с нулевой высотой → кадровый цикл вставал → вечная заставка.
  Лечение: `val Cormorant = FontFamily.Serif` (`ui/theme/Brand.kt`). **Свой TTF не вшивать.**
  Симптом: логотип виден, соседний текст отсутствует, элементы схлопнуты в центр.
- **Заставка:** системный splash перекрывает Compose до первого кадра. Хендофф через
  `androidx.core:core-splashscreen` (`installSplashScreen()` + `setOnExitAnimationListener`),
  иначе на медленном эмуляторе своя анимация проходит под системным сплэшем незаметно.
- **Тесты движка** гонять ТОЛЬКО через `android/run-tests.ps1` (кириллица в пути роняет
  тестовый воркер Gradle с ClassNotFoundException; `gradlew :astrocore:test` не работает).
- **BASE_URL:** debug → `10.0.2.2:8000` (локальный FastAPI с эмулятора), release → `astrosmap.ru`.
- **Зависимость от бэкенда:** приложение дёргает `/api/natal?svg=0`, `/api/transit?svg=0` —
  правка `svg=0` на проде уже есть. Без неё работает, но тянет лишний SVG.
- В названии Compose-параметров нельзя использовать имя `ru` — перекрывает пакет `ru.astrosmap`.

---

## 9. Таро (для контекста)

- 78 карт RWS 1909 (public domain) → `app/src/main/assets/tarot/*.webp`.
- Тексты трактовок — хардкод в `ui/tarot/TarotDeck.kt` (RU+EN, значение + совет).
- Расклады: «на ситуацию», «мысли·чувства·действия», «да/нет». Лимит: бесплатно 1/нед на расклад,
  премиум — ежедневно (`TarotStorage`).
- **«Да/нет»** — детерминированный: две карты из ПОЛНОЙ колоды, выигрывает старшая по
  `TarotDeck.rank()` (старший аркан = 1000+номер; младший = достоинство×10+масть). Ранги уникальны → ничьей нет.

Подробный статус этапов и истории правок — в памяти `android-app` и `android-branding-splash`.

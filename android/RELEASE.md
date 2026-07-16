# Выпуск приложения AstroSMap (Android)

Все команды выполняются из папки `android/`. Java берётся из Android Studio:
в PowerShell перед сборкой — `$env:JAVA_HOME = "$env:ProgramFiles\Android\Android Studio\jbr"`.

## 1. Боевой ключ подписи (делается ОДИН раз, вручную)

```powershell
& "$env:ProgramFiles\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v `
  -keystore astrosmap-release.jks -alias astrosmap -keyalg RSA -keysize 2048 -validity 10000
```

- Файл `astrosmap-release.jks` хранить ВНЕ репозитория (и сделать резервную копию:
  потеряете ключ — не сможете обновлять приложение).
- Рядом с `android/` создать `android/signing.properties` (в .gitignore уже добавлен):

```properties
storeFile=../astrosmap-release.jks
storePassword=<пароль хранилища>
keyAlias=astrosmap
keyPassword=<пароль ключа>
```

Пока `signing.properties` нет, release подписывается debug-ключом — такой APK
годится только для локальных проверок, в магазин его не примут.

## 2. Сборка

```powershell
.\gradlew.bat :app:bundleRelease    # AAB для RuStore
.\gradlew.bat :app:assembleRelease  # APK для распространения с сайта
```

Артефакты:
- `app/build/outputs/bundle/release/app-release.aab` (~6.5 МБ)
- `app/build/outputs/apk/release/app-release.apk` (~4 МБ)

Перед выпуском поднять `versionCode` (+1 каждый выпуск) и `versionName`
в `app/build.gradle.kts`.

## 3. Публикация в RuStore (вручную)

1. Кабинет разработчика: https://console.rustore.ru (регистрация на ИП/самозанятого —
   реквизиты Шубина А.И. подходят).
2. Создать приложение: название «AstroSMap — натальная карта», категория «Образ жизни».
3. Загрузить AAB (или APK), заполнить:
   - описание (RU) — кратко: натальная карта офлайн, транзиты, синхронизация с astrosmap.ru;
   - иконка 512×512 (из `app/src/main/res/drawable/ic_launcher_foreground.xml` отрендерить,
     фон #0A0A1A);
   - скриншоты (эмулятор: форма, колесо, трактовки, прогнозы) — минимум 2;
   - возрастной рейтинг 18+ (как на сайте);
   - ссылка на политику конфиденциальности: https://astrosmap.ru/privacy.html
4. Модерация RuStore обычно 1–3 дня.

## 4. APK с сайта

Выложить `app-release.apk` на сервер (например, `/download/astrosmap.apk`)
и дать ссылку на сайте. Обновления пользователь ставит поверх (подпись совпадает).

## 5. Деплой бэкенда (нужно приложению!)

Приложение использует `/api/natal?svg=0` и `/api/transit?svg=0` — правка уже в
`backend/main.py` локально, на прод НЕ выложена. На SpaceWeb:

```bash
ssh <user>@77.222.42.168
cd <папка проекта> && git pull && systemctl --user restart astro  # или как настроен рестарт
```

Проверка: `curl -s -X POST "https://astrosmap.ru/api/natal?svg=0" -H "Content-Type: application/json" -d "{...}"`
— в ответе не должно быть ключа `svg`. Без этого приложение тоже работает,
просто тянет лишние ~200 КБ SVG на каждый запрос текстов.

## Известные ограничения v1

- Синастрия, лунар, прогрессии, календарь — пока только на сайте (схема
  подключения готова: эндпоинт + RemoteChart, см. SolarScreen как образец).
- Экспорт — PNG (PDF не делали).
- Тесты движка гонять только через `run-tests.ps1` (кириллица в пути ломает
  тестовый воркер Gradle).

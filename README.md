# school_21_mobile

Мобильное приложение для пиров Школы 21: Android-обёртка над [platform.21-school.ru](https://platform.21-school.ru/) в WebView.

Kotlin, Jetpack Compose, Gradle Kotlin DSL + version catalog. minSdk 26, compile/targetSdk 37.

## Требования

- Android Studio **Panda 4 (2025.3.4)** или новее. AGP 9.2 — последняя версия, которую поддерживает Panda 4.
  В более новой студии AGP можно поднять в `gradle/libs.versions.toml`.
- JDK 17+ (подойдёт встроенный в Android Studio JBR).
- Android SDK Platform 37 (Gradle скачает сам, если лицензии SDK приняты).

## Как открыть

1. Android Studio → **File → Open** → выберите корень репозитория.
2. Дождитесь Gradle Sync. `local.properties` с путём к SDK студия создаст сама.
3. Запустите конфигурацию **app** на устройстве или эмуляторе.

## Debug APK

```bash
./gradlew assembleDebug          # Windows: gradlew.bat assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

В debug-сборке включена отладка WebView: `chrome://inspect` в Chrome на компьютере.

## Release: keystore и подпись

### 1. Создать keystore (один раз)

```bash
keytool -genkeypair -v -keystore release.jks -alias school21 \
  -keyalg RSA -keysize 2048 -validity 10000
```

`keytool` есть в `<Android Studio>/jbr/bin`. Храните `release.jks` и пароли в надёжном месте:
без них нельзя выпустить обновление приложения с той же подписью.

### 2. Создать `keystore.properties` в корне проекта

```bash
cp keystore.properties.example keystore.properties
```

```properties
storeFile=release.jks        # путь относительно корня проекта (можно абсолютный)
storePassword=...
keyAlias=school21
keyPassword=...
```

`keystore.properties`, `*.jks` и `*.keystore` перечислены в `.gitignore`.
Без `keystore.properties` release соберётся **неподписанным**.

### 3. Собрать

```bash
./gradlew assembleRelease   # APK → app/build/outputs/apk/release/app-release.apk
./gradlew bundleRelease     # AAB для Google Play → app/build/outputs/bundle/release/app-release.aab
```

В release включены R8 (minify) и shrinkResources. Правила лежат в `app/proguard-rules.pro`,
маппинг для расшифровки стектрейсов: `app/build/outputs/mapping/release/mapping.txt`.

## Как поменять сайт / URL

Все настройки — в `app/src/main/java/ru/school21/app/AppConfig.kt`:

| Поле | Что это |
|---|---|
| `START_URL` | стартовая страница |
| `INTERNAL_DOMAINS` | домены (с поддоменами), открываемые внутри приложения; остальные — в Custom Tabs |
| `AUTH_HOSTS` | хосты страницы входа; после возврата с них история «Назад» очищается |
| `APP_NAME` | часть User-Agent: `<стандартный UA> School21Android/1.0` |
| `PULL_TO_REFRESH_ENABLED` | включить или выключить pull-to-refresh |

Также поправьте:
- `android:host` в deep-link `intent-filter` в `app/src/main/AndroidManifest.xml`;
- название приложения: `app_name` в `res/values/strings.xml`;
- `applicationId` / `namespace` в `app/build.gradle.kts`, если делаете другое приложение.

## Как поменять иконку и цвета

- **Иконка**: Android Studio → правый клик по `app` → **New → Image Asset** → Launcher Icons (Adaptive).
  Мастер перезапишет `res/drawable/ic_launcher_foreground.xml` и `res/mipmap-anydpi/ic_launcher*.xml`.
  Фон иконки — цвет `ic_launcher_background` в `res/values/colors.xml`.
  Эта же иконка показывается на splash screen (`res/values/themes.xml`).
- **Цвет системных панелей и splash**: `system_bar` в `res/values/colors.xml` (светлая тема)
  и `res/values-night/colors.xml` (тёмная).
- **Акцентный цвет** (прогресс-бар, кнопка «Повторить»): `brand`.

## Deep links

Ссылки `https://platform.21-school.ru/*` открываются в приложении, но **автоматически
(без диалога выбора) только если на сайте лежит**
`https://platform.21-school.ru/.well-known/assetlinks.json` с SHA-256 вашего сертификата.
Этот файл может положить только владелец домена. Без него на Android 12+ нужно включить вручную:
Настройки → Приложения → School 21 → Открывать по умолчанию → Добавить ссылку.

## Пуш-уведомления

Не реализованы. Место для них отмечено `TODO(push)` в `AndroidManifest.xml`.

## Структура

```
app/src/main/java/ru/school21/app/
├── AppConfig.kt            — все настройки
├── MainActivity.kt         — жизненный цикл, сохранение состояния, deep links, «Назад»
├── ui/
│   ├── WebScreen.kt        — WebView + pull-to-refresh + прогресс + insets
│   ├── OfflineScreen.kt    — экран «Нет подключения»
│   └── Theme.kt            — Material 3 тема, светлая/тёмная
└── web/
    ├── WebViewFactory.kt   — создание WebView и security-настройки
    ├── AppWebViewClient.kt — навигация, ошибки, история
    ├── AppWebChromeClient.kt — прогресс, input type=file
    ├── UrlRouter.kt        — внутри / Custom Tabs / tel:, mailto:, intent://
    ├── FileChooser.kt      — выбор файлов через ActivityResult API
    ├── DownloadHandler.kt  — скачивание через DownloadManager
    └── WebUiState.kt       — состояние для Compose
```

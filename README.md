# school_21_mobile

Мобильное приложение для пиров Школы 21: Android-обёртка над [platform.21-school.ru](https://platform.21-school.ru/) в WebView.

Kotlin, Jetpack Compose, Gradle Kotlin DSL + version catalog. minSdk 26, compile/targetSdk 37.

> Неофициальное приложение: показывает сайт платформы и данные её API. Логин и пароль (если включить
> автовход) хранятся только на устройстве в зашифрованном виде и отправляются только на `auth.21-school.ru`.

## Возможности

- **Платформа на весь экран** — тёмная тема в цвет сайта, pull-to-refresh, «Назад» по истории, экран «Нет подключения».
- **Автовход** — платформа завершает сессию пару раз в день; приложение само вводит логин и пароль.
- **Где сидит пир** — кластер, ряд и место по логину, уровень, XP, коины, PRP/CRP, коалиция, логтайм.
- **Виджеты на рабочий стол** — профиль, очки, «где я в кампусе», ближайшие события.
- **Уведомления** — «колокольчик» платформы, напоминания за 15 минут о событиях, на которые вы записаны,
  новые события кампуса. Проверка примерно раз в 30 минут (это не пуши: у платформы их нет).
- Загрузка файлов на сайт (в том числе с камеры), скачивание, полноэкранное видео.
- Ярлыки на иконке: Календарь, Проекты, Где сидит пир, Настройки. Проверка обновлений из GitHub Releases.

Всё дополнительное открывается кнопкой **⋯** в углу экрана (её можно перенести или скрыть в настройках).

### Как добавить виджет

1. Удерживайте палец на пустом месте рабочего стола → **«Виджеты»**.
2. Найдите **School 21** и перетащите нужный виджет.
3. Для данных в виджетах нужен включённый автовход (⋯ → Настройки → Автовход).

Если виджет не добавляется — на этом экране рабочего стола не хватает места (лаунчер Xiaomi в этом случае
молча отказывает): освободите место или выберите другой экран.

### Откуда данные

| Функция | Источник |
|---|---|
| Где сидит пир, виджеты, новые события кампуса | официальный [Open API](https://platform.21-school.ru/services/21-school/api/swagger) Школы 21 |
| «Колокольчик», личный календарь | внутренний GraphQL сайта (недокументирован; выключается флагом `PLATFORM_NOTIFICATIONS_ENABLED` в `AppConfig.kt`) |

Приложение только **читает** данные — никаких действий на платформе от вашего имени не выполняет.

## Нашли баг?

Создайте [issue на GitHub](https://github.com/Dlzxn/school_21_mobile/issues): что делали, что ожидали,
что получилось, модель телефона и версия Android, скриншот. В приложении: ⋯ → Настройки → «Сообщить об ошибке».

Автор: [Dlzxn](https://github.com/Dlzxn) · School 21: combinef

## Установка (для пиров)

Нужен Android 8.0 или новее.

1. Откройте [последний релиз](https://github.com/Dlzxn/school_21_mobile/releases/latest) с телефона
   и скачайте файл `School21-vX.Y.apk`.
2. Откройте скачанный файл. Если Android спросит, разрешите установку из этого источника
   (браузера или «Файлов»).
3. Если Google Play Protect предупредит о неизвестном приложении, нажмите «Всё равно установить»:
   приложение не из Play Маркета, поэтому оно ему незнакомо.

Обновление: скачайте APK новой версии и установите поверх старой, вход в аккаунт сохранится.

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
├── AppConfig.kt / AppSettings.kt — настройки сборки и пользователя
├── MainActivity.kt           — WebView, нативные экраны поверх сайта, ярлыки, deep links
├── auth/                     — автовход: зашифрованное хранилище, проверка пароля, заполнение формы
├── api/                      — Keycloak-токены, Open API, внутренний GraphQL сайта
├── sync/                     — фоновая синхронизация (WorkManager), уведомления, напоминания
├── widget/                   — виджеты на Glance
├── update/                   — проверка новой версии на GitHub
├── ui/                       — Compose: тема, настройки, «Где сидит пир», меню, экраны-оверлеи
└── web/                      — WebView: клиенты, роутинг ссылок, загрузка/скачивание файлов
```

`app/src/debug/` — только для debug-сборки: `DebugWidgetDemoActivity` подставляет в виджеты демо-данные
(для скриншотов-превью `res/drawable-nodpi/widget_preview_*.png`):
`adb shell am start -n ru.school21.app/.DebugWidgetDemoActivity`.

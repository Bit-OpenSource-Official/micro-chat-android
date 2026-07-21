# micromsg Android

Легкий native Android клиент для Rust-сервера `micromsg`.

Особенности:

- Java, без Compose, AndroidX, Retrofit, Room и других runtime-зависимостей.
- Страницы: Login, Chats, Chat, Settings.
- Текст, файлы, кошелек DSR и голосовые звонки.
- HTTPS long polling через `MiniTaLib`.
- Сохранение авторизации после перезапуска приложения.
- Фоновые уведомления через foreground service.
- История чата сначала открывается на последних сообщениях; старые догружаются при скролле вверх.
- Голосовые звонки 1-на-1 без видео: входящий звонок показывает рингтон и кнопки Accept/Decline, PCM-аудио идёт через защищённый WebSocket `wss://…/voice`.
- Все соединения с сервером используют стандартный TLS; cleartext HTTP/WebSocket и старый кастомный транспорт не поддерживаются.
- Release APK собирается с R8/minify и debug-подписью для быстрой установки.

## Подключение к backend

Backend должен быть опубликован через HTTPS с сертификатом, которому доверяет Android. Например, TLS можно завершить на Caddy или nginx, а внутренний HTTP-порт сервера оставить доступным только с localhost/приватной сети. В `micromsg` при этом включается `TRUST_HTTPS_PROXY=1`, а proxy передаёт `X-Forwarded-Proto: https` и WebSocket upgrade для `/voice`.

В готовом APK по умолчанию стоит адрес:

```bash
https://danila.e6atb.ru
```

Для локальной разработки нужен локальный доверенный сертификат, например:

```text
https://dev-chat.example.test:8443
```

## Сборка клиента

Открой `android-client/` в Android Studio или собери Gradle:

```bash
cd android-client
gradle :app:assembleRelease
```

Для локальной сборки нужны Android SDK и Gradle. Если их нет, используй Docker-сборку ниже.
Release-сборка требует keystore для подписи. По умолчанию Gradle
ищет `micromsg.keystore` в корне репозитория, а для локальной совместимости
также принимает старый путь `../micromsg.keystore`.

Версию приложения можно задать Gradle-параметрами:

```bash
gradle :app:assembleRelease -PappVersionName=1.2.3 -PappVersionCode=123
```

## GitHub Actions

При создании или обновлении ветки `release/VERSION` workflow собирает release APK.
`VERSION` из имени ветки передается в `versionName`, поэтому в приложении будет
показана та же версия. APK загружается в GitHub Actions artifacts и публикуется
в GitHub Releases с тегом `vVERSION`. Рядом с APK публикуется `update.json`;
Android-клиент читает GitHub Releases API из настроек приложения, сравнивает
`versionCode`, скачивает APK и открывает системный установщик.

В GitHub Actions репозиторий для OTA берется из `GITHUB_REPOSITORY`
автоматически. Для локальной сборки его можно задать вручную:

```bash
gradle :app:assembleRelease -PgithubRepository=OWNER/REPO
```

Для постоянной подписи APK добавь secrets:

- `ANDROID_KEYSTORE_B64` - base64 от `micromsg.keystore`.
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

Если `ANDROID_KEYSTORE_B64` не задан, workflow создаст временный keystore и
соберет APK, пригодный для проверки, но не для обновления уже установленного
приложения с постоянной подписью.

## Сборка в Docker

```bash
cd android-client
docker build -t micromsg-android .
docker create --name micromsg-apk micromsg-android
docker cp micromsg-apk:/src/app/build/outputs/apk/release/app-release.apk ./app-release.apk
docker rm micromsg-apk
```

APK появится здесь:

```text
android-client/app-release.apk
```

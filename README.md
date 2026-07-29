# micromsg Android

Легкий native Android клиент для Rust-сервера `micromsg`.

Особенности:

- Java, без Compose, AndroidX, Retrofit, Room и других runtime-зависимостей.
- Страницы: Login, Chats, Chat, Settings.
- Текст, файлы, кошелек DSR и голосовые звонки.
- HTTP long polling через `MiniTaLib`.
- Сохранение авторизации после перезапуска приложения.
- Фоновые уведомления через foreground service.
- История чата сначала открывается на последних сообщениях; старые догружаются при скролле вверх.
- Голосовые звонки 1-на-1 без видео: входящий звонок показывает рингтон и кнопки Accept/Decline, PCM-аудио идёт через WebSocket `/voice`.
- Cleartext HTTP включен для локальной разработки.
- Release APK собирается с R8/minify и постоянным release-сертификатом.

В готовом APK по умолчанию стоит адрес:

```text
ms.ove.rs:8080
```

Это native MST4 (`RCP4/RSP4`) поверх TCP с закреплённым публичным ключом
messenger. Адрес можно изменить в настройках приложения.

Ссылки на ботов имеют вид
`https://ms.ove.rs/bot/<bot_login>?start=<payload>`, fallback для браузеров —
`ovechat://bot/<bot_login>?start=<payload>`. После авторизации приложение
открывает диалог и один раз ставит `/start <payload>` в очередь отправки.

## Сборка клиента

Открой этот репозиторий в Android Studio или собери Gradle:

```bash
gradle :app:assembleRelease
```

Для локальной сборки нужны Android SDK и Gradle. Если их нет, используй Docker-сборку ниже.
Release-сборка требует ключ сервера и keystore для подписи. По умолчанию Gradle
ищет `micromsg.keystore` в корне этого репозитория.

```bash
cp .env.example .env
./build-apk.sh
```

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

Публичный transport pin production-сервера хранится в `app/build.gradle`, поэтому
GitHub Release воспроизводимо собирается без отдельного секрета. Для тестового
сервера ключ можно переопределить через `-PcryptServerPublicKeyB64=...`,
переменную окружения `CRYPT_SERVER_PUBLIC_KEY_B64` или локальный `.env`.
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
docker build -t micromsg-android .
docker create --name micromsg-apk micromsg-android
docker cp micromsg-apk:/src/app/build/outputs/apk/release/app-release.apk ./app-release.apk
docker rm micromsg-apk
```

APK появится здесь:

```text
app-release.apk
```

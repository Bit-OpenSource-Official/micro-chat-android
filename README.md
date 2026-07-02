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
- Release APK собирается с R8/minify и debug-подписью для быстрой установки.

## Запуск backend

Для хоста `10.100.2.21`:

```bash
ADDR=0.0.0.0:8080 cargo run --manifest-path micromsg/Cargo.toml --bin micromsg
```

В готовом APK по умолчанию стоит адрес:

```text
10.100.2.21:8080
```

Если нужен Android Emulator на той же машине, вручную поменяй поле server на:

```text
10.0.2.2:8080
```

## Сборка клиента

Открой `android-client/` в Android Studio или собери Gradle:

```bash
cd android-client
gradle :app:assembleRelease
```

Для локальной сборки нужны Android SDK и Gradle. Если их нет, используй Docker-сборку ниже.
Release-сборка требует ключ сервера и keystore для подписи. По умолчанию Gradle
ищет `micromsg.keystore` в корне репозитория, а для локальной совместимости
также принимает старый путь `../micromsg.keystore`.

```bash
CRYPT_SERVER_PUBLIC_KEY_B64=... gradle :app:assembleRelease
```

Версию приложения можно задать Gradle-параметрами:

```bash
gradle :app:assembleRelease -PappVersionName=1.2.3 -PappVersionCode=123
```

## GitHub Actions

При создании или обновлении ветки `release/VERSION` workflow собирает release APK.
`VERSION` из имени ветки передается в `versionName`, поэтому в приложении будет
показана та же версия. APK загружается в GitHub Actions artifacts и публикуется
в GitHub Releases с тегом `vVERSION`. Для сборки добавь в GitHub Secrets
значение `CRYPT_SERVER_PUBLIC_KEY_B64`.

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

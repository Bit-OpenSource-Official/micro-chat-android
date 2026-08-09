# OVE.rs for Android

Native Android client for [OVE.rs Messenger](https://ms.ove.rs). The default UI
language is English; Russian is selected automatically from the device locale or
can be chosen in Settings.

The production APK connects to:

```text
ms.ove.rs:8080
```

## Features

- Private chats with end-to-end encryption, key verification, and encrypted
  cloud key backup.
- Groups, channels, channel administration, comments, replies, forwarding,
  editing, emoji reactions, and paid DSR reactions.
- Optimistic outgoing-message queue with pending, sent, read, retry, and failed
  states.
- Images and files with swipe-confirmed DSR capacity purchases (`1 DSR = 1 MiB`),
  free zero-copy media forwarding, contacts, privacy controls, multiple sessions,
  and account recovery.
- DSR wallet, bot buttons, QR/code authorization for OVE.rs services, and GitHub
  in-app updates.
- One-to-one calls, voice channels, and background notifications.
- Native Java UI supporting Android 2.3.6 (API 10) and newer; compile/target SDK
  35.

## Transport and links

The client uses MicroMsg Secure Transport v5 (MST5, `RCP5`/`RSP5`) over TCP.
MST5 uses Noise `NK_25519_ChaChaPoly_SHA256`, pins the production server X25519
public key, carries canonical CBOR frames, multiplexes requests, and compresses
eligible payloads. The production pin is committed in `app/build.gradle` so
official builds are reproducible. A custom server can override it with
`-PcryptServerPublicKeyB64=...`, `CRYPT_SERVER_PUBLIC_KEY_B64`, or `.env`.

Bot links use:

```text
https://ms.ove.rs/bot/<bot_login>?start=<payload>
ovechat://bot/<bot_login>?start=<payload>
```

Service authorization supports `https://ms.ove.rs/oauth/device` and
`ovechat://authorize` links. The app opens the appropriate bot or authorization
screen after sign-in.

## Build

Requirements:

- JDK 17;
- Android SDK platform 35 and build-tools 35.0.0;
- Gradle 8.10.2 or a compatible Gradle 8 release.

Build and test locally:

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleRelease
```

The release build uses R8/resource shrinking and must be signed. By default it
loads the existing `micromsg.keystore`; that legacy filename and its signing
identity are intentionally retained so installed clients can update in place.
The helper builds the production APK without requiring an `.env` file:

```bash
./build-apk.sh
```

Override the application version when needed:

```bash
APP_VERSION_NAME=0.9.3 APP_VERSION_CODE=100041 ./build-apk.sh
```

The output is:

```text
app/build/outputs/apk/release/app-release.apk
```

Docker build:

```bash
docker build -t ove-rs-android .
docker create --name ove-rs-apk ove-rs-android
docker cp ove-rs-apk:/src/app/build/outputs/apk/release/app-release.apk ./ove-rs.apk
docker rm ove-rs-apk
```

## GitHub releases and OTA updates

Pushing a `release/VERSION` branch runs the `OVE.rs Android release` workflow.
It uses the branch suffix as `versionName`, assigns a monotonically increasing
`versionCode`, builds `ove-rs-VERSION.apk`, and publishes:

- a GitHub Release tagged `vVERSION` and titled `OVE.rs VERSION`;
- the signed APK;
- `update.json` containing the package, version, size, and SHA-256 checksum.

The client checks the repository's latest GitHub Release from Settings, verifies
the package ID, file size, and checksum, then opens Android's package installer.
Official releases are available at
[GitHub Releases](https://github.com/Bit-OpenSource-Official/micro-chat-android/releases/latest).
The official repository is embedded as the production default; fork builds can
override it with `-PgithubRepository=OWNER/REPO` or `GITHUB_REPOSITORY`.

Configure these repository secrets to preserve the production signing identity:

- `ANDROID_KEYSTORE_B64` — base64-encoded `micromsg.keystore`;
- `RELEASE_STORE_PASSWORD`;
- `RELEASE_KEY_ALIAS`;
- `RELEASE_KEY_PASSWORD`.

If `ANDROID_KEYSTORE_B64` is absent, CI creates a temporary test keystore. Such
an APK cannot update an installation signed with the production key.

## Compatibility identifiers

The application ID and Java package remain `ru.e6atb.chat`. They are legacy
compatibility identifiers and must not be renamed: changing them would install a
separate application and break existing sessions, deep links, and OTA upgrades.

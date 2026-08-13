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

The client uses MicroMsg Secure Transport v5.1 (MST5, `RCP5`/`RSP5`) over TCP.
MST5 uses Noise `NK_25519_ChaChaPoly_SHA256`, pins the production server X25519
public key, negotiates features in an encrypted HELLO, carries 40-byte CBOR
frames with command nonces/deadlines, multiplexes requests, cancels timed-out
work, rekeys automatically, and compresses eligible payloads. The production pin is committed in `app/build.gradle` so
official builds are reproducible. A custom server can override it with
`-PcryptServerPublicKeyB64=...`, `CRYPT_SERVER_PUBLIC_KEY_B64`, or `.env`.

On ARM Android 2.3+ the app requires the Rust `mst5-client` JNI bridge. Requests,
media streams and voice streams are implemented exclusively by the Rust crate;
there is no Java MST5/WebSocket fallback. Uploads and downloads cross JNI as file
descriptors rather than full Java byte arrays.

Native assets are downloaded and are not committed to this repository. Install
all four Android ABIs from the latest stable public `mst5-client` GitHub Release
with:

```bash
make native-libs
```

The command downloads the release's combined Android archive, verifies it
against the published `SHA256SUMS`, and installs the ready-made JNI libraries.
It never compiles `mst5-client` locally.

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
- Gradle 8.10.2 or a compatible Gradle 8 release;
- `curl`, `jq`, `sha256sum`, and `tar` for downloading the latest MST5 release.

Build and test locally:

```bash
make test
make apk
```

The release build uses R8/resource shrinking and must be signed. By default it
loads the existing `micromsg.keystore`; that legacy filename and its signing
identity are intentionally retained so installed clients can update in place.
`make apk` first downloads the latest released `mst5-client` libraries for all
ABIs and then builds the universal production APK without requiring an `.env`
file. Platform-specific packages can be built with:

```bash
make apk-armv6
make apk-armv7
make apk-arm64
```

Override the application version when needed:

```bash
APP_VERSION_NAME=0.9.8 APP_VERSION_CODE=100055 ./build-apk.sh
```

Prepare the universal APK, three architecture APKs, `update.json`, `SHA256SUMS`, and the release notes
table used by GitHub Actions:

```bash
make release-apks VERSION=0.9.9 VERSION_CODE=100056
```

The output is:

```text
app/build/outputs/apk/release/app-release.apk
```

Docker build:

```bash
make native-libs
docker build -t ove-rs-android .
docker create --name ove-rs-apk ove-rs-android
docker cp ove-rs-apk:/src/app/build/outputs/apk/release/app-release.apk ./ove-rs.apk
docker rm ove-rs-apk
```

## GitHub releases and OTA updates

Pushing a `release/VERSION` branch runs the `OVE.rs Android release` workflow.
It uses the branch suffix as `versionName`, assigns a monotonically increasing
`versionCode`, downloads (without rebuilding) the latest stable MST5 Android
release, runs the release Make targets, and publishes:

- a GitHub Release tagged `vVERSION` and titled `OVE.rs VERSION`;
- universal, ARMv6, ARMv7, and ARM64 signed APKs;
- a download table in the release description;
- `update.json` with independent names, sizes, and SHA-256 hashes for every
  architecture, the exact downloaded MST5 release version (plus the universal
  APK fields for legacy clients), and
  `SHA256SUMS` for every APK.

The OTA client maps the device ABI to `armv6`, `armv7`, or `arm64`, then
downloads only the matching APK. Automatic checks create an app notification;
the update dialog opens only after the user taps that notification. A manual
check in Settings opens the same dialog directly.

Publish the current commit of a clean local `main` as a remote release branch
with one command:

```bash
make release-branch 0.9.9
```

The command does not create or check out a local release branch. It pushes the
current local `main` commit directly to `origin/release/0.9.9`, which starts the
release workflow. If that branch already exists, it is updated only by a
fast-forward, which makes retrying a failed release safe. Local `main` may be
ahead of `origin/main`, but may not be behind or diverged from it.

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

The workflow fails when any signing secret is absent; it never creates a
temporary replacement key.

## Compatibility identifiers

The application ID and Java package remain `ru.e6atb.chat`. They are legacy
compatibility identifiers and must not be renamed: changing them would install a
separate application and break existing sessions, deep links, and OTA upgrades.

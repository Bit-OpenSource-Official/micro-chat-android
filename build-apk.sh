#!/usr/bin/env bash

set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

usage() {
	cat <<'EOF'
Usage: ./build-apk.sh [-h]

Loads .env and builds the signed Android release APK.

Optional environment:
  ENV_FILE=./.env
  CRYPT_SERVER_PUBLIC_KEY_B64=...  Override the production transport pin.
  GRADLE_USER_HOME=/tmp/ove-android-gradle
  APP_VERSION_NAME=0.9.8
  APP_VERSION_CODE=100055
  MST5_NATIVE_ABI=universal|armeabi|armeabi-v7a|arm64-v8a
  APK_OUTPUT=release/ove-rs.apk
  CLEAN_BUILD=1

Output:
  app/build/outputs/apk/release/app-release.apk
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then usage; exit 0; fi
if [[ $# -ne 0 ]]; then usage >&2; exit 2; fi
if [[ -z "${CRYPT_SERVER_PUBLIC_KEY_B64:-}" ]]; then
	ENV_FILE="${ENV_FILE:-$ROOT/.env}"
	if [[ -f "$ENV_FILE" ]]; then
		CRYPT_SERVER_PUBLIC_KEY_B64="$(sed -n 's/^CRYPT_SERVER_PUBLIC_KEY_B64=//p' "$ENV_FILE" | tail -n 1)"
	fi
fi
cd "$ROOT"
GRADLE_ARGS=(
	:app:assembleRelease
	-PappVersionName="${APP_VERSION_NAME:-0.9.8}"
	-PappVersionCode="${APP_VERSION_CODE:-100055}"
	-Pmst5NativeAbi="${MST5_NATIVE_ABI:-universal}"
)
if [[ "${CLEAN_BUILD:-1}" == "1" ]]; then
	GRADLE_ARGS=(clean "${GRADLE_ARGS[@]}")
fi
if [[ -n "${CRYPT_SERVER_PUBLIC_KEY_B64:-}" ]]; then
	GRADLE_ARGS+=("-PcryptServerPublicKeyB64=$CRYPT_SERVER_PUBLIC_KEY_B64")
fi
env -u JDK_JAVA_OPTIONS GRADLE_USER_HOME="${GRADLE_USER_HOME:-/tmp/ove-android-gradle}" \
	gradle "${GRADLE_ARGS[@]}"

if [[ -n "${APK_OUTPUT:-}" ]]; then
	mkdir -p "$(dirname -- "${APK_OUTPUT}")"
	cp app/build/outputs/apk/release/app-release.apk "${APK_OUTPUT}"
fi

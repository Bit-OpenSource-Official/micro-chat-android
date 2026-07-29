#!/usr/bin/env bash

set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

usage() {
	cat <<'EOF'
Usage: ./adb-install.sh [-h] [apk]

Installs a signed APK on every connected adb device without clearing app data.

Required environment:
  None.

Optional environment:
  APK_PATH=app/build/outputs/apk/release/app-release.apk

The positional apk argument overrides APK_PATH.
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then usage; exit 0; fi
if [[ $# -gt 1 ]]; then usage >&2; exit 2; fi
APK="${1:-${APK_PATH:-$ROOT/app/build/outputs/apk/release/app-release.apk}}"
if [[ ! -f "$APK" ]]; then
	echo "APK not found: $APK" >&2
	exit 1
fi

mapfile -t devices < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
if [[ ${#devices[@]} -eq 0 ]]; then
	echo "No connected adb devices." >&2
	exit 1
fi
for device in "${devices[@]}"; do
	echo "------- Installing on $device -------"
	adb -s "$device" install -r "$APK"
done

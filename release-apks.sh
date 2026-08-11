#!/usr/bin/env bash
set -euo pipefail

root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
version="${VERSION:-}"
version_code="${VERSION_CODE:-}"
release_tag="${RELEASE_TAG:-v${version}}"
repository="${GITHUB_REPOSITORY:-Bit-OpenSource-Official/micro-chat-android}"
release_dir="${RELEASE_DIR:-${root}/release}"

if [[ ! "${version}" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z.-]+)?$ ]]; then
	echo "VERSION must contain a release version such as 0.9.9" >&2
	exit 2
fi
if [[ ! "${version_code}" =~ ^[1-9][0-9]*$ ]]; then
	echo "VERSION_CODE must be a positive integer" >&2
	exit 2
fi

mkdir -p "${release_dir}"

build_apk() {
	local abi="$1"
	local suffix="$2"
	local clean="$3"
	APP_VERSION_NAME="${version}" \
	APP_VERSION_CODE="${version_code}" \
	MST5_NATIVE_ABI="${abi}" \
	APK_OUTPUT="${release_dir}/ove-rs-${version}-${suffix}.apk" \
	CLEAN_BUILD="${clean}" \
		"${root}/build-apk.sh"
}

build_apk universal universal 1
build_apk armeabi armv6 0
build_apk armeabi-v7a armv7 0
build_apk arm64-v8a arm64 0

universal_name="ove-rs-${version}-universal.apk"
universal_path="${release_dir}/${universal_name}"
universal_sha256="$(sha256sum "${universal_path}" | awk '{print $1}')"
universal_size="$(wc -c < "${universal_path}")"

cat > "${release_dir}/update.json" <<EOF
{
  "packageName": "ru.e6atb.chat",
  "versionName": "${version}",
  "versionCode": ${version_code},
  "apkName": "${universal_name}",
  "apkSize": ${universal_size},
  "apkSha256": "${universal_sha256}"
}
EOF

(
	cd "${release_dir}"
	sha256sum \
		"ove-rs-${version}-universal.apk" \
		"ove-rs-${version}-armv6.apk" \
		"ove-rs-${version}-armv7.apk" \
		"ove-rs-${version}-arm64.apk" > SHA256SUMS
)

asset_url="https://github.com/${repository}/releases/download/${release_tag}"
size_of() {
	numfmt --to=iec-i --suffix=B "$(wc -c < "$1")"
}

cat > "${release_dir}/release-notes.md" <<EOF
## Загрузки

| Сборка | Совместимость | Размер | Скачать |
|---|---|---:|---|
| Универсальная | ARMv6, ARMv7 и ARM64 | $(size_of "${release_dir}/ove-rs-${version}-universal.apk") | [ove-rs-${version}-universal.apk](${asset_url}/ove-rs-${version}-universal.apk) |
| ARMv6 | armeabi, Android 2.3+ | $(size_of "${release_dir}/ove-rs-${version}-armv6.apk") | [ove-rs-${version}-armv6.apk](${asset_url}/ove-rs-${version}-armv6.apk) |
| ARMv7 | armeabi-v7a | $(size_of "${release_dir}/ove-rs-${version}-armv7.apk") | [ove-rs-${version}-armv7.apk](${asset_url}/ove-rs-${version}-armv7.apk) |
| ARM64 | arm64-v8a | $(size_of "${release_dir}/ove-rs-${version}-arm64.apk") | [ove-rs-${version}-arm64.apk](${asset_url}/ove-rs-${version}-arm64.apk) |

Контрольные суммы: [SHA256SUMS](${asset_url}/SHA256SUMS).
Для автоматического обновления используется универсальная сборка.
EOF

echo "Release APKs and metadata written to ${release_dir}"

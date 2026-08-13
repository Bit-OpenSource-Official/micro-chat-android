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

build_apk universal all 1
build_apk armeabi armv6 0
build_apk armeabi-v7a armv7 0
build_apk arm64-v8a arm64 0
build_apk x86_64 x86_64 0

universal_name="ove-rs-${version}-all.apk"
armv6_name="ove-rs-${version}-armv6.apk"
armv7_name="ove-rs-${version}-armv7.apk"
arm64_name="ove-rs-${version}-arm64.apk"
x86_64_name="ove-rs-${version}-x86_64.apk"
universal_sha256="$(sha256sum "${release_dir}/${universal_name}" | awk '{print $1}')"
armv6_sha256="$(sha256sum "${release_dir}/${armv6_name}" | awk '{print $1}')"
armv7_sha256="$(sha256sum "${release_dir}/${armv7_name}" | awk '{print $1}')"
arm64_sha256="$(sha256sum "${release_dir}/${arm64_name}" | awk '{print $1}')"
x86_64_sha256="$(sha256sum "${release_dir}/${x86_64_name}" | awk '{print $1}')"
universal_size="$(wc -c < "${release_dir}/${universal_name}")"
armv6_size="$(wc -c < "${release_dir}/${armv6_name}")"
armv7_size="$(wc -c < "${release_dir}/${armv7_name}")"
arm64_size="$(wc -c < "${release_dir}/${arm64_name}")"
x86_64_size="$(wc -c < "${release_dir}/${x86_64_name}")"

cat > "${release_dir}/update.json" <<EOF
{
  "packageName": "ru.e6atb.chat",
  "versionName": "${version}",
  "versionCode": ${version_code},
  "apkName": "${universal_name}",
  "apkSize": ${universal_size},
  "apkSha256": "${universal_sha256}",
  "apks": {
    "armv6": {"apkName": "${armv6_name}", "apkSize": ${armv6_size}, "apkSha256": "${armv6_sha256}"},
    "armv7": {"apkName": "${armv7_name}", "apkSize": ${armv7_size}, "apkSha256": "${armv7_sha256}"},
    "arm64": {"apkName": "${arm64_name}", "apkSize": ${arm64_size}, "apkSha256": "${arm64_sha256}"},
    "x86_64": {"apkName": "${x86_64_name}", "apkSize": ${x86_64_size}, "apkSha256": "${x86_64_sha256}"}
  }
}
EOF

(
	cd "${release_dir}"
	sha256sum \
		"${universal_name}" \
		"${armv6_name}" \
		"${armv7_name}" \
		"${arm64_name}" > SHA256SUMS
	sha256sum "${x86_64_name}" >> SHA256SUMS
)

asset_url="https://github.com/${repository}/releases/download/${release_tag}"
size_of() {
	numfmt --to=iec-i --suffix=B "$(wc -c < "$1")"
}

cat > "${release_dir}/release-notes.md" <<EOF
## Загрузки

| Сборка | Совместимость | Размер | Скачать |
|---|---|---:|---|
| Универсальная | ARMv6, ARMv7, ARM64 и x86_64 | $(size_of "${release_dir}/${universal_name}") | [${universal_name}](${asset_url}/${universal_name}) |
| ARMv6 | armeabi, Android 2.3+ | $(size_of "${release_dir}/${armv6_name}") | [${armv6_name}](${asset_url}/${armv6_name}) |
| ARMv7 | armeabi-v7a | $(size_of "${release_dir}/${armv7_name}") | [${armv7_name}](${asset_url}/${armv7_name}) |
| ARM64 | arm64-v8a | $(size_of "${release_dir}/${arm64_name}") | [${arm64_name}](${asset_url}/${arm64_name}) |
| x86_64 | x86_64 | $(size_of "${release_dir}/${x86_64_name}") | [${x86_64_name}](${asset_url}/${x86_64_name}) |

Контрольные суммы: [SHA256SUMS](${asset_url}/SHA256SUMS).
Для автоматического обновления приложение выбирает APK своей архитектуры.
EOF

echo "Release APKs and metadata written to ${release_dir}"

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

mst5_manifest="${root}/app/src/main/assets/mst5-native/manifest.json"
if [[ ! -f "${mst5_manifest}" ]]; then
	echo "MST5 release manifest is missing; run make native-libs" >&2
	exit 2
fi
mst5_version="$(jq -er '.version | select(type == "string" and test("^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$"))' "${mst5_manifest}")"

mkdir -p "${release_dir}"

build_apk() {
	local abi="$1"
	local suffix="$2"
	local clean="$3"
	local armv6_tls="$4"
	APP_VERSION_NAME="${version}" \
	APP_VERSION_CODE="${version_code}" \
	MST5_NATIVE_ABI="${abi}" \
	INCLUDE_ARMV6_OTA_TLS="${armv6_tls}" \
	APK_OUTPUT="${release_dir}/ove-rs-${version}-${suffix}.apk" \
	CLEAN_BUILD="${clean}" \
		"${root}/build-apk.sh"
}

build_apk universal all 1 true
build_apk armeabi armv6 0 true
build_apk armeabi-v7a armv7 0 false
build_apk arm64-v8a arm64 0 false
build_apk x86_64 x86_64 0 false

APP_VERSION_NAME="${version}" \
APP_VERSION_CODE="${version_code}" \
MST5_NATIVE_ABI=universal \
INCLUDE_ARMV6_OTA_TLS=false \
	"${root}/build-xapk.sh" "${release_dir}/ove-rs-${version}.xapk"

universal_name="ove-rs-${version}-all.apk"
armv6_name="ove-rs-${version}-armv6.apk"
armv7_name="ove-rs-${version}-armv7.apk"
arm64_name="ove-rs-${version}-arm64.apk"
x86_64_name="ove-rs-${version}-x86_64.apk"
xapk_name="ove-rs-${version}.xapk"
universal_sha256="$(sha256sum "${release_dir}/${universal_name}" | awk '{print $1}')"
armv6_sha256="$(sha256sum "${release_dir}/${armv6_name}" | awk '{print $1}')"
armv7_sha256="$(sha256sum "${release_dir}/${armv7_name}" | awk '{print $1}')"
arm64_sha256="$(sha256sum "${release_dir}/${arm64_name}" | awk '{print $1}')"
x86_64_sha256="$(sha256sum "${release_dir}/${x86_64_name}" | awk '{print $1}')"
xapk_sha256="$(sha256sum "${release_dir}/${xapk_name}" | awk '{print $1}')"
universal_size="$(wc -c < "${release_dir}/${universal_name}")"
armv6_size="$(wc -c < "${release_dir}/${armv6_name}")"
armv7_size="$(wc -c < "${release_dir}/${armv7_name}")"
arm64_size="$(wc -c < "${release_dir}/${arm64_name}")"
x86_64_size="$(wc -c < "${release_dir}/${x86_64_name}")"
xapk_size="$(wc -c < "${release_dir}/${xapk_name}")"

cat > "${release_dir}/update.json" <<EOF
{
  "packageName": "ru.e6atb.chat",
  "versionName": "${version}",
  "versionCode": ${version_code},
  "mst5Version": "${mst5_version}",
  "xapk": {"name": "${xapk_name}", "size": ${xapk_size}, "sha256": "${xapk_sha256}", "minSdk": 21},
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
	sha256sum "${xapk_name}" >> SHA256SUMS
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
| XAPK | Split APK, Android 5.0+, APKPure XAPK Installer | $(size_of "${release_dir}/${xapk_name}") | [${xapk_name}](${asset_url}/${xapk_name}) |

Контрольные суммы: [SHA256SUMS](${asset_url}/SHA256SUMS).
Для автоматического обновления приложение выбирает APK своей архитектуры.
MST5: готовые Android-библиотеки из [релиза v${mst5_version}](https://github.com/Bit-OpenSource-Official/mst5-client/releases/tag/v${mst5_version}); MST5 при сборке APK не компилируется.
EOF

echo "Release APKs and metadata written to ${release_dir}"

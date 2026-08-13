#!/usr/bin/env bash
set -euo pipefail

root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
output="${1:?output XAPK path is required}"
[[ "${output}" = /* ]] || output="${root}/${output}"
bundletool_version="1.18.3"
bundletool_sha256="a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29"
bundletool="${root}/build/tools/bundletool-all-${bundletool_version}.jar"
version="${APP_VERSION_NAME:?APP_VERSION_NAME is required}"
version_code="${APP_VERSION_CODE:?APP_VERSION_CODE is required}"

for command in curl java jq sha256sum unzip zip; do
	command -v "${command}" >/dev/null 2>&1 || { echo "error: ${command} is required" >&2; exit 2; }
done

mkdir -p "$(dirname -- "${bundletool}")"
if [[ ! -f "${bundletool}" ]] || ! printf '%s  %s\n' "${bundletool_sha256}" "${bundletool}" | sha256sum -c - >/dev/null 2>&1; then
	curl --fail --silent --show-error --location --retry 3 \
		-o "${bundletool}.part" \
		"https://github.com/google/bundletool/releases/download/${bundletool_version}/bundletool-all-${bundletool_version}.jar"
	printf '%s  %s\n' "${bundletool_sha256}" "${bundletool}.part" | sha256sum -c - >/dev/null
	mv "${bundletool}.part" "${bundletool}"
fi

gradle_args=(
	:app:bundleRelease
	-PappVersionName="${version}"
	-PappVersionCode="${version_code}"
	-Pmst5NativeAbi=universal
	-PincludeArmv6OtaTls="${INCLUDE_ARMV6_OTA_TLS:-false}"
	-PpackageMst5AsJniLibs=true
	-PminSdkOverride=21
)
if [[ -n "${CRYPT_SERVER_PUBLIC_KEY_B64:-}" ]]; then
	gradle_args+=("-PcryptServerPublicKeyB64=${CRYPT_SERVER_PUBLIC_KEY_B64}")
fi
env -u JDK_JAVA_OPTIONS GRADLE_USER_HOME="${GRADLE_USER_HOME:-/tmp/ove-android-gradle}" \
	gradle "${gradle_args[@]}"

bundle="${root}/app/build/outputs/bundle/release/app-release.aab"
temporary="$(mktemp -d)"
trap 'rm -rf -- "${temporary}"' EXIT
apks="${temporary}/release.apks"
stage="${temporary}/xapk"

store_file="${RELEASE_STORE_FILE:-micromsg.keystore}"
[[ "${store_file}" = /* ]] || store_file="${root}/${store_file}"
java -jar "${bundletool}" build-apks \
	--bundle="${bundle}" --output="${apks}" --overwrite \
	--mode=default \
	--ks="${store_file}" \
	--ks-key-alias="${RELEASE_KEY_ALIAS:-micromsg}" \
	--ks-pass="pass:${RELEASE_STORE_PASSWORD:-password}" \
	--key-pass="pass:${RELEASE_KEY_PASSWORD:-${RELEASE_STORE_PASSWORD:-password}}"

mkdir -p "${stage}"
unzip -q "${apks}" -d "${temporary}/apks"
base="${temporary}/apks/splits/base-master.apk"
if [[ -z "${base}" || ! -f "${base}" ]]; then
	echo "error: bundletool did not produce a base master APK" >&2
	exit 1
fi
cp "${base}" "${stage}/base.apk"

split_json="${temporary}/splits.json"
printf '[]\n' > "${split_json}"
split_ids=(armeabi_v7a arm64_v8a x86_64 ldpi mdpi tvdpi hdpi xhdpi xxhdpi xxxhdpi)
for id in "${split_ids[@]}"; do
	split="${temporary}/apks/splits/base-${id}.apk"
	[[ -f "${split}" ]] || continue
	name="config.${id}.apk"
	cp "${split}" "${stage}/${name}"
	tmp_json="${temporary}/splits-next.json"
	jq --arg file "${name}" --arg id "config.${id}" \
		'. + [{"file":$file,"id":$id}]' "${split_json}" > "${tmp_json}"
	mv "${tmp_json}" "${split_json}"
done
for id in armeabi_v7a arm64_v8a x86_64; do
	name="config.${id}.apk"
	case "${id}" in
		armeabi_v7a) abi="armeabi-v7a" ;;
		arm64_v8a) abi="arm64-v8a" ;;
		x86_64) abi="x86_64" ;;
	esac
	if [[ ! -f "${stage}/${name}" ]] ||
		! unzip -Z1 "${stage}/${name}" | grep -qx "lib/${abi}/libmst5_android.so"; then
		echo "error: bundletool did not produce a valid MST5 ${id} split" >&2
		exit 1
	fi
done

icon="$(find "${root}/app/build/generated/res/pngs/release" -type f -name 'ic_launcher.png' | sort | tail -n 1)"
icon_name=""
if [[ -n "${icon}" && -f "${icon}" ]]; then
	cp "${icon}" "${stage}/icon.png"
	icon_name="icon.png"
fi

total_size=0
while IFS= read -r apk; do
	total_size=$((total_size + $(wc -c < "${apk}")))
done < <(find "${stage}" -maxdepth 1 -type f -name '*.apk' | sort)

jq -n \
	--arg package_name "ru.e6atb.chat" \
	--arg name "OVE.rs" \
	--arg version_name "${version}" \
	--arg version_code "${version_code}" \
	--arg icon "${icon_name}" \
	--argjson total_size "${total_size}" \
	--slurpfile splits "${split_json}" \
	'{xapk_version:2,package_name:$package_name,name:$name,version_code:$version_code,
	  version_name:$version_name,min_sdk_version:"21",target_sdk_version:"35",
	  permissions:[],split_configs:[$splits[0][].id],total_size:$total_size,
	  split_apks:([{file:"base.apk",id:"base"}] + $splits[0])}
	  + (if $icon == "" then {} else {icon:$icon} end)' \
	> "${stage}/manifest.json"

mkdir -p "$(dirname -- "${output}")"
(
	cd "${stage}"
	zip -q -9 -r "${output}.part" .
)
mv "${output}.part" "${output}"
unzip -tq "${output}" >/dev/null
echo "Split XAPK written to ${output}"

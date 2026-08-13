#!/usr/bin/env bash
set -euo pipefail

repository="Bit-OpenSource-Official/mst5-client"
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
output_dir="${root}/app/src/main/assets/mst5-native"
api_url="https://api.github.com/repos/${repository}/releases/latest"

for command in curl jq sha256sum tar; do
	command -v "${command}" >/dev/null 2>&1 || {
		echo "error: ${command} is required to download the MST5 release" >&2
		exit 2
	}
done

temporary_dir="$(mktemp -d)"
trap 'rm -rf -- "${temporary_dir}"' EXIT

curl_args=(
	--fail
	--silent
	--show-error
	--location
	--retry 3
	--header "Accept: application/vnd.github+json"
	--header "X-GitHub-Api-Version: 2022-11-28"
)
if [[ -n "${GITHUB_TOKEN:-}" ]]; then
	curl_args+=(--header "Authorization: Bearer ${GITHUB_TOKEN}")
fi

release_json="${temporary_dir}/release.json"
curl "${curl_args[@]}" --output "${release_json}" "${api_url}"

tag="$(jq -er '.tag_name | select(test("^v(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$"))' "${release_json}")"
version="${tag#v}"
archive_name="mst5-client-${version}-android.tar.gz"
archive_url="$(jq -er --arg name "${archive_name}" \
	'[.assets[] | select(.name == $name)] | if length == 1 then .[0].browser_download_url else error("expected exactly one Android archive") end' \
	"${release_json}")"
checksums_url="$(jq -er \
	'[.assets[] | select(.name == "SHA256SUMS")] | if length == 1 then .[0].browser_download_url else error("expected exactly one SHA256SUMS") end' \
	"${release_json}")"

archive="${temporary_dir}/${archive_name}"
checksums="${temporary_dir}/SHA256SUMS"
curl "${curl_args[@]}" --output "${archive}" "${archive_url}"
curl "${curl_args[@]}" --output "${checksums}" "${checksums_url}"

expected_sha256="$(awk -v name="${archive_name}" '$2 == name { print $1 }' "${checksums}")"
if [[ ! "${expected_sha256}" =~ ^[0-9a-fA-F]{64}$ ]]; then
	echo "error: ${archive_name} has no valid entry in MST5 SHA256SUMS" >&2
	exit 1
fi
actual_sha256="$(sha256sum "${archive}" | awk '{ print $1 }')"
if [[ "${actual_sha256,,}" != "${expected_sha256,,}" ]]; then
	echo "error: checksum mismatch for ${archive_name}" >&2
	exit 1
fi

while IFS= read -r entry; do
	case "${entry}" in
		android | android/ | android/*) ;;
		*)
			echo "error: unsafe path in ${archive_name}: ${entry}" >&2
			exit 1
			;;
	esac
	case "/${entry}/" in
		*/../*)
			echo "error: unsafe path in ${archive_name}: ${entry}" >&2
			exit 1
			;;
	esac
done < <(tar -tzf "${archive}")

tar -xzf "${archive}" -C "${temporary_dir}"
manifest="${temporary_dir}/android/manifest.json"
if [[ ! -f "${manifest}" || -L "${manifest}" ]]; then
	echo "error: MST5 ${tag} has no regular Android manifest" >&2
	exit 1
fi
jq -e --arg version "${version}" \
	'.abi == 1 and .version == $version and .target == "android"' \
	"${manifest}" >/dev/null

for abi in armeabi armeabi-v7a arm64-v8a x86_64; do
	library="${temporary_dir}/android/assets/mst5-native/${abi}/libmst5_android.so"
	if [[ ! -f "${library}" || -L "${library}" || ! -s "${library}" ]]; then
		echo "error: MST5 ${tag} does not contain Android JNI library for ${abi}" >&2
		exit 1
	fi
done

mkdir -p "${output_dir}"
for abi in armeabi armeabi-v7a arm64-v8a x86_64; do
	source="${temporary_dir}/android/assets/mst5-native/${abi}/libmst5_android.so"
	destination="${output_dir}/${abi}/libmst5_android.so"
	install -D -m 0755 "${source}" "${destination}"
done
install -m 0644 "${manifest}" "${output_dir}/manifest.json"

echo "Installed MST5 ${version} Android JNI libraries from the latest GitHub Release."

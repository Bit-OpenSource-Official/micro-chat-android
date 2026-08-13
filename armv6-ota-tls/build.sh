#!/usr/bin/env bash
set -euo pipefail

root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
ndk_root="${ANDROID_NDK_R14_HOME:-}"
output_dir="${1:-${root}/app/src/main/assets/armv6-ota-tls}"
toolchain="${ARMV6_ANDROID_TOOLCHAIN:-${root}/build/armv6-ota-toolchain}"
mbedtls_version="3.6.7"
mbedtls_archive="mbedtls-${mbedtls_version}.tar.bz2"
mbedtls_sha256="a7e8bcbec0e6f761b4af24f25677626b35f762f68eef79c08677a363212d11f6"
ca_name="cacert-2026-08-13.pem"
ca_sha256="f66dff1bdf8f96060b8177976f8b7d9254bc89bc4db933d769f7384d28480bc9"

if [[ -z "${ndk_root}" || ! -f "${ndk_root}/build/tools/make_standalone_toolchain.py" ]]; then
	echo "Set ANDROID_NDK_R14_HOME to an extracted Android NDK r14b directory." >&2
	exit 1
fi
for command in curl sha256sum tar make python3; do
	command -v "${command}" >/dev/null 2>&1 || { echo "error: ${command} is required" >&2; exit 2; }
done

work="$(mktemp -d)"
trap 'rm -rf -- "${work}"' EXIT
archive="${work}/${mbedtls_archive}"
ca_bundle="${work}/${ca_name}"

curl --fail --silent --show-error --location --retry 3 \
	-o "${archive}" \
	"https://github.com/Mbed-TLS/mbedtls/releases/download/mbedtls-${mbedtls_version}/${mbedtls_archive}"
printf '%s  %s\n' "${mbedtls_sha256}" "${archive}" | sha256sum -c - >/dev/null
curl --fail --silent --show-error --location --retry 3 \
	-o "${ca_bundle}" "https://curl.se/ca/${ca_name}"
printf '%s  %s\n' "${ca_sha256}" "${ca_bundle}" | sha256sum -c - >/dev/null

if [[ ! -x "${toolchain}/bin/arm-linux-androideabi-gcc" ]]; then
	python3 "${ndk_root}/build/tools/make_standalone_toolchain.py" \
		--arch arm --api 9 --install-dir "${toolchain}"
fi

tar -xjf "${archive}" -C "${work}"
source_dir="${work}/mbedtls-${mbedtls_version}"
compiler="${toolchain}/bin/arm-linux-androideabi-gcc"
archiver="${toolchain}/bin/arm-linux-androideabi-ar"
common_flags="-std=c99 -Os -fPIC -ffunction-sections -fdata-sections -D__ANDROID_API__=9 -march=armv6 -mfloat-abi=softfp"

build_log="${work}/mbedtls-build.log"
if ! make -C "${source_dir}/library" -j2 static \
	CC="${compiler}" AR="${archiver}" \
	CFLAGS="${common_flags}" WARNING_CFLAGS="-Wall -Wextra" \
	>"${build_log}" 2>&1; then
	tail -n 100 "${build_log}" >&2
	exit 1
fi

"${compiler}" ${common_flags} -shared \
	-I"${source_dir}/include" \
	-Wl,--gc-sections -Wl,-z,defs \
	"${root}/armv6-ota-tls/ota_tls.c" \
	"${source_dir}/library/libmbedtls.a" \
	"${source_dir}/library/libmbedx509.a" \
	"${source_dir}/library/libmbedcrypto.a" \
	-llog -ldl \
	-o "${work}/libove_ota_tls.so"

if [[ -n "${ARMV6_OTA_TLS_TEST_OUTPUT:-}" ]]; then
	"${compiler}" ${common_flags} -DOTA_TLS_TEST_MAIN \
		-I"${source_dir}/include" \
		-Wl,--gc-sections -Wl,-z,defs \
		"${root}/armv6-ota-tls/ota_tls.c" \
		"${source_dir}/library/libmbedtls.a" \
		"${source_dir}/library/libmbedx509.a" \
		"${source_dir}/library/libmbedcrypto.a" \
		-llog -ldl \
		-o "${ARMV6_OTA_TLS_TEST_OUTPUT}"
fi

"${toolchain}/bin/arm-linux-androideabi-strip" --strip-unneeded "${work}/libove_ota_tls.so"
if ! "${toolchain}/bin/arm-linux-androideabi-readelf" -A "${work}/libove_ota_tls.so" | grep -q 'Tag_CPU_arch: v6'; then
	echo "Refusing to publish an OTA TLS library that is not marked ARMv6." >&2
	exit 1
fi

mkdir -p "${output_dir}"
install -m 0755 "${work}/libove_ota_tls.so" "${output_dir}/libove_ota_tls.so"
install -m 0644 "${ca_bundle}" "${output_dir}/cacert.pem"
printf '{"abi":1,"mbedtls":"%s","ca":"Mozilla %s","target":"android-armv6"}\n' \
	"${mbedtls_version}" "${ca_name#cacert-}" | sed 's/\.pem"/"/' > "${output_dir}/manifest.json"
echo "ARMv6 OTA TLS ${mbedtls_version} written to ${output_dir}"

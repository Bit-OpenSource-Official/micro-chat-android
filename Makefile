.DEFAULT_GOAL := help

# Support both `make release-branch 0.9.9` and
# `make release-branch VERSION=0.9.9`.
ifeq ($(firstword $(MAKECMDGOALS)),release-branch)
POSITIONAL_RELEASE_ARGS := $(wordlist 2,$(words $(MAKECMDGOALS)),$(MAKECMDGOALS))
ifneq ($(word 2,$(POSITIONAL_RELEASE_ARGS)),)
$(error Usage: make release-branch X.Y.Z)
endif
ifneq ($(strip $(VERSION)),)
ifneq ($(strip $(POSITIONAL_RELEASE_ARGS)),)
$(error Pass the version either positionally or as VERSION=X.Y.Z, not both)
endif
endif
.PHONY: $(POSITIONAL_RELEASE_ARGS)
$(POSITIONAL_RELEASE_ARGS):
	@:
endif

RELEASE_VERSION := $(if $(VERSION),$(VERSION),$(word 1,$(POSITIONAL_RELEASE_ARGS)))

MST5_NATIVE_OUTPUT := $(CURDIR)/app/src/main/assets/mst5-native
MST5_NATIVE_VERSION ?= 0.7.0

.PHONY: help release-branch test release-check native-libs require-native-libs armv6-ota-tls require-armv6-ota-tls apk apk-universal apk-armv6 apk-armv7 apk-arm64 apk-x86_64 xapk release-apks

help:
	@echo "Usage:"
	@echo "  make release-branch 0.9.9"
	@echo "  make test"
	@echo "  make native-libs"
	@echo "  make armv6-ota-tls"
	@echo "  make apk"
	@echo "  make apk-armv6 | apk-armv7 | apk-arm64 | apk-x86_64"
	@echo "  make xapk VERSION=0.10.4 VERSION_CODE=100104"
	@echo "  make release-apks VERSION=0.10.4 VERSION_CODE=100104"

release-branch:
	@./release-branch.sh "$(RELEASE_VERSION)"

test: native-libs require-native-libs armv6-ota-tls require-armv6-ota-tls
	@gradle --no-daemon :app:testDebugUnitTest

release-check: test
	@gradle --no-daemon :app:lintVitalRelease

armv6-ota-tls:
	@./armv6-ota-tls/build.sh

require-armv6-ota-tls:
	@test -s "app/src/main/assets/armv6-ota-tls/libove_ota_tls.so"
	@test -s "app/src/main/assets/armv6-ota-tls/cacert.pem"
	@jq -e '.abi == 1 and .mbedtls == "3.6.7" and .target == "android-armv6"' "app/src/main/assets/armv6-ota-tls/manifest.json" >/dev/null

native-libs:
	@./fetch-mst5-native.sh

require-native-libs:
	@test -s "$(MST5_NATIVE_OUTPUT)/armeabi/libmst5_android.so"
	@test -s "$(MST5_NATIVE_OUTPUT)/armeabi-v7a/libmst5_android.so"
	@test -s "$(MST5_NATIVE_OUTPUT)/arm64-v8a/libmst5_android.so"
	@test -s "$(MST5_NATIVE_OUTPUT)/x86_64/libmst5_android.so"
	@jq -e --arg version "$(MST5_NATIVE_VERSION)" '.abi == 1 and .android_jni_abi == 6 and .version == $$version and .target == "android"' "$(MST5_NATIVE_OUTPUT)/manifest.json" >/dev/null

apk: apk-universal

apk-universal: native-libs require-native-libs armv6-ota-tls require-armv6-ota-tls
	@MST5_NATIVE_ABI=universal INCLUDE_ARMV6_OTA_TLS=true ./build-apk.sh

apk-armv6: native-libs require-native-libs armv6-ota-tls require-armv6-ota-tls
	@MST5_NATIVE_ABI=armeabi INCLUDE_ARMV6_OTA_TLS=true ./build-apk.sh

apk-armv7: native-libs require-native-libs
	@MST5_NATIVE_ABI=armeabi-v7a ./build-apk.sh

apk-arm64: native-libs require-native-libs
	@MST5_NATIVE_ABI=arm64-v8a ./build-apk.sh

apk-x86_64: native-libs require-native-libs
	@MST5_NATIVE_ABI=x86_64 ./build-apk.sh

xapk: native-libs require-native-libs
	@if [ -z "$(VERSION)" ] || [ -z "$(VERSION_CODE)" ]; then \
		echo "Usage: make xapk VERSION=0.10.4 VERSION_CODE=100104" >&2; \
		exit 2; \
	fi
	@APP_VERSION_NAME="$(VERSION)" APP_VERSION_CODE="$(VERSION_CODE)" \
		MST5_NATIVE_ABI=universal INCLUDE_ARMV6_OTA_TLS=false \
		./build-xapk.sh "release/ove-rs-$(VERSION).xapk"

release-apks: native-libs armv6-ota-tls
	@if [ -z "$(VERSION)" ] || [ -z "$(VERSION_CODE)" ]; then \
		echo "Usage: make release-apks VERSION=0.9.9 VERSION_CODE=100056" >&2; \
		exit 2; \
	fi
	@VERSION="$(VERSION)" VERSION_CODE="$(VERSION_CODE)" \
		RELEASE_TAG="$(if $(RELEASE_TAG),$(RELEASE_TAG),v$(VERSION))" \
		./release-apks.sh

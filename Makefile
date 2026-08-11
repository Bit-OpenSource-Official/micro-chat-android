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

MST5_CLIENT_DIR ?= ../mst5-client
MST5_NATIVE_OUTPUT := $(CURDIR)/app/src/main/assets/mst5-native

.PHONY: help release-branch test release-check native-libs require-native-libs apk apk-universal apk-armv6 apk-armv7 apk-arm64 release-apks

help:
	@echo "Usage:"
	@echo "  make release-branch 0.9.9"
	@echo "  make test"
	@echo "  make native-libs"
	@echo "  make apk"
	@echo "  make apk-armv6 | apk-armv7 | apk-arm64"
	@echo "  make release-apks VERSION=0.9.9 VERSION_CODE=100056"

release-branch:
	@./release-branch.sh "$(RELEASE_VERSION)"

test: native-libs require-native-libs
	@gradle --no-daemon :app:testDebugUnitTest

release-check: test
	@gradle --no-daemon :app:lintVitalRelease

native-libs:
	@test -x "$(MST5_CLIENT_DIR)/android-jni/build-android.sh" || { echo "mst5-client checkout not found at $(MST5_CLIENT_DIR)" >&2; exit 2; }
	@"$(MST5_CLIENT_DIR)/android-jni/build-android.sh" "$(MST5_NATIVE_OUTPUT)"
	@"$(MST5_CLIENT_DIR)/android-jni/build-armv6.sh" "$(MST5_NATIVE_OUTPUT)"

require-native-libs:
	@test -s "$(MST5_NATIVE_OUTPUT)/armeabi/libmst5_android.so"
	@test -s "$(MST5_NATIVE_OUTPUT)/armeabi-v7a/libmst5_android.so"
	@test -s "$(MST5_NATIVE_OUTPUT)/arm64-v8a/libmst5_android.so"

apk: apk-universal

apk-universal: native-libs require-native-libs
	@MST5_NATIVE_ABI=universal ./build-apk.sh

apk-armv6: native-libs require-native-libs
	@MST5_NATIVE_ABI=armeabi ./build-apk.sh

apk-armv7: native-libs require-native-libs
	@MST5_NATIVE_ABI=armeabi-v7a ./build-apk.sh

apk-arm64: native-libs require-native-libs
	@MST5_NATIVE_ABI=arm64-v8a ./build-apk.sh

release-apks: native-libs
	@if [ -z "$(VERSION)" ] || [ -z "$(VERSION_CODE)" ]; then \
		echo "Usage: make release-apks VERSION=0.9.9 VERSION_CODE=100056" >&2; \
		exit 2; \
	fi
	@VERSION="$(VERSION)" VERSION_CODE="$(VERSION_CODE)" \
		RELEASE_TAG="$(if $(RELEASE_TAG),$(RELEASE_TAG),v$(VERSION))" \
		./release-apks.sh

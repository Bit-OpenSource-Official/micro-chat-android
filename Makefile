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

.PHONY: help release-branch test release-check apk apk-universal apk-armv6 apk-armv7 apk-arm64 release-apks

help:
	@echo "Usage:"
	@echo "  make release-branch 0.9.9"
	@echo "  make test"
	@echo "  make apk"
	@echo "  make apk-armv6 | apk-armv7 | apk-arm64"
	@echo "  make release-apks VERSION=0.9.9 VERSION_CODE=100056"

release-branch:
	@./release-branch.sh "$(RELEASE_VERSION)"

test:
	@gradle --no-daemon :app:testDebugUnitTest

release-check: test
	@gradle --no-daemon :app:lintVitalRelease

apk: apk-universal

apk-universal:
	@MST5_NATIVE_ABI=universal ./build-apk.sh

apk-armv6:
	@MST5_NATIVE_ABI=armeabi ./build-apk.sh

apk-armv7:
	@MST5_NATIVE_ABI=armeabi-v7a ./build-apk.sh

apk-arm64:
	@MST5_NATIVE_ABI=arm64-v8a ./build-apk.sh

release-apks:
	@if [ -z "$(VERSION)" ] || [ -z "$(VERSION_CODE)" ]; then \
		echo "Usage: make release-apks VERSION=0.9.9 VERSION_CODE=100056" >&2; \
		exit 2; \
	fi
	@VERSION="$(VERSION)" VERSION_CODE="$(VERSION_CODE)" \
		RELEASE_TAG="$(if $(RELEASE_TAG),$(RELEASE_TAG),v$(VERSION))" \
		./release-apks.sh

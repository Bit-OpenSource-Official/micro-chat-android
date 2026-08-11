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

.PHONY: help release-branch

help:
	@echo "Usage:"
	@echo "  make release-branch 0.9.9"

release-branch:
	@./release-branch.sh "$(RELEASE_VERSION)"

#!/usr/bin/env bash
set -euo pipefail

usage() {
	cat >&2 <<'EOF'
Usage: ./release-branch.sh VERSION

Pushes the current, up-to-date main commit to origin as release/VERSION.
No local release branch is created. The push starts the GitHub release workflow.
EOF
}

if [[ $# -ne 1 ]]; then
	usage
	exit 2
fi

version="$1"

if [[ ! "$version" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
	echo "error: VERSION must use X.Y.Z format, for example 0.9.9" >&2
	exit 2
fi

repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
cd "$repo_dir"

if [[ "$(git rev-parse --show-toplevel)" != "$repo_dir" ]]; then
	echo "error: $repo_dir is not the Android client Git repository root" >&2
	exit 1
fi

branch="release/$version"
git check-ref-format --branch "$branch" >/dev/null

if [[ -n "$(git status --porcelain)" ]]; then
	echo "error: working tree is not clean; commit or stash the changes first" >&2
	exit 1
fi

current_branch="$(git branch --show-current)"
if [[ "$current_branch" != "main" ]]; then
	echo "error: switch to main before creating a release (current: ${current_branch:-detached HEAD})" >&2
	exit 1
fi

git remote get-url origin >/dev/null
echo "Fetching origin/main..."
git fetch --quiet origin "+refs/heads/main:refs/remotes/origin/main"

if ! git merge-base --is-ancestor refs/remotes/origin/main refs/heads/main; then
	echo "error: main is behind or has diverged from origin/main; update it before releasing" >&2
	exit 1
fi

if git ls-remote --exit-code --heads origin "refs/heads/$branch" >/dev/null 2>&1; then
	echo "error: remote branch origin/$branch already exists" >&2
	exit 1
fi

echo "Publishing main as origin/$branch; this starts the GitHub release workflow..."
git push origin "HEAD:refs/heads/$branch"

echo "Remote release branch origin/$branch created. Local branch remains main."

#!/usr/bin/env bash
# The repo's checks. Exit 0 only when the branch is fit to review.
set -euo pipefail

repo_root="$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)"
export MISE_TRUSTED_CONFIG_PATHS="$repo_root${MISE_TRUSTED_CONFIG_PATHS:+:$MISE_TRUSTED_CONFIG_PATHS}"

ktlint '**/*.kt' '**/*.kts' '!**/build/**' --reporter=plain
./gradlew build --no-daemon

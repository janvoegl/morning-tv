#!/usr/bin/env bash
# Installs scripts/check-secrets.sh as the git pre-commit hook.
set -euo pipefail
ROOT="$(git rev-parse --show-toplevel)"
ln -sf ../../scripts/check-secrets.sh "$ROOT/.git/hooks/pre-commit"
echo "pre-commit hook installed"
